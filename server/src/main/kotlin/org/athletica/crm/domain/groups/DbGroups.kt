package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toDisciplineId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.events.DomainEvents
import org.athletica.crm.domain.events.GroupCreated
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [Groups] с доступом к PostgreSQL через R2DBC. */
class DbGroups(private val events: DomainEvents) : Groups {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: GroupId,
        name: String,
        schedule: List<ScheduleSlot>,
        disciplineIds: List<DisciplineId>,
    ): Group {
        try {
            tr
                .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .bind("name", name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            if (e.message?.contains("uq_groups_org_name") == true) {
                raise(CommonDomainError("GROUP_NAME_ALREADY_EXISTS", Messages.GroupNameAlreadyExists.localize()))
            } else {
                raise(CommonDomainError("GROUP_ALREADY_EXISTS", Messages.GroupAlreadyExists.localize()))
            }
        }

        schedule.forEach { slot ->
            tr
                .sql(
                    """
                    INSERT INTO schedule_slots (org_id, group_id, day_of_week, start_time, end_time)
                    VALUES (:orgId, :groupId, :dayOfWeek::day_of_week, :startAt::time, :endAt::time)
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("groupId", id)
                .bind("dayOfWeek", slot.dayOfWeek.name)
                .bind("startAt", slot.startAt.toString())
                .bind("endAt", slot.endAt.toString())
                .execute()
        }

        disciplineIds.forEach { disciplineId ->
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
        events.publish(GroupCreated(id))
        return DbGroup(id, ctx.branchId, name, schedule, disciplineIds)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Group> {
        val groups =
            tr
                .sql(
                    """
                    SELECT id, branch_id, name FROM groups
                    WHERE org_id = :orgId AND branch_id = :branchId
                    ORDER BY name
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .list { row ->
                    Triple(
                        row.asUuid("id").toGroupId(),
                        row.asUuid("branch_id").toBranchId(),
                        row.asString("name"),
                    )
                }

        if (groups.isEmpty()) {
            return emptyList()
        }

        val groupIds = groups.map { it.first.value }

        val slotsByGroup =
            tr
                .sql(
                    """
                    SELECT group_id, day_of_week, start_time, end_time
                    FROM schedule_slots
                    WHERE group_id = ANY(:ids)
                    ORDER BY day_of_week, start_time
                    """.trimIndent(),
                )
                .bind("ids", groupIds)
                .list { row ->
                    row.asUuid("group_id").toGroupId() to
                        ScheduleSlot(
                            dayOfWeek = DayOfWeek.valueOf(row.asString("day_of_week")),
                            startAt = row.asLocalTime("start_time"),
                            endAt = row.asLocalTime("end_time"),
                        )
                }
                .groupBy({ it.first }, { it.second })

        val disciplinesByGroup =
            tr
                .sql(
                    """
                    SELECT group_id, discipline_id
                    FROM group_disciplines
                    WHERE group_id = ANY(:ids)
                    """.trimIndent(),
                )
                .bind("ids", groupIds)
                .list { row ->
                    row.asUuid("group_id").toGroupId() to row.asUuid("discipline_id").toDisciplineId()
                }
                .groupBy({ it.first }, { it.second })

        return groups.map { (id, branchId, name) ->
            DbGroup(
                id = id,
                branchId = branchId,
                name = name,
                schedule = slotsByGroup[id] ?: emptyList(),
                disciplines = disciplinesByGroup[id] ?: emptyList(),
            )
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: GroupId): Group {
        val (branchId, name) =
            tr
                .sql("SELECT branch_id, name FROM groups WHERE id = :id AND org_id = :orgId")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.asUuid("branch_id").toBranchId() to row.asString("name") }
                ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))

        val schedule =
            tr
                .sql(
                    """
                    SELECT day_of_week, start_time, end_time
                    FROM schedule_slots
                    WHERE group_id = :groupId
                    ORDER BY day_of_week, start_time
                    """.trimIndent(),
                )
                .bind("groupId", id)
                .list { row ->
                    ScheduleSlot(
                        dayOfWeek = DayOfWeek.valueOf(row.asString("day_of_week")),
                        startAt = row.asLocalTime("start_time"),
                        endAt = row.asLocalTime("end_time"),
                    )
                }

        val disciplines =
            tr
                .sql("SELECT discipline_id FROM group_disciplines WHERE group_id = :groupId")
                .bind("groupId", id)
                .list { row -> row.asUuid("discipline_id").toDisciplineId() }

        return DbGroup(id, branchId, name, schedule, disciplines)
    }
}
