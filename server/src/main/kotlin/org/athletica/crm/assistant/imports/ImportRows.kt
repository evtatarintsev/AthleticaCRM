package org.athletica.crm.assistant.imports

import arrow.core.raise.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.assistant.AssistantClientPayload
import org.athletica.crm.api.schemas.assistant.AssistantImportRow
import org.athletica.crm.api.schemas.assistant.AssistantImportRowError
import org.athletica.crm.api.schemas.assistant.AssistantImportRowStatus
import org.athletica.crm.api.schemas.assistant.AssistantImportWarning
import org.athletica.crm.api.schemas.clients.ClientContactInput
import org.athletica.crm.assistant.assistantError
import org.athletica.crm.assistant.tools.isValidContact
import org.athletica.crm.assistant.tools.validateContacts
import org.athletica.crm.core.Gender
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.customfields.CustomFieldValues
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.usecases.clients.import.ValueParsers

/** Назначение колонки или исправления. Части ФИО собираются в «Фамилия Имя Отчество». */
@Serializable
enum class ImportTarget {
    @SerialName("name")
    NAME,

    @SerialName("last_name")
    LAST_NAME,

    @SerialName("first_name")
    FIRST_NAME,

    @SerialName("middle_name")
    MIDDLE_NAME,

    @SerialName("birthday")
    BIRTHDAY,

    @SerialName("gender")
    GENDER,

    @SerialName("contact")
    CONTACT,

    @SerialName("custom_field")
    CUSTOM_FIELD,

    @SerialName("skip")
    SKIP,
}

/** Сопоставление колонки [index] с полем клиента. */
@Serializable
data class ImportColumn(
    val index: Int,
    val target: ImportTarget,
    /** Тип контакта для [ImportTarget.CONTACT]. */
    val contactType: ContactType? = null,
    /** Подпись контакта словами пользователя, например «мама». */
    val contactLabel: String? = null,
    /** Ключ доп. поля для [ImportTarget.CUSTOM_FIELD]. */
    val fieldKey: String? = null,
)

/** Исправление пользователя: значение поля [target] строки [row]. */
@Serializable
data class ImportRowFix(
    val row: Int,
    val target: ImportTarget,
    val value: String,
    val contactType: ContactType? = null,
    val fieldKey: String? = null,
    /**
     * Колонка контакта, значение которой заменяет исправление; без неё — первая колонка типа
     * [contactType]. Если такой колонки нет, контакт добавляется. Подпись колонки сохраняется.
     */
    val index: Int? = null,
)

/** Решение о строках, совпавших с клиентом или строкой файла только по ФИО. */
@Serializable
enum class PossibleDuplicates {
    @SerialName("skip")
    SKIP,

    @SerialName("include")
    INCLUDE,

    /** Создать только строки из `includeDuplicateRows`. */
    @SerialName("rows")
    ROWS,
}

/** Аргументы `prepare_import` до проверки; значения строк модель не передаёт. */
@Serializable
data class PrepareImportArgs(
    val sourceId: String,
    val sheet: String? = null,
    /** Номер строки заголовка (`row` из образца); `0` — заголовка нет. */
    val headerRow: Int,
    val columns: List<ImportColumn>,
    /** Формат даты Java, например `dd.MM.yyyy`; иначе — распространённые форматы. */
    val dateFormat: String? = null,
    val genderValues: Map<String, Gender> = emptyMap(),
    val genderByFirstName: Map<String, Gender> = emptyMap(),
    val defaultGender: Gender? = null,
    val rowFixes: List<ImportRowFix> = emptyList(),
    val excludeRows: List<Int> = emptyList(),
    val possibleDuplicates: PossibleDuplicates = PossibleDuplicates.SKIP,
    val includeDuplicateRows: List<Int> = emptyList(),
)

/** Строка после разбора: [view] в статусе `ready` или `error`, ключи для поиска дублей. */
class ImportCandidate(val view: AssistantImportRow, val phones: List<String>, val nameKey: String?)

/** Разобранные строки листа и имена, пол которых не определён ни колонкой, ни картой имён. */
class ParsedImport(val candidates: List<ImportCandidate>, val namesWithoutGender: List<String>, val uncertainCells: Int)

