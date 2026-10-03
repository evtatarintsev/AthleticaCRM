package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.toLeadSourceId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** R2DBC-реализация каталога источников привлечения. */
class DbLeadSources : LeadSources {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<LeadSource> =
        tr.sql("SELECT ls.id, ls.name, ls.description FROM lead_sources ls WHERE ls.org_id = :orgId ORDER BY ls.name")
            .bind("orgId", ctx.orgId)
            .list {
                it.toLeadSource()
            }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: LeadSourceId, name: String, description: LeadSourceDescription): LeadSource = DbLeadSource(id, name, description)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: LeadSourceId): LeadSource =
        byIds(listOf(id)).singleOrNull()
            ?: raise(CommonDomainError("LEAD_SOURCE_NOT_FOUND", Messages.LeadSourceNotFound.localize()))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<LeadSourceId>): List<LeadSource> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) {
            return emptyList()
        }

        val result =
            tr.sql("SELECT ls.id, ls.name, ls.description FROM lead_sources ls WHERE ls.id = ANY(:ids) AND ls.org_id = :orgId")
                .bind("ids", distinctIds)
                .bind("orgId", ctx.orgId)
                .list {
                    it.toLeadSource()
                }

        if (result.size != distinctIds.size) {
            raise(CommonDomainError("LEAD_SOURCE_NOT_FOUND", Messages.LeadSourceNotFound.localize()))
        }
        return result
    }
}

/** Источник из строки выборки с колонками `id`, `name`, `description`; пояснение в БД уже нормализовано. */
private fun Row.toLeadSource(): LeadSource =
    DbLeadSource(
        id = asUuid("id").toLeadSourceId(),
        name = asString("name"),
        description = LeadSourceDescription.fromDb(asString("description")),
    )
