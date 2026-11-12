package org.athletica.crm.domain.attendancecontrol

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import kotlin.time.Duration
import kotlin.time.Instant

internal const val JOURNAL_DEADLINE_SQL =
    "((s.date + s.end_time) AT TIME ZONE o.timezone) + make_interval(mins => COALESCE(acs.overdue_task_delay_minutes, :defaultDelay))"
internal const val JOURNAL_ENABLED_SQL = "COALESCE(acs.overdue_task_enabled, :defaultEnabled)"
internal const val JOURNAL_DUE_SQL =
    "deadline <= CAST(:now AS timestamptz) AND deadline > CAST(:now AS timestamptz) - make_interval(secs => :lookbackSeconds)"

internal fun QueryBuilder.bindJournalWindow(now: Instant, lookback: Duration) =
    bind("defaultDelay", JournalCloseDelay.DEFAULT.minutes)
        .bind("defaultEnabled", AttendanceControl.DEFAULT_OVERDUE_TASK_ENABLED)
        .bind("now", now)
        .bind("lookbackSeconds", lookback.inWholeSeconds.toDouble())

internal data class OverdueJournalTask(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val groupName: String,
)

/** Блокирует занятие, проверяет актуальный срок и резервирует единственную задачу. */
context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
internal suspend fun reserveOverdueJournalTask(
    sessionId: SessionId,
    taskId: TaskId,
    now: Instant,
    lookback: Duration,
): OverdueJournalTask? =
    tr.sql(
        """
        WITH locked AS MATERIALIZED (
            SELECT id, org_id, group_id, date, start_time, end_time
            FROM sessions
            WHERE id = :sessionId AND org_id = :orgId AND status = 'scheduled'
            FOR UPDATE
        ), candidate AS (
            SELECT s.*, g.name AS group_name, $JOURNAL_DEADLINE_SQL AS deadline
            FROM locked s
            JOIN organizations o ON o.id = s.org_id
            JOIN groups g ON g.id = s.group_id
            LEFT JOIN attendance_control_settings acs ON acs.org_id = s.org_id
            WHERE $JOURNAL_ENABLED_SQL
        ), reserved AS (
            INSERT INTO session_overdue_tasks (session_id, org_id, task_id)
            SELECT id, org_id, :taskId FROM candidate WHERE $JOURNAL_DUE_SQL
            ON CONFLICT (session_id) DO NOTHING
            RETURNING session_id
        )
        SELECT c.date, c.start_time, c.end_time, c.group_name
        FROM candidate c JOIN reserved r ON r.session_id = c.id
        """.trimIndent(),
    )
        .bind("sessionId", sessionId)
        .bind("orgId", ctx.orgId)
        .bind("taskId", taskId)
        .bindJournalWindow(now, lookback)
        .firstOrNull { row ->
            OverdueJournalTask(
                row.asLocalDate("date"),
                row.asLocalTime("start_time"),
                row.asLocalTime("end_time"),
                row.asString("group_name"),
            )
        }