/** Найденные в организации клиенты по нормализованному телефону и ФИО. */
class ExistingClients(val byPhone: Map<String, ClientId>, val byName: Map<String, ClientId>)

/** Применение сопоставления ко всем строкам источника; ошибка строки не блокирует остальные. */
object ImportRows {
    /** Сколько различающихся имён без пола получает модель. */
    const val NAMES_WITHOUT_GENDER = 150

    /** Разбирает строки выбранного листа [source] по [args] и доп. полям [fields]. */
    context(raise: Raise<AppError>)
    fun parse(source: ImportSource, args: PrepareImportArgs, fields: List<CustomFieldDefinition>): ParsedImport {
        val sheet =
            when {
                args.sheet != null -> source.sheets.firstOrNull { it.name == args.sheet } ?: raise(assistantError("IMPORT_SHEET_UNKNOWN"))
                source.sheets.size == 1 -> source.sheets.single()
                else -> raise(assistantError("IMPORT_SHEET_REQUIRED"))
            }
        val keys = fields.map { it.fieldKey.value }.toSet()
        if (args.columns.any { (it.target == ImportTarget.CONTACT && it.contactType == null) || (it.target == ImportTarget.CUSTOM_FIELD && it.fieldKey !in keys) } ||
            args.rowFixes.any { (it.target == ImportTarget.CONTACT && it.contactType == null) || (it.target == ImportTarget.CUSTOM_FIELD && it.fieldKey !in keys) }
        ) {
            raise(assistantError("VALIDATION"))
        }
        val header = sheet.rows.firstOrNull { it.n == args.headerRow }?.cells?.map { it.value }
        val rows = sheet.rows.filter { it.n > args.headerRow && it.n !in args.excludeRows && (header == null || !repeatsHeader(it, header)) }
        if (rows.size > ImportFiles.MAX_ROWS) {
            raise(assistantError("IMPORT_TOO_MANY_ROWS"))
        }
        val genderValues = args.genderValues.mapKeys { key(it.key) }
        val byFirstName = args.genderByFirstName.mapKeys { key(it.key) }
        val withoutGender = linkedSetOf<String>()
        var uncertain = 0
        val candidates =
            rows.map { row ->
                uncertain += row.cells.count { it.uncertain }
                RowParser(row, sheet.name.takeIf { row.photo == null }, args, fields, genderValues, byFirstName).candidate().also { parsed ->
                    parsed.second?.let(withoutGender::add)
                }.first
            }
        return ParsedImport(candidates, withoutGender.take(NAMES_WITHOUT_GENDER), uncertain)
    }

    /**
     * Статусы дублей по порядку строк: телефон клиента организации — `exists`, телефон более ранней
     * строки — `duplicate_in_file`, совпадение только по ФИО — `possible_duplicate`, если решение
     * [decision] не включает строку в создание.
     */
    fun classify(
        candidates: List<ImportCandidate>,
        existing: ExistingClients,
        decision: PossibleDuplicates,
        included: Set<Int>,
    ): List<AssistantImportRow> {
        val phones = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        return candidates.map { candidate ->
            val view = candidate.view
            if (view.status == AssistantImportRowStatus.ERROR) {
                return@map view
            }
            val known = candidate.phones.firstNotNullOfOrNull { existing.byPhone[it] }
            val nameClient = candidate.nameKey?.let { existing.byName[it] }
            val result =
                when {
                    known != null -> view.copy(status = AssistantImportRowStatus.EXISTS, existingClientId = known)
                    candidate.phones.any { it in phones } -> view.copy(status = AssistantImportRowStatus.DUPLICATE_IN_FILE)
                    nameClient != null || candidate.nameKey in names ->
                        if (decision == PossibleDuplicates.INCLUDE || (decision == PossibleDuplicates.ROWS && view.row in included)) {
                            view.copy(warnings = view.warnings + AssistantImportWarning.POSSIBLE_DUPLICATE, existingClientId = nameClient)
                        } else {
                            view.copy(status = AssistantImportRowStatus.POSSIBLE_DUPLICATE, existingClientId = nameClient)
                        }
                    else -> view
                }
            phones += candidate.phones
            candidate.nameKey?.let(names::add)
            result
        }
    }

