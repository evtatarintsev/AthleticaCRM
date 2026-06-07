package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.toDisciplineId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** R2DBC-реализация каталога дисциплин. */
class DbDisciplines : Disciplines {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Discipline> =
        tr.sql("SELECT s.id, s.name FROM disciplines s WHERE s.org_id = :orgId ORDER BY s.name")
            .bind("orgId", ctx.orgId)
            .list {
                DbDiscipline(id = it.asUuid("id").toDisciplineId(), name = it.asString("name"))
            }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: DisciplineId, name: String): Discipline = DbDiscipline(id, name)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: DisciplineId): Discipline =
        byIds(listOf(id)).singleOrNull()
            ?: raise(CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<DisciplineId>): List<Discipline> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) {
            return emptyList()
        }

        val result =
            tr.sql("SELECT s.id, s.name FROM disciplines s WHERE s.id = ANY(:ids) AND s.org_id = :orgId")
                .bind("ids", distinctIds)
                .bind("orgId", ctx.orgId)
                .list {
                    DbDiscipline(id = it.asUuid("id").toDisciplineId(), name = it.asString("name"))
                }

        if (result.size != distinctIds.size) {
            raise(CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()))
        }
        return result
    }
}
