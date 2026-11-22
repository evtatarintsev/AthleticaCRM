package org.athletica.crm.assistant.imports

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.athletica.crm.api.schemas.assistant.AssistantBlock
import org.athletica.crm.api.schemas.assistant.AssistantDraftStatus
import org.athletica.crm.api.schemas.assistant.AssistantImportCounts
import org.athletica.crm.api.schemas.assistant.AssistantImportLimits
import org.athletica.crm.api.schemas.assistant.AssistantImportRow
import org.athletica.crm.api.schemas.assistant.AssistantImportRowStatus
import org.athletica.crm.assistant.assistantError
import org.athletica.crm.assistant.storage.AssistantStorage
import org.athletica.crm.assistant.tools.validateContacts
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

/** Строки черновика и решение о возможных дублях, по которым он повторно проверяется перед записью. */
@Serializable
data class StoredImportDraft(
    /** Разобранные строки источника со статусами на момент подготовки. */
    val rows: List<AssistantImportRow>,
    /** Стабильные ID будущих клиентов по номеру строки: повтор подтверждения ничего не дублирует. */
    val clientIds: Map<Int, ClientId>,
    /** Решение о строках, совпавших только по ФИО. */
    val possibleDuplicates: PossibleDuplicates,
    /** Строки, включённые в создание при решении [PossibleDuplicates.ROWS]. */
    val includeDuplicateRows: Set<Int> = emptySet(),
    /** Подписи доп. полей по ключу для карточки. */
    val customFieldLabels: Map<String, String> = emptyMap(),
)

/** Квитанция пакета. */
@Serializable
data class ImportReceipt(
    /** Сколько клиентов создано. */
    val created: Int,
    /** ID созданных клиентов. */
    val clientIds: List<ClientId>,
)

/** Загрузка, приложенная к сообщению: метаданные из таблицы `uploads`. */
class AttachedUpload(
    /** ID загрузки. */
    val id: UploadId,
    /** Ключ объекта в хранилище. */
    val objectKey: String,
    /** Исходное имя файла для ленты. */
    val name: String,
    /** Размер файла в байтах. */
    val sizeBytes: Long,
)

