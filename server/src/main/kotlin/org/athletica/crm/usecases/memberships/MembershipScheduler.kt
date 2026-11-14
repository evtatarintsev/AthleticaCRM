package org.athletica.crm.usecases.memberships

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.memberships.advanceCalendar
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.domain.tasks.Tasks
import org.athletica.crm.read.memberships.UnpaidSessionsView
import org.athletica.crm.read.memberships.dueMemberships
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
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
        val due = db.transaction { orThrow { dueMemberships(clock.now()) } }
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
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun process(id: MembershipId, today: LocalDate) {
        val previous = memberships.byId(id)
        val updated = previous.advanceCalendar(today)
        if (updated is ActiveMembership) {
            when (previous) {
                is InactiveMembership -> createCheckClientTaskIfDebts(unpaidSessions, tasks, updated)
                is ActiveMembership -> notifyClosePendingIfDue(notifications, employees, updated, today)
                else -> Unit
            }
        }
    }
}
