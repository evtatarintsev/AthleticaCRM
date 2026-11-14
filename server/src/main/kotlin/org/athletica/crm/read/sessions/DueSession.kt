package org.athletica.crm.read.sessions

import arrow.core.raise.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.attendancecontrol.JOURNAL_DEADLINE_SQL
import org.athletica.crm.domain.attendancecontrol.JOURNAL_DUE_SQL
import org.athletica.crm.domain.attendancecontrol.JOURNAL_ENABLED_SQL
import org.athletica.crm.domain.attendancecontrol.bindJournalWindow
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asUuid
import kotlin.time.Duration
import kotlin.time.Instant

/** Занятия всех организаций, срок задачи по которым наступил за последние [lookback]. */
context(tr: Transaction, raise: Raise<AppError>)
internal suspend fun dueJournalSessions(
    now: Instant,
    fromDate: LocalDate,
    lookback: Duration,
): List<DueSession> =
    tr
        .sql(
            """
            WITH candidates AS (
                SELECT s.id, s.org_id, $JOURNAL_DEADLINE_SQL AS deadline
                FROM sessions s
                JOIN organizations o ON o.id = s.org_id
                LEFT JOIN attendance_control_settings acs ON acs.org_id = s.org_id
                WHERE s.status = 'scheduled'
                  AND s.date >= :fromDate
                  AND $JOURNAL_ENABLED_SQL
                  AND NOT EXISTS (SELECT 1 FROM session_overdue_tasks t WHERE t.session_id = s.id)
            )
            SELECT id, org_id
            FROM candidates
            WHERE $JOURNAL_DUE_SQL
            ORDER BY org_id, deadline, id
            """.trimIndent(),
        )
        .bind("fromDate", fromDate)
        .bindJournalWindow(now, lookback)
        .list { row ->
            DueSession(
                id = row.asUuid("id").toSessionId(),
                orgId = row.asUuid("org_id").toOrgId(),
            )
        }

/** Занятие, по которому предварительно пора создать задачу. */
internal data class DueSession(
    /** Идентификатор занятия. */
    val id: SessionId,
    /** Организация занятия. */
    val orgId: OrgId,
)
