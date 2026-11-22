package org.athletica.crm.assistant.storage

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.assistant.AssistantBlock
import org.athletica.crm.api.schemas.assistant.AssistantDialogResponse
import org.athletica.crm.api.schemas.assistant.AssistantDraftStatus
import org.athletica.crm.api.schemas.assistant.AssistantMessage
import org.athletica.crm.api.schemas.assistant.AssistantMessageRole
import org.athletica.crm.api.schemas.assistant.AssistantMessageStatus
import org.athletica.crm.api.schemas.assistant.AssistantSettingsResponse
import org.athletica.crm.assistant.assistantError
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** SQL только по таблицам помощника; контекст организации и сотрудника обязателен. */
class AssistantStorage(private val calendar: OrgCalendar, private val clock: Clock = Clock.System) {
    /** Настройки по умолчанию выключены; строка блокируется перед проверкой квоты. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun settings(available: Boolean, lock: Boolean = false): AssistantSettingsResponse {
        tr.sql("INSERT INTO assistant_settings(org_id) VALUES (:org) ON CONFLICT DO NOTHING")
            .bind("org", ctx.orgId).execute()
        val settings =
            tr.sql("SELECT enabled, daily_message_limit FROM assistant_settings WHERE org_id = :org" + if (lock) " FOR UPDATE" else "")
                .bind("org", ctx.orgId).firstOrNull { row -> row.asBoolean("enabled") to row.asInt("daily_message_limit") }!!
        val date = calendar.today()
        val used =
            tr.sql("SELECT accepted_messages FROM assistant_usage WHERE org_id = :org AND local_date = :date")
                .bind("org", ctx.orgId).bind("date", date).firstOrNull { it.asInt("accepted_messages") } ?: 0
        return AssistantSettingsResponse(available, settings.first, settings.second, used)
    }

    /** Сохраняет валидированные настройки организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun updateSettings(enabled: Boolean, limit: Int) {
        if (limit <= 0) {
            raise(assistantError("VALIDATION"))
        }
        tr.sql("INSERT INTO assistant_settings(org_id, enabled, daily_message_limit) VALUES (:org,:enabled,:limit) ON CONFLICT (org_id) DO UPDATE SET enabled = :enabled, daily_message_limit = :limit")
            .bind("org", ctx.orgId).bind("enabled", enabled).bind("limit", limit).execute()
    }

    /** Возвращает последний диалог, создавая первый при необходимости. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun current(): AssistantDialogResponse {
        val id =
            tr.sql("SELECT id FROM assistant_dialogs WHERE org_id = :org AND employee_id = :employee ORDER BY created_at DESC, id DESC LIMIT 1")
                .bind("org", ctx.orgId).bind("employee", ctx.employee.id).firstOrNull { it.asUuid("id") }
        return if (id == null) create() else dialog(id)
    }

    /** Новый личный диалог; прошлые диалоги сохраняются на сервере. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun create(): AssistantDialogResponse {
        val id = Uuid.random()
        tr.sql("INSERT INTO assistant_dialogs(id,org_id,employee_id) VALUES (:id,:org,:employee)")
            .bind("id", id).bind("org", ctx.orgId).bind("employee", ctx.employee.id).execute()
        return AssistantDialogResponse(id, emptyList())
    }

    /** Короткая блокировка диалога одновременно проверяет его владельца. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun lockDialog(id: Uuid) {
        tr.sql("SELECT id FROM assistant_dialogs WHERE id = :id AND org_id = :org AND employee_id = :employee FOR UPDATE")
            .bind("id", id).bind("org", ctx.orgId).bind("employee", ctx.employee.id).firstOrNull { it.asUuid("id") }
            ?: raise(assistantError("DIALOG_UNAVAILABLE"))
        expire(id)
    }

    /** Просрочка не позволяет позднему ответу оживить прерванный ход. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun expire(id: Uuid) {
        tr.sql("UPDATE assistant_messages SET status = 'failed', error_code = 'ASSISTANT_TIMEOUT' WHERE dialog_id = :dialog AND org_id = :org AND status = 'processing' AND deadline <= :now")
            .bind("dialog", id).bind("org", ctx.orgId).bind("now", clock.now()).execute()
        listOf("assistant_client_drafts", "assistant_import_drafts").forEach { table ->
            tr.sql("UPDATE $table SET status = 'superseded' WHERE dialog_id = :dialog AND org_id = :org AND status = 'pending' AND message_id IN (SELECT id FROM assistant_messages WHERE dialog_id = :dialog AND org_id = :org AND status = 'failed')")
                .bind("dialog", id).bind("org", ctx.orgId).execute()
        }
    }

    /** Новая карточка, клиентская или пакетная, заменяет прежнюю ожидающую карточку диалога. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun supersedePending(dialog: Uuid) {
        listOf("assistant_client_drafts", "assistant_import_drafts").forEach { table ->
            tr.sql("UPDATE $table SET status = 'superseded' WHERE dialog_id = :dialog AND org_id = :org AND status = 'pending'")
                .bind("dialog", dialog).bind("org", ctx.orgId).execute()
        }
    }

    /**
     * Сохранять можно только карточку из завершённого предыдущего обмена ([card] — ход карточки).
     * Прямое подтверждение без [current] отклоняется, пока в диалоге идёт ход: он может заменить карточку.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun requireConfirmable(dialog: Uuid, card: Uuid, current: Uuid?) {
        if (current == null) {
            requireIdle(dialog)
        }
        val confirmable =
            if (current == null) {
                tr.sql("SELECT status FROM assistant_messages WHERE id = :card AND dialog_id = :dialog AND org_id = :org")
                    .bind("card", card).bind("dialog", dialog).bind("org", ctx.orgId).firstOrNull { it.asString("status") } == "completed"
            } else {
                tr.sql("SELECT old.status = 'completed' AND old.position < current.position AS earlier FROM assistant_messages old, assistant_messages current WHERE old.id = :card AND current.id = :current AND old.dialog_id = :dialog AND current.dialog_id = :dialog AND old.org_id = :org AND current.org_id = :org")
                    .bind("card", card).bind("current", current).bind("dialog", dialog).bind("org", ctx.orgId)
                    .firstOrNull { it.get("earlier", Boolean::class.java) } ?: false
            }
        if (!confirmable) {
            raise(assistantError("CONFIRMATION_REQUIRED"))
        }
    }

    /** Сообщения и актуальные состояния карточек восстанавливаются из БД. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun dialog(id: Uuid): AssistantDialogResponse {
        lockDialog(id)
        val drafts =
            tr.sql("SELECT client_id,status FROM assistant_client_drafts WHERE dialog_id = :dialog AND org_id = :org")
                .bind("dialog", id).bind("org", ctx.orgId)
                .list { it.asUuid("client_id") to AssistantDraftStatus.valueOf(it.asString("status").uppercase()) }.toMap()
        val imports =
            tr.sql("SELECT id,status FROM assistant_import_drafts WHERE dialog_id = :dialog AND org_id = :org")
                .bind("dialog", id).bind("org", ctx.orgId)
                .list { it.asUuid("id") to AssistantDraftStatus.valueOf(it.asString("status").uppercase()) }.toMap()
        val messages =
            tr.sql("SELECT id,role,text,status,blocks::text,error_code,attachments::text FROM assistant_messages WHERE dialog_id = :dialog AND org_id = :org ORDER BY position")
                .bind("dialog", id).bind("org", ctx.orgId).list { row ->
                    val blocks =
                        Json.decodeFromString<List<AssistantBlock>>(row.asString("blocks")).map { block ->
                            when (block) {
                                is AssistantBlock.ClientDraft -> block.copy(status = drafts[block.clientId.value] ?: AssistantDraftStatus.SUPERSEDED)
                                is AssistantBlock.ImportDraft -> block.copy(status = imports[block.draftId] ?: AssistantDraftStatus.SUPERSEDED)
                                else -> block
                            }
                        }
                    AssistantMessage(
                        row.asUuid("id"),
                        AssistantMessageRole.valueOf(row.asString("role").uppercase()),
                        row.asString("text"),
                        AssistantMessageStatus.valueOf(row.asString("status").uppercase()),
                        blocks,
                        row.asStringOrNull("error_code"),
                        Json.decodeFromString<List<String>>(row.asString("attachments")),
                    )
                }
        return AssistantDialogResponse(id, messages)
    }

    /**
     * В одной транзакции резервирует квоту и сохраняет вход вместе с ходом. При вложениях [attachments]
     * (имена файлов) текст может быть пустым; [deadline] — срок хода от приёма.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun accept(
        dialogId: Uuid,
        text: String,
        available: Boolean,
        attachments: List<String> = emptyList(),
        deadline: Duration = TURN_DEADLINE,
    ): Uuid {
        if ((text.isBlank() && attachments.isEmpty()) || text.length > 4000) {
            raise(assistantError("VALIDATION"))
        }
        val settings = settings(available, lock = true)
        if (!settings.enabled) {
            raise(assistantError("DISABLED"))
        }
        lockDialog(dialogId)
        requireIdle(dialogId)
        val date = calendar.today()
        val reserved =
            tr.sql("INSERT INTO assistant_usage(org_id,local_date,accepted_messages) VALUES (:org,:date,1) ON CONFLICT (org_id,local_date) DO UPDATE SET accepted_messages = assistant_usage.accepted_messages + 1 WHERE assistant_usage.accepted_messages < :limit RETURNING accepted_messages")
                .bind("org", ctx.orgId).bind("date", date).bind("limit", settings.dailyMessageLimit).firstOrNull { it.asInt("accepted_messages") }
        if (reserved == null) {
            raise(assistantError("LIMIT"))
        }
        insertMessage(dialogId, Uuid.random(), "user", text, "completed", deadline, attachments)
        val id = Uuid.random()
        insertMessage(dialogId, id, "assistant", "", "processing", deadline)
        return id
    }

    /** Отклоняет действие, пока в диалоге идёт ход; вызывается под блокировкой [lockDialog]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun requireIdle(dialog: Uuid) {
        if (tr.sql("SELECT id FROM assistant_messages WHERE dialog_id = :dialog AND org_id = :org AND status = 'processing'")
                .bind("dialog", dialog).bind("org", ctx.orgId).firstOrNull { it.asUuid("id") } != null
        ) {
            raise(assistantError("BUSY"))
        }
    }

    /** Записывает одно сообщение принятого хода. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun insertMessage(
        dialog: Uuid,
        id: Uuid,
        role: String,
        text: String,
        status: String,
        deadline: Duration,
        attachments: List<String> = emptyList(),
    ) {
        tr.sql("INSERT INTO assistant_messages(id,dialog_id,org_id,role,text,status,deadline,attachments) VALUES (:id,:dialog,:org,:role,:text,:status,:deadline,:attachments::jsonb)")
            .bind("id", id).bind("dialog", dialog).bind("org", ctx.orgId).bind("role", role).bind("text", text).bind("status", status).bind("deadline", clock.now() + deadline)
            .bind("attachments", Json.encodeToString(attachments)).execute()
    }

    /** Последние целые успешные обмены, без незавершённых tool call/result. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun history(dialog: Uuid): List<String> {
        lockDialog(dialog)
        return tr.sql("SELECT provider_history::text FROM assistant_messages WHERE dialog_id = :dialog AND org_id = :org AND role = 'assistant' AND status = 'completed' ORDER BY position DESC LIMIT 8")
            .bind("dialog", dialog).bind("org", ctx.orgId).list { it.asString("provider_history") }.reversed()
    }

    /** Проверяет живой ход перед каждым tool и завершением. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun requireProcessing(dialog: Uuid, message: Uuid) {
        lockDialog(dialog)
        if (tr.sql("SELECT id FROM assistant_messages WHERE id = :id AND dialog_id = :dialog AND org_id = :org AND status = 'processing' AND deadline > :now")
                .bind("id", message).bind("dialog", dialog).bind("org", ctx.orgId).bind("now", clock.now()).firstOrNull { it.asUuid("id") } == null
        ) {
            raise(assistantError("TIMEOUT"))
        }
    }

    /** Сохраняет ответ, только пока ход жив; квота уже принята и не возвращается. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun finish(
        dialog: Uuid,
        message: Uuid,
        text: String,
        blocks: List<AssistantBlock>,
        history: String,
        error: String? = null,
    ) {
        lockDialog(dialog)
        tr.sql("UPDATE assistant_messages SET text = :text, blocks = :blocks::jsonb, provider_history = :history::jsonb, status = :status, error_code = :error WHERE id = :id AND dialog_id = :dialog AND org_id = :org AND status = 'processing' AND deadline > :now")
            .bind("id", message).bind("dialog", dialog).bind("org", ctx.orgId).bind("text", text).bind("blocks", Json.encodeToString(blocks))
            .bind("history", history).bind("status", if (error == null) "completed" else "failed").bind("error", error).bind("now", clock.now()).execute()
    }

    companion object {
        /** Срок жизни хода от приёма сообщения; после него ответ модели не сохраняется. */
        val TURN_DEADLINE = 45.seconds

        /** Срок хода с вложениями: распознавание фото и подготовка импорта не укладываются в обычный. */
        val ATTACHMENTS_TURN_DEADLINE = 180.seconds
    }
}
