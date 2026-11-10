package org.athletica.crm.read.sessions

import arrow.core.raise.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.time.Duration
import kotlin.time.Instant

/** Занятия всех организаций, срок задачи по которым наступил за последние [lookback]. */
context(tr: Transaction, raise: Raise<AppError>)
internal suspend fun dueJournalSessions(
    now: Instant,
    fromDate: LocalDate,
    lookback: Duration,
    defaultDelayMinutes: Int,
    defaultEnabled: Boolean,
): List<DueSession> =
    tr
        .sql(
            """
            WITH candidates AS (
                SELECT s.id, s.org_id, s.date, s.start_time, s.end_time, g.name AS group_name,
                       ((s.date + s.end_time) AT TIME ZONE o.timezone)
                           + make_interval(mins => COALESCE(acs.overdue_task_delay_minutes, :defaultDelay)) AS deadline
                FROM sessions s
                JOIN organizations o ON o.id = s.org_id
                LEFT JOIN groups g ON g.id = s.group_id
                LEFT JOIN attendance_control_settings acs ON acs.org_id = s.org_id
                WHERE s.status = 'scheduled'
                  AND s.date >= :fromDate
                  AND COALESCE(acs.overdue_task_enabled, :defaultEnabled)
                  AND NOT EXISTS (SELECT 1 FROM session_overdue_tasks t WHERE t.session_id = s.id)
            )
            SELECT id, org_id, date, start_time, end_time, group_name
            FROM candidates
            WHERE deadline <= CAST(:now AS timestamptz)
              AND deadline > CAST(:now AS timestamptz) - make_interval(secs => :lookbackSeconds)
            ORDER BY org_id, deadline, id
            """.trimIndent(),
        )
        .bind("defaultDelay", defaultDelayMinutes)
        .bind("defaultEnabled", defaultEnabled)
        .bind("fromDate", fromDate)
        .bind("now", now)
        .bind("lookbackSeconds", lookback.inWholeSeconds.toDouble())
        .list { row ->
            DueSession(
                id = row.asUuid("id").toSessionId(),
                orgId = row.asUuid("org_id").toOrgId(),
                date = row.asLocalDate("date"),
                startTime = row.asLocalTime("start_time"),
                endTime = row.asLocalTime("end_time"),
                groupName = row.asStringOrNull("group_name") ?: "",
            )
        }

/** Занятие, по которому пора создать задачу, с данными для её текста. */
internal data class DueSession(
    /** Идентификатор занятия. */
    val id: SessionId,
    /** Организация занятия. */
    val orgId: OrgId,
    /** Дата занятия. */
    val date: LocalDate,
    /** Время начала. */
    val startTime: LocalTime,
    /** Время окончания. */
    val endTime: LocalTime,
    /** Название группы. */
    val groupName: String,
)
