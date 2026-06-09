package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Transaction

/** Декоратор [Clients], оборачивающий выдаваемых клиентов в [AuditActiveClient]. */
class AuditClients(private val delegate: Clients, private val audit: AuditLog) : Clients by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: ClientId) =
        when (val client = delegate.byId(id)) {
            is ActiveClient -> AuditActiveClient(client, audit)
            is ArchivedClient -> AuditArchivedClient(client, audit)
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: ClientId,
        name: String,
        avatarId: UploadId?,
        birthday: LocalDate?,
        gender: Gender,
        leadSourceId: LeadSourceId?,
        customFields: List<CustomFieldValue>,
    ) = AuditActiveClient(delegate.new(id, name, avatarId, birthday, gender, leadSourceId, customFields), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(archived: Boolean) =
        delegate.list(archived).map {
            when (it) {
                is ActiveClient -> AuditActiveClient(it, audit)
                is ArchivedClient -> AuditArchivedClient(it, audit)
            }
        }
}
