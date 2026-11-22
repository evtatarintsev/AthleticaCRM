package org.athletica.crm.assistant.imports

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.assistant.AssistantImportRow
import org.athletica.crm.api.schemas.assistant.AssistantImportRowError
import org.athletica.crm.api.schemas.assistant.AssistantImportRowStatus
import org.athletica.crm.api.schemas.assistant.AssistantImportWarning
import org.athletica.crm.core.Gender
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.customfields.toFieldKey
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Применение сопоставления к строкам: каждое правило и независимость ошибок строк. */
class ImportRowsTest {
    private val header = listOf("ФИО", "Пол", "Дата", "Телефон", "Email", "Уровень")
    private val level = either { CustomFieldDefinition.Text("level".toFieldKey(), "Уровень", isRequired = true) }.getOrNull()!!

    private fun source(vararg rows: List<String?>, uncertain: Set<Pair<Int, Int>> = emptySet()) =
        ImportSource(
            listOf(
                SourceSheet(
                    "Лист1",
                    (listOf(header) + rows).mapIndexed { i, cells -> SourceRow(i + 1, i + 1, null, cells.mapIndexed { c, v -> SourceCell(v, (i + 1 to c) in uncertain) }) },
                ),
            ),
        )

    private val columns =
        listOf(
            ImportColumn(0, ImportTarget.NAME),
            ImportColumn(1, ImportTarget.GENDER),
            ImportColumn(2, ImportTarget.BIRTHDAY),
            ImportColumn(3, ImportTarget.CONTACT, ContactType.PHONE, "мама"),
            ImportColumn(4, ImportTarget.CONTACT, ContactType.EMAIL),
            ImportColumn(5, ImportTarget.CUSTOM_FIELD, fieldKey = "level"),
        )

    private fun args(vararg extra: (PrepareImportArgs) -> PrepareImportArgs) = extra.fold(PrepareImportArgs("s", headerRow = 1, columns = columns, genderValues = mapOf("м" to Gender.MALE, "Ж" to Gender.FEMALE))) { a, f -> f(a) }

    private fun parse(source: ImportSource, args: PrepareImportArgs = args()): ParsedImport = either { ImportRows.parse(source, args, listOf(level)) }.getOrNull()!!

    private fun rows(source: ImportSource, args: PrepareImportArgs = args()) = parse(source, args).candidates.map { it.view }

    @Test
    fun mapsAllFields() {
        val row = rows(source(listOf("  Иванов   Иван ", "М", "05.03.2014", "8 909 130-22-11; 8(909)1302212", "a@b.ru", "Новичок"))).single()
        assertEquals(AssistantImportRowStatus.READY, row.status)
        val client = row.client!!
        assertEquals("Иванов Иван", client.name)
        assertEquals(Gender.MALE, client.gender)
        assertEquals(LocalDate(2014, 3, 5), client.birthday)
        assertEquals(listOf("8 909 130-22-11", "8(909)1302212", "a@b.ru"), client.contacts.map { it.value })
        assertEquals("мама", client.contacts.first().label)
        assertEquals(listOf<CustomFieldValue>(CustomFieldValue.Text(level.fieldKey, "Новичок")), client.customFields)
    }

    /** Строка 12 с некорректным email не мешает остальным 49. */
    @Test
    fun rowErrorDoesNotBlockOthers() {
        val data = List(50) { listOf("Клиент $it", "Ж", null, null, if (it == 10) "не-email" else null, "A") }
        val rows = rows(source(*data.toTypedArray()))
        assertEquals(49, rows.count { it.status == AssistantImportRowStatus.READY })
        val bad = rows.single { it.status == AssistantImportRowStatus.ERROR }
        assertEquals(12, bad.row)
        assertEquals(AssistantImportRowError.INVALID_CONTACT, bad.error)
        assertEquals("EMAIL", bad.errorField)
    }

    @Test
    fun errorsPerRule() {
        val rows =
            rows(
                source(
                    listOf(null, "М", null, null, null, "A"),
                    listOf("Петров", "М", "весной", null, null, "A"),
                    listOf("Сидоров", "М", null, null, null, null),
                    listOf("Кто-то", "?", null, null, null, "A"),
                ),
            )
        assertEquals(
            listOf(AssistantImportRowError.NAME_REQUIRED, AssistantImportRowError.INVALID_DATE, AssistantImportRowError.FIELD_REQUIRED, AssistantImportRowError.GENDER_REQUIRED),
            rows.map { it.error },
        )
        assertEquals("level", rows[2].errorField)
    }

