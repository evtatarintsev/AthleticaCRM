package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.core.Gender
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientDocId
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
    private val client: Client,
    private val audit: AuditLog,
    private val auditEvents: List<AuditEvent> = emptyList(),
) : Client {
    override val id = client.id
    override val name = client.name
    override val avatarId = client.avatarId
    override val birthday = client.birthday
    override val gender = client.gender
    override val groups = client.groups
    override val balance = client.balance
    override val docs = client.docs

    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        client.save()
        auditEvents.forEach {
            audit.log(it)
        }
    }

    context(ctx: RequestContext)
    override fun attachDoc(doc: ClientDoc) =
        AuditClient(
            client.attachDoc(doc),
            audit,
            auditEvents + AuditEvent(ctx, AuditActionType.CREATE, "client_doc", doc.id.value, doc.id.toString()),
        )

    context(ctx: RequestContext, raise: Raise<DomainError>)
    override fun deleteDoc(docId: ClientDocId): Client {
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

    context(ctx: RequestContext, raise: Raise<DomainError>)
    override fun withNew(
        newName: String,
        newAvatarId: UploadId?,
        newBirthday: LocalDate?,
        newGender: Gender,
    ): Client {
        val newValues =
            mapOf(
                "name" to newName,
                "avatarId" to newAvatarId,
                "birthday" to newBirthday,
                "gender" to newGender,
            )
        val auditEvent =
            AuditEvent(
                ctx,
                AuditActionType.UPDATE,
                "client",
                id.value,
                Json.encodeToString(newValues),
            )
        return AuditClient(client.withNew(newName, newAvatarId, newBirthday, newGender), audit, auditEvents + auditEvent)
    }
}
