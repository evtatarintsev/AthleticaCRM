package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import jdk.internal.misc.Signal.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Transaction
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.i18n.Messages
import kotlin.collections.plus
import kotlin.uuid.Uuid

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
            auditEvents + AuditEvent(ctx, AuditActionType.CREATE, "client_doc", doc.id, doc.id.toString()),
        )

    context(ctx: RequestContext, raise: Raise<DomainError>)
    override fun deleteDoc(docId: Uuid): Client {
        val docToDelete = client.docs.firstOrNull { it.id == docId }
        if (docToDelete == null) {
            raise(CommonDomainError("DOC_NOT_FOUND", Messages.UploadNotFound.localize()))
        }
        val auditEvent =
            AuditEvent(
                ctx,
                AuditActionType.DELETE,
                "client_doc",
                docToDelete.id,
                docToDelete.id.toString(),
            )
        return AuditClient(client.deleteDoc(docId), audit, auditEvents + auditEvent)
    }
}
