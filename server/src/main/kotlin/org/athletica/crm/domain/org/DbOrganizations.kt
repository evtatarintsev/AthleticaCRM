package org.athletica.crm.domain.org

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

class DbOrganizations : Organizations {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun current(): Organization =
        tr
            .sql("SELECT id, name, timezone FROM organizations WHERE id = :orgId")
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                DbOrganization(
                    id = row.asUuid("id").toOrgId(),
                    name = row.asString("name"),
                    timezone = row.asString("timezone"),
                )
            } ?: raise(CommonDomainError("ORG_NOT_FOUND", Messages.OrgNotFound.localize()))
}