    @Test
    fun namePartsDateFormatFixesAndExclusions() {
        val parts =
            listOf(
                ImportColumn(0, ImportTarget.LAST_NAME),
                ImportColumn(1, ImportTarget.FIRST_NAME),
                ImportColumn(2, ImportTarget.MIDDLE_NAME),
                ImportColumn(3, ImportTarget.BIRTHDAY),
                ImportColumn(4, ImportTarget.SKIP),
                ImportColumn(5, ImportTarget.CUSTOM_FIELD, fieldKey = "level"),
            )
        val src = source(listOf("Петров", "Иван", "Сергеевич", "04/03/2015", "100", "A"), listOf("Сидорова", "Анна", null, "05/03/2015", null, "A"), listOf("Лишний", "Ян", null, null, null, "A"))
        val args =
            args({ it.copy(columns = parts, dateFormat = "MM/dd/yyyy", genderByFirstName = mapOf("иван" to Gender.MALE, "Анна" to Gender.FEMALE)) }, {
                it.copy(rowFixes = listOf(ImportRowFix(2, ImportTarget.LAST_NAME, "Петрова")), excludeRows = listOf(4))
            })
        val rows = rows(src, args)
        assertEquals(listOf("Петрова Иван Сергеевич", "Сидорова Анна"), rows.map { it.client?.name })
        assertEquals(LocalDate(2015, 4, 3), rows.first().client?.birthday)
        assertEquals(listOf(Gender.MALE, Gender.FEMALE), rows.map { it.client?.gender })
    }

    /** Неуверенный телефон выбрасывается, неуверенное имя остаётся с предупреждением. */
    @Test
    fun uncertainCells() {
        val parsed = parse(source(listOf("Иванов Иван", "М", null, "89091302211", null, "A"), uncertain = setOf(2 to 0, 2 to 3)))
        val row = parsed.candidates.single().view
        assertEquals(AssistantImportRowStatus.READY, row.status)
        assertEquals(emptyList(), row.client!!.contacts)
        assertEquals(listOf(AssistantImportWarning.CONTACT_DROPPED, AssistantImportWarning.UNCERTAIN), row.warnings)
        assertEquals(listOf("name"), row.uncertainFields)
        assertEquals(2, parsed.uncertainCells)
    }

    /** Колонка пола → имя → пол по умолчанию → ошибка; имена без пола попадают в сводку. */
    @Test
    fun genderOrder() {
        val noGender = columns.filter { it.target != ImportTarget.GENDER }
        val src = source(listOf("Иванов Иван", null, null, null, null, "A"), listOf("Петрова Мария", null, null, null, null, "A"), listOf("Ким Саша", null, null, null, null, "A"))
        val first = parse(src, args({ it.copy(columns = noGender) }))
        assertEquals(listOf("Иван", "Мария", "Саша"), first.namesWithoutGender)
        assertEquals(3, first.candidates.count { it.view.error == AssistantImportRowError.GENDER_REQUIRED })
        val byName = rows(src, args({ it.copy(columns = noGender, genderByFirstName = mapOf("Иван" to Gender.MALE, "Мария" to Gender.FEMALE)) }))
        assertEquals(listOf(Gender.MALE, Gender.FEMALE, null), byName.map { it.client?.gender })
        val withDefault = rows(src, args({ it.copy(columns = noGender, genderByFirstName = mapOf("Иван" to Gender.MALE), defaultGender = Gender.FEMALE) }))
        assertEquals(listOf(Gender.MALE, Gender.FEMALE, Gender.FEMALE), withDefault.map { it.client?.gender })
    }

    @Test
    fun sheetAndColumnArguments() {
        val two = ImportSource(listOf(SourceSheet("A", emptyList()), SourceSheet("B", emptyList())))

        fun code(source: ImportSource, args: PrepareImportArgs) = assertIs<Either.Left<AppError>>(either { ImportRows.parse(source, args, listOf(level)) }).value.code
        assertEquals("ASSISTANT_IMPORT_SHEET_REQUIRED", code(two, args()))
        assertEquals("ASSISTANT_IMPORT_SHEET_UNKNOWN", code(two, args({ it.copy(sheet = "C") })))
        assertEquals("ASSISTANT_VALIDATION", code(source(), args({ it.copy(columns = listOf(ImportColumn(0, ImportTarget.CUSTOM_FIELD, fieldKey = "balance"))) })))
    }