/** Пакетные черновики импорта: источник диалога, подготовка, атомарная запись. */
class ImportDrafts(
    private val storage: AssistantStorage,
    private val clients: Clients,
    private val contacts: ClientContacts,
    private val definitions: CustomFieldDefinitions,
) {
    /**
     * Загрузки [ids] сотрудника, ещё нигде не используемые: чужая, уже прикреплённая к клиенту или
     * задаче загрузка недоступна, потому что после получения строк файл удаляется.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun uploads(ids: List<UploadId>): List<AttachedUpload> {
        val found =
            tr.sql(
                """
                SELECT u.id, u.object_key, u.original_name, u.size_bytes FROM uploads u
                WHERE u.id = ANY(:ids) AND u.org_id = :org AND u.uploaded_by = :user
                  AND NOT EXISTS (SELECT 1 FROM clients WHERE avatar_id = u.id)
                  AND NOT EXISTS (SELECT 1 FROM employees WHERE avatar_id = u.id)
                  AND NOT EXISTS (SELECT 1 FROM client_docs WHERE upload_id = u.id)
                  AND NOT EXISTS (SELECT 1 FROM task_attachments WHERE upload_id = u.id)
                """.trimIndent(),
            ).bind("ids", ids).bind("org", ctx.orgId).bind("user", ctx.userId)
                .list { AttachedUpload(UploadId(it.asUuid("id")), it.asString("object_key"), it.asString("original_name"), it.asLong("size_bytes")) }
                .associateBy { it.id }
        return ids.distinct().map { found[it] ?: raise(assistantError("IMPORT_FILE_UNAVAILABLE")) }
    }

    /** Сохраняет строки хода [message] и удаляет записи загрузок [uploads]; объекты удаляет вызывающий код. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun saveSource(
        dialog: Uuid,
        message: Uuid,
        kind: String,
        source: ImportSource,
        uploads: List<AttachedUpload>,
    ): Uuid {
        storage.requireProcessing(dialog, message)
        val id = Uuid.random()
        tr.sql("INSERT INTO assistant_import_sources(id,dialog_id,org_id,message_id,kind,data) VALUES (:id,:dialog,:org,:message,:kind,:data::jsonb)")
            .bind("id", id).bind("dialog", dialog).bind("org", ctx.orgId).bind("message", message).bind("kind", kind).bind("data", Json.encodeToString(source)).execute()
        tr.sql("DELETE FROM uploads WHERE id = ANY(:ids) AND org_id = :org").bind("ids", uploads.map { it.id }).bind("org", ctx.orgId).execute()
        return id
    }

    /**
     * Применяет сопоставление ко всем строкам источника своего диалога и заменяет ожидающую карточку.
     * Возвращает карточку и сводку для модели: без значений строк, кроме имён без пола.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun prepare(dialog: Uuid, message: Uuid, args: PrepareImportArgs): Pair<AssistantBlock.ImportDraft, String> {
        storage.requireProcessing(dialog, message)
        val sourceId =
            try {
                Uuid.parse(args.sourceId)
            } catch (_: IllegalArgumentException) {
                raise(assistantError("VALIDATION"))
            }
        val source =
            tr.sql("SELECT data::text FROM assistant_import_sources WHERE id = :id AND dialog_id = :dialog AND org_id = :org")
                .bind("id", sourceId).bind("dialog", dialog).bind("org", ctx.orgId).firstOrNull { Json.decodeFromString<ImportSource>(it.asString("data")) }
                ?: raise(assistantError("DIALOG_UNAVAILABLE"))
        val fields = definitions.all("CLIENT")
        val parsed = ImportRows.parse(source, args, fields)
        val included = args.includeDuplicateRows.toSet()
        val rows = ImportRows.classify(parsed.candidates, existing(parsed.candidates), args.possibleDuplicates, included)
        val ids = rows.filter { it.client != null }.associate { it.row to ClientId.new() }
        val stored = StoredImportDraft(rows, ids, args.possibleDuplicates, included, fields.associate { it.fieldKey.value to it.label })
        val id = Uuid.random()
        storage.supersedePending(dialog)
        tr.sql("INSERT INTO assistant_import_drafts(id,dialog_id,org_id,message_id,source_id,payload,status) VALUES (:id,:dialog,:org,:message,:source,:payload::jsonb,'pending')")
            .bind("id", id).bind("dialog", dialog).bind("org", ctx.orgId).bind("message", message).bind("source", sourceId).bind("payload", Json.encodeToString(stored)).execute()
        val block = block(id, stored, AssistantDraftStatus.PENDING)
        return block to summary(block, rows, parsed)
    }

    /** Диалог пакетной карточки только у её владельца, без раскрытия чужих ID. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun dialogOf(draft: Uuid): Uuid =
        tr.sql("SELECT d.id FROM assistant_import_drafts i JOIN assistant_dialogs d ON d.id = i.dialog_id AND d.org_id = i.org_id WHERE i.id = :draft AND d.org_id = :org AND d.employee_id = :employee")
            .bind("draft", draft).bind("org", ctx.orgId).bind("employee", ctx.employee.id).firstOrNull { it.asUuid("id") }
            ?: raise(assistantError("DIALOG_UNAVAILABLE"))

    /**
     * Создаёт всех готовых клиентов карточки в транзакции вызывающего кода; повтор возвращает ту же
     * квитанцию. Если перед записью готовая строка перестала быть готовой — `IMPORT_DRAFT_STALE` без записи.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save(dialog: Uuid, draftId: Uuid, currentMessage: Uuid? = null): AssistantBlock.ImportCompleted {
        storage.lockDialog(dialog)
        if (currentMessage != null) {
            storage.requireProcessing(dialog, currentMessage)
        }
        val draft =
            tr.sql("SELECT status,payload::text,receipt::text,message_id FROM assistant_import_drafts WHERE id = :id AND dialog_id = :dialog AND org_id = :org FOR UPDATE")
                .bind("id", draftId).bind("dialog", dialog).bind("org", ctx.orgId).firstOrNull { row ->
                    listOf(row.asString("status"), row.asString("payload"), row.get("receipt", String::class.java), row.asUuid("message_id").toString())
                } ?: raise(assistantError("DIALOG_UNAVAILABLE"))
        if (draft[0] == "executed") {
            return AssistantBlock.ImportCompleted(draftId, Json.decodeFromString<ImportReceipt>(checkNotNull(draft[2])).created)
        }
        if (draft[0] != "pending") {
            raise(assistantError("DRAFT_SUPERSEDED"))
        }
        storage.requireConfirmable(dialog, Uuid.parse(checkNotNull(draft[3])), currentMessage)
        val stored = Json.decodeFromString<StoredImportDraft>(checkNotNull(draft[1]))
        val candidates = stored.rows.map(ImportRows::candidate)
        val current = ImportRows.classify(candidates, existing(candidates), stored.possibleDuplicates, stored.includeDuplicateRows)
        val ready = stored.rows.filter { it.status == AssistantImportRowStatus.READY }
        if (current.filter { it.status == AssistantImportRowStatus.READY }.map { it.row } != ready.map { it.row }) {
            raise(assistantError("IMPORT_DRAFT_STALE"))
        }
        val created =
            ready.map { row ->
                val client = checkNotNull(row.client)
                val id = checkNotNull(stored.clientIds[row.row])
                val checked = validateContacts(client.contacts, id)
                val saved = clients.new(id, NonBlankName.from(client.name), null, client.birthday, client.gender, customFields = client.customFields)
                contacts.replace(saved, checked.map { ClientContact(ClientContactId.new(), id, it.type, it.value, it.label, it.isPrimary) })
                id
            }
        tr.sql("UPDATE assistant_import_drafts SET status = 'executed', receipt = :receipt::jsonb WHERE id = :id AND dialog_id = :dialog AND org_id = :org")
            .bind("id", draftId).bind("dialog", dialog).bind("org", ctx.orgId).bind("receipt", Json.encodeToString(ImportReceipt(created.size, created))).execute()
        return AssistantBlock.ImportCompleted(draftId, created.size)
    }

    /** Один SQL по организации: клиенты с совпадающим нормализованным телефоном или ФИО. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun existing(candidates: List<ImportCandidate>): ExistingClients {
        val phones = candidates.flatMap { it.phones }.distinct()
        val names = candidates.mapNotNull { it.nameKey }.distinct()
        if (phones.isEmpty() && names.isEmpty()) {
            return ExistingClients(emptyMap(), emptyMap())
        }
        val found =
            tr.sql(
                """
                SELECT 'phone' AS kind, digits AS key, client_id FROM (
                    SELECT client_id, CASE WHEN length(d) = 11 AND left(d, 1) = '8' THEN '7' || substr(d, 2) ELSE d END AS digits
                    FROM (SELECT client_id, regexp_replace(value, '[^0-9]', '', 'g') AS d FROM client_contacts WHERE org_id = :org AND type = 'PHONE') raw
                ) phones WHERE digits = ANY(:phones)
                UNION ALL
                SELECT 'name', lower(regexp_replace(replace(replace(trim(name), 'ё', 'е'), 'Ё', 'Е'), '\s+', ' ', 'g')), id
                FROM clients WHERE org_id = :org AND lower(regexp_replace(replace(replace(trim(name), 'ё', 'е'), 'Ё', 'Е'), '\s+', ' ', 'g')) = ANY(:names)
                ORDER BY 3
                """.trimIndent(),
            ).bind("org", ctx.orgId).bind("phones", phones.toTypedArray()).bind("names", names.toTypedArray())
                .list { Triple(it.asString("kind"), it.asString("key"), it.asUuid("client_id").toClientId()) }
        return ExistingClients(
            found.filter { it.first == "phone" }.associate { it.second to it.third },
            found.filter { it.first == "name" }.associate { it.second to it.third },
        )
    }

    private companion object {
        /** Сколько номеров строк одного кода ошибки получает модель. */
        const val ERROR_ROWS = 30

        /** Карточка: счётчики, проблемные строки и превью готовых. */
        fun block(id: Uuid, stored: StoredImportDraft, status: AssistantDraftStatus): AssistantBlock.ImportDraft {
            val rows = stored.rows
            val counts =
                AssistantImportCounts(
                    total = rows.size,
                    ready = rows.count { it.status == AssistantImportRowStatus.READY },
                    exists = rows.count { it.status == AssistantImportRowStatus.EXISTS },
                    duplicateInFile = rows.count { it.status == AssistantImportRowStatus.DUPLICATE_IN_FILE },
                    possibleDuplicate = rows.count { it.status == AssistantImportRowStatus.POSSIBLE_DUPLICATE },
                    error = rows.count { it.status == AssistantImportRowStatus.ERROR },
                    warnings = rows.count { it.status == AssistantImportRowStatus.READY && it.warnings.isNotEmpty() },
                )
            return AssistantBlock.ImportDraft(
                id,
                status,
                counts,
                rows.filter { it.status != AssistantImportRowStatus.READY || it.warnings.isNotEmpty() }.take(AssistantImportLimits.PROBLEM_ROWS),
                rows.filter { it.status == AssistantImportRowStatus.READY }.take(AssistantImportLimits.PREVIEW_ROWS),
                stored.customFieldLabels,
            )
        }

        /** Сводка для модели: счётчики, коды ошибок с номерами строк и имена без пола. */
        fun summary(block: AssistantBlock.ImportDraft, rows: List<AssistantImportRow>, parsed: ParsedImport): String {
            val errors =
                rows.filter { it.status == AssistantImportRowStatus.ERROR }.groupBy { "${it.error?.name}${it.errorField?.let { f -> ":$f" }.orEmpty()}" }
                    .mapValues { (_, list) -> JsonArray(list.take(ERROR_ROWS).map { JsonPrimitive(it.row) }) }
            val possible = rows.filter { it.status == AssistantImportRowStatus.POSSIBLE_DUPLICATE }.map { JsonPrimitive(it.row) }
            return Json.encodeToString(
                JsonObject(
                    mapOf(
                        "draftId" to JsonPrimitive(block.draftId.toString()),
                        "counts" to Json.encodeToJsonElement(AssistantImportCounts.serializer(), block.counts),
                        "errors" to JsonObject(errors),
                        "possibleDuplicateRows" to JsonArray(possible.take(ERROR_ROWS)),
                        "uncertainCells" to JsonPrimitive(parsed.uncertainCells),
                        "namesWithoutGender" to JsonArray(parsed.namesWithoutGender.map(::JsonPrimitive)),
                        "note" to JsonPrimitive("The server displays the import card. Nothing is created until the user confirms it in a later message."),
                    ),
                ),
            )
        }
    }
}
