package org.athletica.crm.assistant.tools

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.assistant.AssistantBlock
import org.athletica.crm.api.schemas.assistant.AssistantClientPayload
import org.athletica.crm.api.schemas.assistant.AssistantDraftStatus
import org.athletica.crm.api.schemas.clients.ClientContactInput
import org.athletica.crm.assistant.assistantError
import org.athletica.crm.assistant.storage.AssistantStorage
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.customfields.CustomFieldValues
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

/** Данные tool до проверки существующими доменными типами. */
@Serializable
internal data class PrepareClientArgs(
    val name: String? = null,
    val gender: Gender? = null,
    val birthday: String? = null,
    val contacts: List<ClientContactInput> = emptyList(),
    val customFields: Map<String, String> = emptyMap(),
)

/** Только ID ранее показанного черновика, без произвольных данных для записи. */
@Serializable
internal data class SaveClientArgs(val clientId: String)

/** Подготовка и атомарная доменная запись одного подтверждаемого клиента. */
class ClientDrafts(
    private val storage: AssistantStorage,
    private val clients: Clients,
    private val contacts: ClientContacts,
    private val definitions: CustomFieldDefinitions,
) {
    /** Проверяет данные, не создавая доменных записей. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    internal suspend fun prepare(dialog: Uuid, message: Uuid, args: PrepareClientArgs): AssistantBlock.ClientDraft {
        storage.requireProcessing(dialog, message)
        val name = NonBlankName.from(args.name.orEmpty())
        val gender = args.gender ?: raise(assistantError("GENDER_REQUIRED"))
        val fields = definitions.all("CLIENT")
        val unknown = args.customFields.keys - fields.map { it.fieldKey.value }.toSet()
        if (unknown.isNotEmpty()) {
            raise(assistantError("VALIDATION"))
        }
        val values =
            fields.mapNotNull { field ->
                args.customFields[field.fieldKey.value]?.let { raw ->
                    when (field) {
                        is CustomFieldDefinition.Number -> CustomFieldValue.Number(field.fieldKey, raw.toDoubleOrNull() ?: raise(assistantError("VALIDATION")))
                        is CustomFieldDefinition.Bool -> CustomFieldValue.Bool(field.fieldKey, raw.toBooleanStrictOrNull() ?: raise(assistantError("VALIDATION")))
                        is CustomFieldDefinition.Date -> CustomFieldValue.Date(field.fieldKey, date(raw))
                        is CustomFieldDefinition.Select -> CustomFieldValue.Select(field.fieldKey, raw)
                        else -> CustomFieldValue.Text(field.fieldKey, raw)
                    }
                }
            }
        fields.filter { it.isRequired && args.customFields[it.fieldKey.value].isNullOrBlank() }.firstOrNull()?.let {
            raise(CommonDomainError("ASSISTANT_FIELD_REQUIRED", "${it.label} (${it.fieldKey.value})"))
        }
        val payload =
            AssistantClientPayload(
                name.value,
                gender,
                args.birthday?.let { date(it) },
                validateContacts(args.contacts),
                CustomFieldValues(fields).forCreation(values),
                fields.associate { it.fieldKey.value to it.label },
            )
        val id = ClientId.new()
        tr.sql("UPDATE assistant_client_drafts SET status = 'superseded' WHERE dialog_id = :dialog AND org_id = :org AND status = 'pending'")
            .bind("dialog", dialog).bind("org", ctx.orgId).execute()
        tr.sql("INSERT INTO assistant_client_drafts(client_id,dialog_id,org_id,message_id,payload,status) VALUES (:id,:dialog,:org,:message,:payload::jsonb,'pending')")
            .bind("id", id).bind("dialog", dialog).bind("org", ctx.orgId).bind("message", message).bind("payload", Json.encodeToString(payload)).execute()
        return AssistantBlock.ClientDraft(id, payload, AssistantDraftStatus.PENDING)
    }

    /** Читает диалог карточки только у её владельца, без раскрытия чужих ID. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun dialogOf(client: ClientId): Uuid =
        tr.sql("SELECT d.id FROM assistant_client_drafts c JOIN assistant_dialogs d ON d.id = c.dialog_id AND d.org_id = c.org_id WHERE c.client_id = :client AND d.org_id = :org AND d.employee_id = :employee")
            .bind("client", client).bind("org", ctx.orgId).bind("employee", ctx.employee.id).firstOrNull { it.asUuid("id") }
            ?: raise(assistantError("DIALOG_UNAVAILABLE"))

    /** Повтор возвращает ту же квитанцию; вся запись выполняется в транзакции вызывающего кода. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save(dialog: Uuid, client: ClientId, currentMessage: Uuid? = null): AssistantBlock.ClientCreated {
        storage.lockDialog(dialog)
        if (currentMessage != null) {
            storage.requireProcessing(dialog, currentMessage)
        }
        val draft =
            tr.sql("SELECT c.status,c.payload::text,c.message_id,m.status AS message_status,m.position FROM assistant_client_drafts c JOIN assistant_messages m ON m.id = c.message_id AND m.dialog_id = c.dialog_id AND m.org_id = c.org_id WHERE c.client_id = :client AND c.dialog_id = :dialog AND c.org_id = :org FOR UPDATE OF c")
                .bind("client", client).bind("dialog", dialog).bind("org", ctx.orgId).firstOrNull { row ->
                    Triple(row.asString("status"), row.asString("payload"), row.asString("message_status") to row.asUuid("message_id"))
                } ?: raise(assistantError("DIALOG_UNAVAILABLE"))
        if (draft.first == "executed") {
            return AssistantBlock.ClientCreated(client)
        }
        if (draft.first != "pending") {
            raise(assistantError("DRAFT_SUPERSEDED"))
        }
        if (draft.third.first != "completed" || draft.third.second == currentMessage) {
            raise(assistantError("CONFIRMATION_REQUIRED"))
        }
        if (currentMessage != null) {
            val earlier =
                tr.sql("SELECT old.position < current.position AS earlier FROM assistant_messages old, assistant_messages current WHERE old.id = :old AND current.id = :current AND old.dialog_id = :dialog AND current.dialog_id = :dialog AND old.org_id = :org AND current.org_id = :org")
                    .bind("old", draft.third.second).bind("current", currentMessage).bind("dialog", dialog).bind("org", ctx.orgId)
                    .firstOrNull { it.get("earlier", Boolean::class.java) } ?: false
            if (!earlier) {
                raise(assistantError("CONFIRMATION_REQUIRED"))
            }
        }
        val payload = Json.decodeFromString<AssistantClientPayload>(draft.second)
        val checkedContacts = validateContacts(payload.contacts)
        val created = clients.new(client, NonBlankName.from(payload.name), null, payload.birthday, payload.gender, customFields = payload.customFields)
        contacts.replace(created, checkedContacts.map { ClientContact(ClientContactId.new(), client, it.type, it.value) })
        tr.sql("UPDATE assistant_client_drafts SET status = 'executed' WHERE client_id = :id AND dialog_id = :dialog AND org_id = :org")
            .bind("id", client).bind("dialog", dialog).bind("org", ctx.orgId).execute()
        return AssistantBlock.ClientCreated(client)
    }

    /** Разбирает календарную дату на границе аргументов модели. */
    context(raise: Raise<AppError>)
    private fun date(raw: String): LocalDate =
        try {
            LocalDate.parse(raw)
        } catch (_: IllegalArgumentException) {
            raise(assistantError("VALIDATION"))
        }

    /** Проверяет контакты до карточки и повторно перед сохранением. */
    context(raise: Raise<AppError>)
    private fun validateContacts(values: List<ClientContactInput>): List<ClientContactInput> =
        values.map { contact ->
            val value = contact.value.trim()
            val valid =
                when (contact.type) {
                    ContactType.PHONE -> value.all { it.isDigit() || it in "+()- ." } && PhoneDigits.from(value)?.value?.length in 7..15
                    ContactType.EMAIL -> value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                    else -> value.isNotBlank()
                }
            if (!valid || value.length > 512) {
                raise(assistantError("VALIDATION"))
            }
            contact.copy(value = value)
        }
}