    /** Телефон организации, повтор в файле, совпадение только по ФИО и решение о возможных дублях. */
    @Test
    fun classifiesDuplicates() {
        val src =
            source(
                listOf("Иванов Иван", "М", null, "89091302211", null, "A"),
                listOf("Петров Пётр", "М", null, "+7 909 130 22 12", null, "A"),
                listOf("Другой", "М", null, "89091302212", null, "A"),
                listOf("Петров  петр", "М", null, null, null, "A"),
                listOf("Сидоров", "М", null, null, null, "A"),
            )
        val candidates = parse(src).candidates
        val known = ClientId.new()
        val namesake = ClientId.new()
        val existing = ExistingClients(mapOf("79091302211" to known), mapOf("сидоров" to namesake))

        fun statuses(rows: List<AssistantImportRow>) = rows.map { it.status }
        val skipped = ImportRows.classify(candidates, existing, PossibleDuplicates.SKIP, emptySet())
        assertEquals(
            listOf(AssistantImportRowStatus.EXISTS, AssistantImportRowStatus.READY, AssistantImportRowStatus.DUPLICATE_IN_FILE, AssistantImportRowStatus.POSSIBLE_DUPLICATE, AssistantImportRowStatus.POSSIBLE_DUPLICATE),
            statuses(skipped),
        )
        assertEquals(known, skipped[0].existingClientId)
        assertEquals(namesake, skipped[4].existingClientId)
        val included = ImportRows.classify(candidates, existing, PossibleDuplicates.INCLUDE, emptySet())
        assertEquals(AssistantImportRowStatus.READY, included[4].status)
        assertEquals(listOf(AssistantImportWarning.POSSIBLE_DUPLICATE), included[4].warnings)
        val chosen = ImportRows.classify(candidates, existing, PossibleDuplicates.ROWS, setOf(6))
        assertEquals(listOf(AssistantImportRowStatus.POSSIBLE_DUPLICATE, AssistantImportRowStatus.READY), statuses(chosen).takeLast(2))
        assertEquals(statuses(included), statuses(ImportRows.classify(included.map(ImportRows::candidate), existing, PossibleDuplicates.INCLUDE, emptySet())))
    }

    /** Исправление контакта заменяет одну колонку с её подписью, другой телефон остаётся; без колонки — добавляется. */
    @Test
    fun contactFixReplacesOneColumn() {
        val withFather = columns + ImportColumn(6, ImportTarget.CONTACT, ContactType.PHONE, "папа")
        val src =
            ImportSource(
                listOf(
                    SourceSheet(
                        "Лист1",
                        listOf(
                            SourceRow(1, 1, null, (header + "Телефон папы").map { SourceCell(it) }),
                            SourceRow(2, 2, null, listOf("Иванов Иван", "М", null, "89091302211", null, "A", "89091302212").map { SourceCell(it) }),
                        ),
                    ),
                ),
            )

        fun contacts(fix: ImportRowFix) = rows(src, args({ it.copy(columns = withFather, rowFixes = listOf(fix)) })).single().client!!.contacts.map { it.value to it.label }.toSet()
        assertEquals(setOf("89091302211" to "мама", "89091300000" to "папа"), contacts(ImportRowFix(2, ImportTarget.CONTACT, "89091300000", ContactType.PHONE, index = 6)))
        assertEquals(setOf("89091300000" to "мама", "89091302212" to "папа"), contacts(ImportRowFix(2, ImportTarget.CONTACT, "89091300000", ContactType.PHONE)))
        assertEquals(
            setOf("89091302211" to "мама", "89091302212" to "папа", "c@d.ru" to null),
            contacts(ImportRowFix(2, ImportTarget.CONTACT, "c@d.ru", ContactType.EMAIL, index = 9)),
        )
    }

    /** Заголовок следующего фото с мелкими расхождениями распознавания не становится клиентом. */
    @Test
    fun dropsRepeatedHeader() {
        val src = source(listOf("Иванов Иван", "М", null, null, null, "A"), listOf("фио.", "Пол", "Дата ", null, "E-mail", "Уровень"), listOf("Петров Пётр", "М", null, null, null, "A"))
        assertEquals(listOf("Иванов Иван", "Петров Пётр"), rows(src).map { it.name })
    }
}