    /** Строка сохранённого черновика снова становится кандидатом для повторной проверки дублей. */
    fun candidate(row: AssistantImportRow): ImportCandidate {
        val client = row.client
        val base =
            row.copy(
                status = if (client == null) AssistantImportRowStatus.ERROR else AssistantImportRowStatus.READY,
                warnings = row.warnings - AssistantImportWarning.POSSIBLE_DUPLICATE,
                existingClientId = null,
            )
        return ImportCandidate(base, client?.contacts.orEmpty().phones(), client?.name?.let(::key))
    }

    /** Нормализованное ФИО или имя: регистр, `ё → е`, пробелы; совпадает с SQL поиска дублей. */
    fun key(name: String): String = name.trim().replace('ё', 'е').replace('Ё', 'Е').replace(Regex("\\s+"), " ").lowercase()

    /**
     * Повтор заголовка [header], например на следующем фото журнала: совпадает больше половины его
     * непустых ячеек без учёта регистра, пробелов и знаков, поэтому мелкие расхождения распознавания не мешают.
     */
    private fun repeatsHeader(row: SourceRow, header: List<String?>): Boolean {
        val keys = header.map(::cellKey)
        val total = keys.count { it.isNotEmpty() }
        val same = keys.indices.count { keys[it].isNotEmpty() && cellKey(row.cells.getOrNull(it)?.value) == keys[it] }
        return total > 0 && same * 2 > total
    }

    /** Текст ячейки для сравнения с заголовком: только буквы и цифры нормализованного [value]. */
    private fun cellKey(value: String?): String = key(value.orEmpty()).filter(Char::isLetterOrDigit)

    /** Нормализованные телефоны контактов. */
    fun List<ClientContactInput>.phones(): List<String> = filter { it.type == ContactType.PHONE }.mapNotNull { PhoneDigits.from(it.value)?.value }
}

