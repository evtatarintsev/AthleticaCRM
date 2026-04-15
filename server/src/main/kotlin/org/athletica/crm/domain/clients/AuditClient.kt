package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Transaction
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
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
    override fun attachDoc(doc: ClientDoc): Client =
        AuditClient(
            client.attachDoc(doc),
            audit,
            auditEvents + AuditEvent(ctx, AuditActionType.CREATE, "client_doc", doc.id, doc.id.toString()),
        )
}
