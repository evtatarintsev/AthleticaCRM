package org.athletica.crm.usecases.memberships

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.ClosedMembership
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.memberships.RevokedMembership
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.read.memberships.UnpaidSessionsView
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asUuid
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Фоновая обработка абонементов по местной дате организации: активация «с даты» и по крайней
 * дате первого посещения, снятие закончившейся заморозки, закрытие по сроку ([CloseMode.ON_EXPIRY])
 * и уведомления «к закрытию».
 *
 * Один SQL выбирает абонементы, которым что-то положено; каждый обрабатывается в своей транзакции
 * в системном контексте организации, а правило перепроверяется по свежему состоянию — повторный
 * проход по тому же абонементу ничего не меняет.
 *
 * [clock] задаёт «сейчас» для выборки и подменяется в тестах вместе с [calendar];
 * [interval] — пауза между проходами.
 */
class MembershipScheduler(
    private val db: Database,
    private val memberships: Memberships,
    private val notifications: Notifications,
    private val employees: Employees,
    private val calendar: OrgCalendar,
    private val unpaidSessions: UnpaidSessionsView,
    private val tasks: Tasks,
    private val clock: Clock = Clock.System,
    private val interval: Duration = 15.minutes,
) {
    private val logger = LoggerFactory.getLogger(MembershipScheduler::class.java)

    /** Запускает бесконечный цикл обработки. Должен выполняться в отдельной корутине. */
    suspend fun run() {
        logger.info("Фоновая обработка абонементов запущена")
        while (true) {
            try {
                processDue()
            } catch (e: Exception) {
                logger.error("Ошибка в цикле обработки абонементов: ${e.message}", e)
            }
            delay(interval)
        }
    }

    /** Один проход: обрабатывает все абонементы, которым на сейчас положено действие. */
    suspend fun processDue() {
        val due = db.transaction { dueMemberships() }
        due.forEach { (orgId, membershipId) ->
            try {
                db.transaction {
                    either {
                        context(SystemContexts.forOrg(orgId)) {
                            process(membershipId, calendar.today())
                        }
                    }.getOrElse { throw IllegalStateException("Абонемент $membershipId не обработан: $it") }
                }
            } catch (e: Exception) {
                logger.error("Ошибка обработки абонемента $membershipId: ${e.message}", e)
            }
        }
    }

    /**
     * Применяет к абонементу [id] положенное на дату [today] действие по его текущему состоянию:
     * активацию (с задачей «Проверить клиента» при долгах), снятие закончившейся заморозки,
     * закрытие по сроку или уведомление
     * «к закрытию».
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun process(id: MembershipId, today: LocalDate) {
        when (val membership = memberships.byId(id)) {
            is InactiveMembership.OnDate ->
                if (membership.activation.date <= today) {
                    val active = membership.activate(membership.activation.date, ActivationReason.ON_DATE)
                    createCheckClientTaskIfDebts(unpaidSessions, tasks, active)
                }
            is InactiveMembership.OnFirstVisit -> {
                val deadline = membership.activation.deadline
                if (deadline != null && deadline <= today) {
                    val active = membership.activate(deadline, ActivationReason.ON_DEADLINE)
                    createCheckClientTaskIfDebts(unpaidSessions, tasks, active)
                }
            }
            is ActiveMembership -> {
                val active = clearExpiredFreeze(membership, today)
                if (active.closeMode == CloseMode.ON_EXPIRY && active.endDate < today) {
                    active.close(CloseReason.EXPIRY, today)
                } else {
                    notifyClosePendingIfDue(notifications, employees, active, today)
                }
            }
            is ClosedMembership, is RevokedMembership -> Unit
        }
    }

    /** Абонементы всех организаций, которым по местной дате организации положено действие. */
    context(tr: Transaction)
    private suspend fun dueMemberships(): List<Pair<OrgId, MembershipId>> =
        tr
            .sql(
                """
                WITH local AS (
                    SELECT m.*, (CAST(:now AS timestamptz) AT TIME ZONE o.timezone)::date AS today
                    FROM memberships m
                    JOIN organizations o ON o.id = m.org_id
                    WHERE m.state IN ('INACTIVE', 'ACTIVE')
                )
                SELECT org_id, id FROM local
                WHERE (state = 'INACTIVE' AND activation_mode = 'ON_DATE' AND activate_on <= today)
                   OR (state = 'INACTIVE' AND activation_mode = 'ON_FIRST_VISIT' AND activation_deadline <= today)
                   OR (state = 'ACTIVE' AND freeze_end < today)
                   OR (state = 'ACTIVE' AND close_mode = 'ON_EXPIRY' AND end_date < today)
                   OR (state = 'ACTIVE' AND close_notified_at IS NULL
                       AND EXISTS (SELECT 1 FROM employees e WHERE e.id = issued_by AND e.is_active)
                       AND ((close_mode = 'MANUAL' AND (end_date < today OR sessions_remaining = 0))
                         OR (close_mode = 'ON_LAST_SESSION' AND end_date < today AND sessions_remaining > 0)))
                ORDER BY org_id, id
                """.trimIndent(),
            )
            .bind("now", clock.now())
            .list { it.asUuid("org_id").toOrgId() to it.asUuid("id").toMembershipId() }
}
