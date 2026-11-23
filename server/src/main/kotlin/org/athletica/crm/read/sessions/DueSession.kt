package org.athletica.crm.read.sessions

import arrow.core.raise.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.sql.JournalWindowSql
import org.athletica.crm.storage.sql.bindJournalWindow
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
                SELECT s.id, s.org_id, ${JournalWindowSql.DEADLINE} AS deadline
                FROM sessions s
                JOIN organizations o ON o.id = s.org_id
                LEFT JOIN attendance_control_settings acs ON acs.org_id = s.org_id
                WHERE s.status = 'scheduled'
                  AND s.date >= :fromDate
                  AND ${JournalWindowSql.ENABLED}
                  AND NOT EXISTS (SELECT 1 FROM session_overdue_tasks t WHERE t.session_id = s.id)
            )
            SELECT id, org_id
            FROM candidates
            WHERE ${JournalWindowSql.DUE}
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
