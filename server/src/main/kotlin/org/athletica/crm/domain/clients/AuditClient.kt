package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import kotlin.collections.plus

data class AuditClient(
    private val client: ActiveClient,
    private val audit: AuditLog,
    private val auditEvents: List<AuditEvent> = emptyList(),
) : ActiveClient {
    override val id = client.id
    override val name = client.name
    override val avatarId = client.avatarId
    override val birthday = client.birthday
    override val gender = client.gender
    override val groups = client.groups
    override val docs = client.docs
    override val leadSourceId = client.leadSourceId
    override val customFields = client.customFields

    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        client.save()
        auditEvents.forEach {
            audit.log(it)
        }
    }

    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun archive() = client.archive()

    context(ctx: EmployeeRequestContext)
    override fun attachDoc(doc: ClientDoc) =
        AuditClient(
            client.attachDoc(doc),
            audit,
            auditEvents + AuditEvent(ctx, AuditActionType.CREATE, "client_doc", doc.id.value, doc.id.toString()),
        )

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun deleteDoc(docId: ClientDocId): ActiveClient {
        val docToDelete = client.docs.firstOrNull { it.id == docId }
        if (docToDelete == null) {
            raise(CommonDomainError("DOC_NOT_FOUND", Messages.UploadNotFound.localize()))
        }
        val auditEvent =
            AuditEvent(
                ctx,
                AuditActionType.DELETE,
                "client_doc",
                docToDelete.id.value,
                docToDelete.id.toString(),
            )
        return AuditClient(client.deleteDoc(docId), audit, auditEvents + auditEvent)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun withNew(
        newName: String,
        newAvatarId: UploadId?,
        newBirthday: LocalDate?,
        newGender: Gender,
        newLeadSourceId: LeadSourceId?,
        newCustomFields: List<CustomFieldValue>,
    ): ActiveClient {
        val newValues =
            ClientAuditData(
                name = newName,
                avatarId = newAvatarId,
                birthday = newBirthday,
                gender = newGender,
                leadSourceId = newLeadSourceId,
            )
        val auditEvent =
            AuditEvent(
                ctx,
                AuditActionType.UPDATE,
                "client",
                id.value,
                Json.encodeToString(newValues),
            )
        return AuditClient(client.withNew(newName, newAvatarId, newBirthday, newGender, newLeadSourceId, newCustomFields), audit, auditEvents + auditEvent)
    }
}

/** Снимок изменяемых полей клиента для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class ClientAuditData(
    val name: String,
    val avatarId: UploadId?,
    val birthday: LocalDate?,
    val gender: Gender,
    val leadSourceId: LeadSourceId?,
)
