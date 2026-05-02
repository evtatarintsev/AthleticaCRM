package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/** Реализация [Sessions] с доступом к PostgreSQL через R2DBC. */
class DbSessions : Sessions {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: SessionId,
        groupId: GroupId,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        hallId: HallId,
        notes: String?,
        originDayOfWeek: String?,
        originStartTime: LocalTime?,
        originDate: LocalDate?,
    ): Session? =
        try {
            tr
                .sql(
                    """
                    INSERT INTO sessions (
                        id, org_id, group_id, date, start_time, end_time, hall_id, notes,
                        is_manual, origin_day_of_week, origin_start_time, origin_date
                    ) VALUES (
                        :id, :orgId, :groupId, :date, :startTime::time, :endTime::time, :hallId, :notes,
                        :isManual, :originDayOfWeek, :originStartTime::time, :originDate
                    )
                    ON CONFLICT (group_id, origin_day_of_week, origin_start_time, origin_date)
                    WHERE origin_day_of_week IS NOT NULL
                    DO NOTHING
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("groupId", groupId)
                .bind("date", date.toJavaLocalDate())
                .bind("startTime", startTime.toString())
                .bind("endTime", endTime.toString())
                .bind("hallId", hallId)
                .bind("notes", notes)
                .bind("isManual", originDayOfWeek == null)
                .bind("originDayOfWeek", originDayOfWeek)
                .bind("originStartTime", originStartTime?.toString())
                .bind("originDate", originDate?.toJavaLocalDate())
                .execute()

            byIdOrNull(id)
        } catch (e: R2dbcDataIntegrityViolationException) {
            null
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(
        groupId: GroupId,
        from: LocalDate,
        to: LocalDate,
    ): List<Session> =
        tr
            .sql(
                """
                SELECT s.id, s.group_id, s.date, s.start_time, s.end_time, s.hall_id,
                       s.status, s.is_manual, s.is_rescheduled, s.origin_day_of_week,
                       s.origin_start_time, s.origin_date, s.notes
                FROM sessions s
                WHERE s.org_id = :orgId AND s.group_id = :groupId
                  AND s.date >= :from AND s.date <= :to
                ORDER BY s.date, s.start_time
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("groupId", groupId)
            .bind("from", from.toJavaLocalDate())
            .bind("to", to.toJavaLocalDate())
            .list { row -> row.toSession() }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun listAll(
        from: LocalDate,
        to: LocalDate,
    ): List<Session> =
        tr
            .sql(
                """
                SELECT s.id, s.group_id, s.date, s.start_time, s.end_time, s.hall_id,
                       s.status, s.is_manual, s.is_rescheduled, s.origin_day_of_week,
                       s.origin_start_time, s.origin_date, s.notes
                FROM sessions s
                WHERE s.org_id = :orgId
                  AND s.date >= :from AND s.date <= :to
                ORDER BY s.date, s.start_time
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("from", from.toJavaLocalDate())
            .bind("to", to.toJavaLocalDate())
            .list { row -> row.toSession() }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: SessionId): Session =
        byIdOrNull(id)
            ?: raise(CommonDomainError("SESSION_NOT_FOUND", "Занятие не найдено"))

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun futureScheduledBySlot(
        groupId: GroupId,
        dayOfWeek: String,
        startTime: LocalTime,
        from: LocalDate,
    ): List<Session> =
        tr
            .sql(
                """
                SELECT s.id, s.group_id, s.date, s.start_time, s.end_time, s.hall_id,
                       s.status, s.is_manual, s.is_rescheduled, s.origin_day_of_week,
                       s.origin_start_time, s.origin_date, s.notes
                FROM sessions s
                WHERE s.org_id = :orgId AND s.group_id = :groupId
                  AND s.origin_day_of_week = :dayOfWeek
                  AND s.origin_start_time = :startTime::time
                  AND s.status = 'scheduled'
                  AND s.date >= :from
                  AND s.is_rescheduled = false
                ORDER BY s.date
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("groupId", groupId)
            .bind("dayOfWeek", dayOfWeek)
            .bind("startTime", startTime.toString())
            .bind("from", from.toJavaLocalDate())
            .list { row -> row.toSession() }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun byIdOrNull(id: SessionId): Session? =
        tr
            .sql(
                """
                SELECT s.id, s.group_id, s.date, s.start_time, s.end_time, s.hall_id,
                       s.status, s.is_manual, s.is_rescheduled, s.origin_day_of_week,
                       s.origin_start_time, s.origin_date, s.notes
                FROM sessions s
                WHERE s.id = :id AND s.org_id = :orgId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toSession() }
}

private fun io.r2dbc.spi.Row.toSession(): DbSession =
    DbSession(
        id = asUuid("id").toSessionId(),
        groupId = asUuid("group_id").toGroupId(),
        date = asLocalDate("date"),
        startTime = asLocalTime("start_time"),
        endTime = asLocalTime("end_time"),
        hallId = asUuid("hall_id").toHallId(),
        status = asString("status"),
        isManual = asBoolean("is_manual"),
        isRescheduled = asBoolean("is_rescheduled"),
        originDayOfWeek = asStringOrNull("origin_day_of_week"),
        originStartTime =
            get("origin_start_time", java.time.LocalTime::class.java)?.let {
                kotlinx.datetime.LocalTime(it.hour, it.minute, it.second)
            },
        originDate = asLocalDateOrNull("origin_date"),
        notes = asStringOrNull("notes"),
    )