/** Разбор одной строки: ошибка становится статусом строки, а не ошибкой вызова. */
private class RowParser(
    private val row: SourceRow,
    private val sheet: String?,
    private val args: PrepareImportArgs,
    private val fields: List<CustomFieldDefinition>,
    private val genderValues: Map<String, Gender>,
    private val byFirstName: Map<String, Gender>,
) {
    private val fixes = args.rowFixes.filter { it.row == row.n }
    private val uncertain = linkedSetOf<String>()
    private val warnings = linkedSetOf<AssistantImportWarning>()

    /** Кандидат и имя без пола (если пол не дала ни колонка, ни карта имён). */
    fun candidate(): Pair<ImportCandidate, String?> {
        val words = name()
        val name = words.joinToString(" ")
        val base = AssistantImportRow(row.n, sheet, row.photo, row.line, AssistantImportRowStatus.READY, name.ifBlank { null })
        val firstName = fix(ImportTarget.FIRST_NAME) ?: cells(ImportTarget.FIRST_NAME).firstOrNull()?.value?.trim() ?: words.getOrNull(1) ?: words.firstOrNull()
        val gender = gender(firstName)
        val withoutGender = firstName?.takeIf { gender.second }
        val parsed =
            either {
                val clientName = either { NonBlankName.from(name) }.fold({ raise(AssistantImportRowError.NAME_REQUIRED to null) }, { it })
                val birthday = date(fix(ImportTarget.BIRTHDAY) ?: value(ImportTarget.BIRTHDAY, "birthday"))
                val contacts = contacts()
                val custom = customFields()
                AssistantClientPayload(clientName.value, gender.first ?: raise(AssistantImportRowError.GENDER_REQUIRED to "gender"), birthday, contacts, custom)
            }
        val view =
            parsed.fold(
                { (error, field) -> base.copy(status = AssistantImportRowStatus.ERROR, error = error, errorField = field) },
                { client -> base.copy(name = client.name, client = client) },
            ).let {
                if (uncertain.isNotEmpty()) {
                    warnings += AssistantImportWarning.UNCERTAIN
                }
                it.copy(warnings = warnings.toList(), uncertainFields = uncertain.toList())
            }
        val client = view.client
        return ImportCandidate(view, with(ImportRows) { client?.contacts.orEmpty().phones() }, client?.name?.let(ImportRows::key)) to withoutGender
    }

    /** Слова ФИО: колонка целиком или части; исправление части заменяет слово на её месте. */
    private fun name(): MutableList<String> {
        val full = cells(ImportTarget.NAME)
        val parts = listOf(ImportTarget.LAST_NAME, ImportTarget.FIRST_NAME, ImportTarget.MIDDLE_NAME).map(::cells)
        if ((full + parts.flatten()).any { it.uncertain }) {
            uncertain += "name"
        }
        val words =
            (if (full.any { !it.value.isNullOrBlank() }) full.mapNotNull { it.value } else parts.mapNotNull { it.firstOrNull()?.value })
                .flatMap { it.trim().split(Regex("\\s+")) }.filter(String::isNotBlank).toMutableList()
        fixes.forEach { fix ->
            val position = listOf(ImportTarget.LAST_NAME, ImportTarget.FIRST_NAME, ImportTarget.MIDDLE_NAME).indexOf(fix.target)
            when {
                fix.target == ImportTarget.NAME -> {
                    words.clear()
                    words += fix.value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
                    uncertain -= "name"
                }
                position >= 0 -> {
                    while (words.size <= position) {
                        words += ""
                    }
                    words[position] = fix.value.trim()
                    uncertain -= "name"
                }
            }
        }
        return words.filter(String::isNotBlank).toMutableList()
    }

    /** Пол: колонка по `genderValues` → карта имён → пол по умолчанию; второе — пол не дала ни колонка, ни карта. */
    private fun gender(firstName: String?): Pair<Gender?, Boolean> {
        val raw = fix(ImportTarget.GENDER) ?: value(ImportTarget.GENDER, "gender")
        val fromColumn = raw?.let { Gender.entries.firstOrNull { g -> g.name == it.uppercase() } ?: genderValues[ImportRows.key(it)] }
        val fromName = firstName?.let { byFirstName[ImportRows.key(it)] }
        return (fromColumn ?: fromName ?: args.defaultGender) to (fromColumn == null && fromName == null)
    }

    /** Значение колонки [target]; неуверенная ячейка отмечает поле [field]. */
    private fun value(target: ImportTarget, field: String): String? {
        val cell = cells(target).firstOrNull { !it.value.isNullOrBlank() } ?: return null
        if (cell.uncertain) {
            uncertain += field
        }
        return cell.value?.trim()
    }

    /** Ячейки строки в колонках [target], отобранных [select]; колонка за краем строки — пустая ячейка. */
    private fun cells(target: ImportTarget, select: (ImportColumn) -> Boolean = { true }): List<SourceCell> = args.columns.filter { it.target == target && select(it) }.map { row.cells.getOrNull(it.index) ?: SourceCell() }

    /** Последнее исправление поля [target] этой строки. */
    private fun fix(target: ImportTarget): String? = fixes.lastOrNull { it.target == target }?.value?.trim()

    /** Дата в формате пользователя или распространённых форматах; ISO — формат дат-ячеек xlsx. */
    context(raise: Raise<Pair<AssistantImportRowError, String?>>)
    private fun date(raw: String?, field: String = "birthday") =
        ValueParsers.parseDate(raw, args.dateFormat).fold({ ValueParsers.parseDate(raw, "yyyy-MM-dd").getOrNull() }, { it })
            .let { if (it == null && !raw.isNullOrBlank()) raise(AssistantImportRowError.INVALID_DATE to field) else it }

    /**
     * Контакты колонок с разбиением по `,;`; неуверенный контакт выбрасывается с предупреждением.
     * Исправление заменяет значение одной колонки ([ImportRowFix.index]) и сохраняет её подпись.
     */
    context(raise: Raise<Pair<AssistantImportRowError, String?>>)
    private fun contacts(): List<ClientContactInput> {
        val columns = args.columns.filter { it.target == ImportTarget.CONTACT }
        val fixed = fixes.filter { it.target == ImportTarget.CONTACT }
        val byColumn = fixed.mapNotNull { fix -> column(fix, columns)?.let { it to fix } }.toMap()
        val fromColumns =
            columns.flatMap { column ->
                val label = column.contactLabel?.trim()?.ifBlank { null }
                val fix = byColumn[column]
                val cell = row.cells.getOrNull(column.index) ?: SourceCell()
                val values = split(fix?.value ?: cell.value)
                when {
                    fix != null -> values.map { ClientContactInput(checkNotNull(fix.contactType), it, label) }
                    cell.uncertain && values.isNotEmpty() -> {
                        warnings += AssistantImportWarning.CONTACT_DROPPED
                        emptyList()
                    }
                    else -> values.map { ClientContactInput(checkNotNull(column.contactType), it, label) }
                }
            }
        val added = fixed.filter { column(it, columns) == null }.flatMap { fix -> split(fix.value).map { ClientContactInput(checkNotNull(fix.contactType), it) } }
        val contacts = fromColumns + added
        contacts.firstOrNull { !isValidContact(it.type, it.value) }?.let { raise(AssistantImportRowError.INVALID_CONTACT to it.type.name) }
        return either { validateContacts(contacts, ClientId.new()) }.fold({ raise(AssistantImportRowError.INVALID_CONTACT to null) }, { it })
    }

    /** Колонка контакта, которую заменяет исправление [fix]: по номеру или первая колонка того же типа. */
    private fun column(fix: ImportRowFix, columns: List<ImportColumn>): ImportColumn? = columns.firstOrNull { if (fix.index != null) it.index == fix.index else it.contactType == fix.contactType }

    /** Значения контактов ячейки [value], разделённые `,` или `;`. */
    private fun split(value: String?): List<String> = value.orEmpty().split(',', ';').map(String::trim).filter(String::isNotEmpty)

    /** Значения доп. полей по типам определений и проверка обязательных. */
    context(raise: Raise<Pair<AssistantImportRowError, String?>>)
    private fun customFields(): List<CustomFieldValue> {
        val values =
            fields.mapNotNull { field ->
                val key = field.fieldKey.value
                val raw =
                    fixes.lastOrNull { it.target == ImportTarget.CUSTOM_FIELD && it.fieldKey == key }?.value?.trim()
                        ?: cells(ImportTarget.CUSTOM_FIELD) { it.fieldKey == key }.firstOrNull { !it.value.isNullOrBlank() }?.also { if (it.uncertain) uncertain += key }?.value?.trim()
                raw?.takeIf(String::isNotEmpty)?.let {
                    when (field) {
                        is CustomFieldDefinition.Number -> CustomFieldValue.Number(field.fieldKey, ValueParsers.parseDecimal(it).getOrNull() ?: raise(AssistantImportRowError.INVALID_FIELD to key))
                        is CustomFieldDefinition.Bool -> CustomFieldValue.Bool(field.fieldKey, BOOLEANS[it.lowercase()] ?: raise(AssistantImportRowError.INVALID_FIELD to key))
                        is CustomFieldDefinition.Date -> CustomFieldValue.Date(field.fieldKey, date(it, key) ?: raise(AssistantImportRowError.INVALID_FIELD to key))
                        is CustomFieldDefinition.Select -> CustomFieldValue.Select(field.fieldKey, it)
                        else -> CustomFieldValue.Text(field.fieldKey, it)
                    }
                }
            }
        fields.firstOrNull { field -> field.isRequired && values.none { it.fieldKey == field.fieldKey } }?.let { raise(AssistantImportRowError.FIELD_REQUIRED to it.fieldKey.value) }
        return either { CustomFieldValues(fields).forCreation(values) }.fold({ raise(AssistantImportRowError.INVALID_FIELD to null) }, { it })
    }

    private companion object {
        val BOOLEANS = mapOf("true" to true, "false" to false, "да" to true, "нет" to false, "yes" to true, "no" to false, "1" to true, "0" to false, "+" to true, "-" to false)
    }
}
