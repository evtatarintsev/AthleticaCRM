package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** R2DBC-реализация дисциплины. Все запросы фильтруются по `org_id`. */
data class DbDiscipline(
    override val id: DisciplineId,
    override val name: String,
) : Discipline {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        try {
            tr.sql(
                """
                INSERT INTO disciplines (id, org_id, name)
                VALUES (:id, :orgId, :name)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
                WHERE disciplines.org_id = :orgId
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("DISCIPLINE_ALREADY_EXISTS", Messages.DisciplineAlreadyExists.localize()))
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() {
        tr.sql("DELETE FROM disciplines WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    override fun withNew(name: String): Discipline = copy(name = name)
}
