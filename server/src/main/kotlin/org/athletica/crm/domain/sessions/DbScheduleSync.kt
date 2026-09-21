package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction

/** Горизонт материализации занятий в месяцах от сегодняшнего дня. */
private const val HORIZON_MONTHS = 3

/**
 * Реализация [ScheduleSync] четырьмя множественными операциями в SQL.
 * Цикла по группам и датам нет: `schedule_slots` содержит период действия каждой версии,
 * поэтому «какие занятия должны существовать» полностью выразимо запросом.
 */
class DbScheduleSync : ScheduleSync {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun sync() {
        val today = java.time.LocalDate.now().toKotlinLocalDate()
        val horizon = today.plus(HORIZON_MONTHS, DateTimeUnit.MONTH)
        createMissing(today, horizon)
        updateAttributes(today)
        deleteOutOfValidity(today)
        syncEmployees(today)
    }

    /** Создаёт занятия на все подходящие даты периодов слотов внутри горизонта. */
    context(ctx: RequestContext, tr: Transaction)
    private suspend fun createMissing(today: LocalDate, horizon: LocalDate) {
        tr
            .sql(
                """
                INSERT INTO sessions (id, org_id, group_id, date, start_time, end_time, hall_id,
                                      origin_slot_id, origin_date)
                SELECT uuidv7(), ss.org_id, ss.group_id, d::date, ss.start_time, ss.end_time, ss.hall_id,
                       ss.id, d::date
                  FROM schedule_slots ss
                  CROSS JOIN LATERAL generate_series(
                        GREATEST(:today::date, lower(ss.validity)),
                        LEAST(:horizon::date, COALESCE(upper(ss.validity) - 1, :horizon::date)),
                        interval '1 day') AS d
                 WHERE ss.org_id = :orgId
                   AND isodow_of(ss.day_of_week) = extract(isodow FROM d)
                    ON CONFLICT (origin_slot_id, origin_date) DO NOTHING
                """.trimIndent(),
            )
            .bindWindow(today, horizon)
            .execute()
    }

    /** Подтягивает время и зал у нетронутых будущих занятий из их версии слота. */
    context(ctx: RequestContext, tr: Transaction)
    private suspend fun updateAttributes(today: LocalDate) {
        tr
            .sql(
                """
                UPDATE sessions s
                   SET start_time = ss.start_time, end_time = ss.end_time, hall_id = ss.hall_id
                  FROM schedule_slots ss
                 WHERE s.origin_slot_id = ss.id AND s.org_id = :orgId AND s.date >= :today::date
                   AND ${UntouchedSessions.predicate("s")}
                   AND (s.start_time, s.end_time, s.hall_id)
                       IS DISTINCT FROM (ss.start_time, ss.end_time, ss.hall_id)
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("today", today)
            .execute()
    }

    /**
     * Удаляет нетронутые будущие занятия, дата которых вышла за период своего слота.
     * Занятие исчезает, а не отменяется: отмена остаётся высказыванием человека.
     */
    context(ctx: RequestContext, tr: Transaction)
    private suspend fun deleteOutOfValidity(today: LocalDate) {
        tr
            .sql(
                """
                DELETE FROM sessions s
                 USING schedule_slots ss
                 WHERE s.origin_slot_id = ss.id AND s.org_id = :orgId AND s.date >= :today::date
                   AND ${UntouchedSessions.predicate("s")}
                   AND NOT (ss.validity @> s.origin_date)
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("today", today)
            .execute()
    }

    /** Приводит состав тренеров нетронутых будущих занятий к составу их группы. */
    context(ctx: RequestContext, tr: Transaction)
    private suspend fun syncEmployees(today: LocalDate) {
        tr
            .sql(
                """
                DELETE FROM session_employees se
                 USING sessions s
                 WHERE se.session_id = s.id AND s.org_id = :orgId AND s.date >= :today::date
                   AND ${UntouchedSessions.predicate("s")}
                   AND NOT EXISTS (SELECT 1 FROM group_employees ge
                                    WHERE ge.group_id = s.group_id AND ge.employee_id = se.employee_id)
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("today", today)
            .execute()
        tr
            .sql(
                """
                INSERT INTO session_employees (session_id, employee_id)
                SELECT s.id, ge.employee_id
                  FROM sessions s
                  JOIN group_employees ge ON ge.group_id = s.group_id
                 WHERE s.org_id = :orgId AND s.date >= :today::date
                   AND ${UntouchedSessions.predicate("s")}
                    ON CONFLICT DO NOTHING
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("today", today)
            .execute()
    }

    /** Привязывает организацию и границы окна материализации. */
    context(ctx: RequestContext)
    private fun QueryBuilder.bindWindow(today: LocalDate, horizon: LocalDate): QueryBuilder =
        bind("orgId", ctx.orgId)
            .bind("today", today)
            .bind("horizon", horizon)
}
