package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Параметры абонемента, общие для всех состояний: снимок условий на момент выдачи.
 * Не меняются после выдачи; от состояния зависят только даты, остаток и признаки закрытия.
 */
interface MembershipTerms {
    /** Идентификатор абонемента. */
    val id: MembershipId

    /** Клиент-владелец абонемента. */
    val clientId: ClientId

    /** Тариф-шаблон, на основе которого выдан абонемент; `null` — индивидуальный. */
    val tariffPlanId: TariffPlanId?

    /** Название (снимок на момент выдачи). */
    val name: String

    /** Общее количество занятий; `null` — безлимит. */
    val sessionsTotal: Int?

    /** Срок действия. */
    val duration: MembershipDuration

    /** Группы, в которых действует абонемент; не пусто. */
    val groups: List<MembershipGroup>

    /** Стоимость на момент выдачи (без учёта оплаты/баланса). */
    val price: Money

    /** Режим закрытия. */
    val closeMode: CloseMode

    /** Дата выдачи в часовом поясе организации. */
    val issuedOn: LocalDate

    /** Сотрудник, выдавший абонемент; `null`, если выдан системой или сотрудник удалён. */
    val issuedBy: EmployeeId?
}

/**
 * Выданный абонемент в одном из сохранённых состояний.
 *
 * Каждое состояние — отдельный тип, и у него есть только допустимые для него переходы:
 * `Неактивирован` → `Активный` → `Закрыт`, `Отозван` — из любого нефинального.
 * Переход — команда: пишет строку абонемента и событие истории в текущей транзакции
 * и возвращает абонемент в новом состоянии. Автор события — из [RequestContext].
 */
sealed interface Membership : MembershipTerms {
    /** Режим активации, заданный при выдаче. */
    val activation: Activation

    /** Остаток занятий; `null` — безлимит. До активации равен [sessionsTotal]. */
    val sessionsRemaining: Int?
}

/**
 * Группа абонемента: идентификатор и название на момент чтения.
 * Название подставляется тем же запросом, что читает абонемент (design.md, п. 10).
 */
data class MembershipGroup(
    /** Идентификатор группы. */
    val id: GroupId,
    /** Название группы. */
    val name: String,
)

/** Абонемент, ещё не активированный: дат начала и окончания нет. */
sealed interface InactiveMembership : Membership {
    /**
     * Активирует абонемент с датой начала [startDate] по причине [reason].
     * Дата окончания вычисляется из [startDate] и срока.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun activate(startDate: LocalDate, reason: ActivationReason): ActiveMembership

    /** Отзывает абонемент. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun revoke(): RevokedMembership

    /** Неактивированный абонемент, который активируется в дату [activation].date. */
    interface OnDate : InactiveMembership {
        override val activation: Activation.OnDate
    }

    /** Неактивированный абонемент, который активируется первым посещением или по крайней дате. */
    interface OnFirstVisit : InactiveMembership {
        override val activation: Activation.OnFirstVisit
    }
}

/** Активный абонемент: даты начала и окончания заданы, по нему списываются занятия. */
interface ActiveMembership : Membership {
    /** Дата начала действия; после активации не меняется. */
    val startDate: LocalDate

    /** Дата окончания действия, последний день включительно. */
    val endDate: LocalDate

    /** Момент отправки уведомления «к закрытию» в текущем эпизоде; `null` — не отправлялось. */
    val closeNotifiedAt: Instant?

    /** Выводимые свойства на дату [date]: чистая функция дат, остатка и [date]. */
    fun statusAt(date: LocalDate): ActiveStatus = ActiveStatus.of(startDate, endDate, sessionsRemaining, date)

    /**
     * Нужно ли на дату [date] уведомить выдавшего, что абонемент пора закрыть:
     * абонемент «к закрытию», режим закрытия не закроет его сам и уведомление в эпизоде не отправлялось.
     */
    fun closeNotificationDueAt(date: LocalDate): Boolean = closeNotifiedAt == null && statusAt(date).needsCloseNotification(closeMode)

    /** Закрывает абонемент датой [closedOn] по причине [reason]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun close(reason: CloseReason, closedOn: LocalDate): ClosedMembership

    /** Отзывает абонемент. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun revoke(): RevokedMembership

    /** Отмечает отправку уведомления «к закрытию» в текущем эпизоде и пишет её в историю. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun markCloseNotified(): ActiveMembership
}

/** Закрытый абонемент: финальное состояние, переходов нет. */
interface ClosedMembership : Membership {
    /** Дата начала действия. */
    val startDate: LocalDate

    /** Дата окончания действия. */
    val endDate: LocalDate

    /** Дата закрытия. */
    val closedOn: LocalDate

    /** Причина закрытия. */
    val closeReason: CloseReason
}

/** Отозванный абонемент: финальное состояние, в списаниях не участвует. */
interface RevokedMembership : Membership {
    /** Дата начала, если абонемент был активирован до отзыва. */
    val startDate: LocalDate?

    /** Дата окончания, если абонемент был активирован до отзыва. */
    val endDate: LocalDate?
}
