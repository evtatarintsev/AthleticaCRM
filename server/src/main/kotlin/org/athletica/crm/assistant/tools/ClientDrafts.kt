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
import org.athletica.crm.domain.clientcontacts.normalized
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
        val id = ClientId.new()
        val payload =
            AssistantClientPayload(
                name.value,
                gender,
                args.birthday?.let { date(it) },
                validateContacts(args.contacts, id),
                CustomFieldValues(fields).forCreation(values),
                fields.associate { it.fieldKey.value to it.label },
            )
        storage.supersedePending(dialog)
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

    /**
     * Повтор возвращает ту же квитанцию; вся запись выполняется в транзакции вызывающего кода.
     * Прямое подтверждение без [currentMessage] отклоняется, пока в диалоге идёт ход: он может заменить карточку.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun save(dialog: Uuid, client: ClientId, currentMessage: Uuid? = null): AssistantBlock.ClientCreated {
        storage.lockDialog(dialog)
        if (currentMessage != null) {
            storage.requireProcessing(dialog, currentMessage)
        }
        val draft =
            tr.sql("SELECT status,payload::text,message_id FROM assistant_client_drafts WHERE client_id = :client AND dialog_id = :dialog AND org_id = :org FOR UPDATE")
                .bind("client", client).bind("dialog", dialog).bind("org", ctx.orgId).firstOrNull { row ->
                    Triple(row.asString("status"), row.asString("payload"), row.asUuid("message_id"))
                } ?: raise(assistantError("DIALOG_UNAVAILABLE"))
        if (draft.first == "executed") {
            return AssistantBlock.ClientCreated(client)
        }
        if (draft.first != "pending") {
            raise(assistantError("DRAFT_SUPERSEDED"))
        }
        storage.requireConfirmable(dialog, draft.third, currentMessage)
        val payload = Json.decodeFromString<AssistantClientPayload>(draft.second)
        val checkedContacts = validateContacts(payload.contacts, client)
        val created = clients.new(client, NonBlankName.from(payload.name), null, payload.birthday, payload.gender, customFields = payload.customFields)
        contacts.replace(created, checkedContacts.map { ClientContact(ClientContactId.new(), client, it.type, it.value, it.label, it.isPrimary) })
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
}

/** Правило допустимого значения контакта, общее для карточки клиента и импорта. */
internal fun isValidContact(type: ContactType, raw: String): Boolean {
    val value = raw.trim()
    val valid =
        when (type) {
            ContactType.PHONE -> value.all { it.isDigit() || it in "+()- ." } && PhoneDigits.from(value)?.value?.length in 7..15
            ContactType.EMAIL -> value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            else -> value.isNotBlank()
        }
    return valid && value.length <= 512
}

/**
 * Проверяет контакты клиента [client] до карточки и повторно перед сохранением и приводит их
 * к хранимому виду ([normalized]): карточка показывает ровно то, что будет записано.
 */
context(raise: Raise<AppError>)
internal fun validateContacts(values: List<ClientContactInput>, client: ClientId): List<ClientContactInput> =
    values.map { contact ->
        if (!isValidContact(contact.type, contact.value)) {
            raise(assistantError("VALIDATION"))
        }
        ClientContact(ClientContactId.new(), client, contact.type, contact.value.trim(), contact.label, contact.isPrimary)
    }.normalized().map { ClientContactInput(it.type, it.value, it.label, it.isPrimary) }
