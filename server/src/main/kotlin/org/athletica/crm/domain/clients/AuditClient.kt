package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import kotlin.collections.plus

data class AuditActiveClient(
    private val client: ActiveClient,
    private val audit: AuditLog,
    private val auditEvents: List<AuditEvent> = emptyList(),
) : ActiveClient by client {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() {
        client.save()
        auditEvents.forEach {
            audit.log(it)
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun archive() {
        client.archive()
        audit.logUpdate("client", id, """{"state":"ARCHIVED"}""")
    }

    context(ctx: EmployeeRequestContext)
    override fun attachDoc(doc: ClientDoc): ActiveClient {
        val auditEvent =
            AuditEvent(
                ctx,
                AuditActionType.CREATE,
                "client_doc",
                doc.id.value,
                Json.encodeToString(doc.auditData()),
            )
        return AuditActiveClient(client.attachDoc(doc), audit, auditEvents + auditEvent)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
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
                Json.encodeToString(docToDelete.auditData()),
            )
        return AuditActiveClient(client.deleteDoc(docId), audit, auditEvents + auditEvent)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun withNew(
        newName: NonBlankName,
        newAvatarId: UploadId?,
        newBirthday: LocalDate?,
        newGender: Gender,
        newLeadSourceId: LeadSourceId?,
        newCustomFields: List<CustomFieldValue>,
    ): ActiveClient {
        val newValues =
            ClientAuditData(
                name = newName.value,
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
        return AuditActiveClient(
            client.withNew(
                newName,
                newAvatarId,
                newBirthday,
                newGender,
                newLeadSourceId,
                newCustomFields,
            ),
            audit, auditEvents + auditEvent,
        )
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

/** Снимок документа клиента для журнала аудита: поле `data` журнала хранится как JSONB. */
@Serializable
private data class ClientDocAuditData(
    /** Идентификатор загруженного файла документа. */
    val uploadId: UploadId,
    /** Отображаемое название документа. */
    val name: String,
)

/** Снимок документа для журнала аудита. */
private fun ClientDoc.auditData() = ClientDocAuditData(uploadId, name)

data class AuditArchivedClient(
    private val client: ArchivedClient,
    private val audit: AuditLog,
) : ArchivedClient by client {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun restore() {
        client.restore()
        audit.logUpdate("client", id, """{"state":"ACTIVE"}""")
    }
}
