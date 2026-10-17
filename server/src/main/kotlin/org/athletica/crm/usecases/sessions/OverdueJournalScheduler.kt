package org.athletica.crm.usecases.sessions

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.domain.attendancecontrol.AttendanceControl
import org.athletica.crm.domain.attendancecontrol.JournalCloseDelay
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.i18n.Messages
import org.athletica.crm.i18n.localized
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Фоновое создание задачи «Закрыть журнал» по занятиям, журнал которых не закрыт за окно
 * из настроек контроля посещаемости организации.
 *
 * Один SQL выбирает по всем организациям запланированные занятия, срок задачи которых
 * (окончание по расписанию в часовом поясе организации плюс окно) наступил за последние
 * [LOOKBACK]; давние незакрытые занятия задачу не получают. Каждое занятие обрабатывается
 * в своей транзакции системного контекста: занятие сначала занимается в `session_overdue_tasks`,
 * и только при успехе создаётся задача — поэтому по занятию бывает не больше одной задачи
 * при повторных и параллельных проходах.
 *
 * [clock] задаёт «сейчас» и подменяется в тестах; [interval] — пауза между проходами.
 */
class OverdueJournalScheduler(
    private val db: Database,
    private val tasks: Tasks,
    private val clock: Clock = Clock.System,
    private val interval: Duration = 5.minutes,
) {
    private val logger = LoggerFactory.getLogger(OverdueJournalScheduler::class.java)

    /** Запускает бесконечный цикл обработки. Должен выполняться в отдельной корутине. */
    suspend fun run() {
        logger.info("Контроль незакрытых журналов запущен")
        while (true) {
            try {
                processDue()
            } catch (e: Exception) {
                logger.error("Ошибка в цикле контроля незакрытых журналов: ${e.message}", e)
            }
            delay(interval)
        }
    }

    /** Один проход: создаёт задачи по всем занятиям, срок задачи которых наступил. */
    suspend fun processDue() {
        val due = db.transaction { dueSessions() }
        due.forEach { session ->
            try {
                db.transaction {
                    either {
                        context(SystemContexts.forOrg(session.orgId)) {
                            createTask(session)
                        }
                    }.getOrElse { throw IllegalStateException("Задача по занятию ${session.id} не создана: $it") }
                }
            } catch (e: Exception) {
                logger.error("Ошибка создания задачи по занятию ${session.id}: ${e.message}", e)
            }
        }
    }

    /**
     * Занимает занятие [session] и создаёт по нему задачу без исполнителя. Если занятие уже
     * занято другим проходом или успело стать проведённым или отменённым — ничего не делает.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun createTask(session: DueSession) {
        val taskId = TaskId.new()
        val claimed =
            tr
                .sql(
                    """
                    INSERT INTO session_overdue_tasks (session_id, org_id, task_id)
                    SELECT id, org_id, :taskId FROM sessions WHERE id = :sessionId AND status = 'scheduled'
                    ON CONFLICT (session_id) DO NOTHING
                    """.trimIndent(),
                )
                .bind("taskId", taskId)
                .bind("sessionId", session.id)
                .execute()
        if (claimed == 0L) {
            return
        }
        val time = "${session.date.localized(ctx.lang)} ${session.startTime}–${session.endTime}"
        tasks.new(
            id = taskId,
            title = Messages.OverdueJournalTaskTitle.localize(session.groupName),
            description = Messages.OverdueJournalTaskDescription.localize(session.groupName, time),
            clientId = null,
            dueDate = null,
            dueDateEnd = null,
        )
    }

    /** Занятия всех организаций, срок задачи по которым наступил за последние [LOOKBACK]. */
    context(tr: Transaction)
    private suspend fun dueSessions(): List<DueSession> {
        val now = clock.now()
        val fromDate =
            (now - JournalCloseDelay.MAX_MINUTES.minutes - LOOKBACK - 2.days)
                .toLocalDateTime(TimeZone.UTC)
                .date
        return tr
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
            .bind("defaultDelay", JournalCloseDelay.DEFAULT.minutes)
            .bind("defaultEnabled", AttendanceControl.DEFAULT_OVERDUE_TASK_ENABLED)
            .bind("fromDate", fromDate.toJavaLocalDate())
            .bind("now", now)
            .bind("lookbackSeconds", LOOKBACK.inWholeSeconds.toDouble())
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
    }

    /** Занятие, по которому пора создать задачу, с данными для её текста. */
    private data class DueSession(
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

    private companion object {
        /** Насколько давно может наступить срок задачи, чтобы она ещё создавалась. */
        val LOOKBACK = 1.days
    }
}
