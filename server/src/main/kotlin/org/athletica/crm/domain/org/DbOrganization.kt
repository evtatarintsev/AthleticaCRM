package org.athletica.crm.domain.org

import arrow.core.raise.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

class DbOrganization(
    override val id: OrgId,
    override val name: String,
    override val timezone: String,
) : Organization {
    context(ctx: RequestContext, raise: Raise<DomainError>)
    override suspend fun withNew(newName: String, newTimezone: String) = DbOrganization(id, newName, newTimezone)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr
            .sql("UPDATE organizations SET name = :name, timezone = :timezone WHERE id = :id")
            .bind("id", id)
            .bind("name", name)
            .bind("timezone", timezone)
            .execute()
    }
}
