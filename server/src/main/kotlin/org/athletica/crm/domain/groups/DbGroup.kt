package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** Конкретная реализация [Group] на основе данных из PostgreSQL. */
class DbGroup(
    override val id: GroupId,
    override val branchId: BranchId,
    override val name: String,
    override val schedule: List<ScheduleSlot>,
    override val disciplines: List<DisciplineId>,
) : Group {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        val updatedRows =
            try {
                tr
                    .sql("UPDATE groups SET name = :name WHERE id = :id AND org_id = :orgId")
                    .bind("id", id)
                    .bind("orgId", ctx.orgId)
                    .bind("name", name)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("GROUP_NAME_ALREADY_EXISTS", Messages.GroupNameAlreadyExists.localize()))
            }

        if (updatedRows == 0L) {
            raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
        }

        tr
            .sql("DELETE FROM schedule_slots WHERE group_id = :groupId AND org_id = :orgId")
            .bind("groupId", id)
            .bind("orgId", ctx.orgId)
            .execute()

        schedule.forEach { slot ->
            val hallExists =
                tr
                    .sql("SELECT 1 FROM halls WHERE id = :hallId AND org_id = :orgId AND branch_id = :branchId")
                    .bind("hallId", slot.hallId)
                    .bind("orgId", ctx.orgId)
                    .bind("branchId", branchId)
                    .firstOrNull { 1 } != null
            if (!hallExists) {
                raise(CommonDomainError("HALL_NOT_FOUND", Messages.HallNotFound.localize()))
            }
            tr
                .sql(
                    """
                    INSERT INTO schedule_slots (org_id, group_id, day_of_week, start_time, end_time, hall_id)
                    VALUES (:orgId, :groupId, :dayOfWeek::day_of_week, :startAt::time, :endAt::time, :hallId)
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("groupId", id)
                .bind("dayOfWeek", slot.dayOfWeek.name)
                .bind("startAt", slot.startAt.toString())
                .bind("endAt", slot.endAt.toString())
                .bind("hallId", slot.hallId)
                .execute()
        }

        tr
            .sql("DELETE FROM group_disciplines WHERE group_id = :groupId")
            .bind("groupId", id)
            .execute()

        disciplines.forEach { disciplineId ->
            try {
                tr
                    .sql(
                        "INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:groupId, :disciplineId)",
                    )
                    .bind("groupId", id)
                    .bind("disciplineId", disciplineId)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()))
            }
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun withNewSchedule(schedule: List<ScheduleSlot>): Group = DbGroup(id, branchId, name, schedule, disciplines)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun withNewDisciplines(disciplines: List<DisciplineId>): Group = DbGroup(id, branchId, name, schedule, disciplines)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun withNewName(name: String): Group = DbGroup(id, branchId, name, schedule, disciplines)
}
