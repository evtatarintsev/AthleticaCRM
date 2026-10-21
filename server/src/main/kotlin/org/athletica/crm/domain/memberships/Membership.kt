package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.core.subscription.UnfreezeReason
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

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

    /**
     * Сохранённая заморозка; `null` — нет. Может быть уже закончившейся, пока её не снял
     * планировщик: текущую на дату даёт [freezeAt].
     */
    val freeze: MembershipFreeze?

    /** Запланированная или действующая на дату [today] заморозка; закончившаяся считается отсутствующей. */
    fun freezeAt(today: LocalDate): MembershipFreeze? = freeze?.takeIf { it.statusAt(today) != null }

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

    /**
     * Замораживает абонемент на период [start]..[end] (включительно) по причине [reason] при
     * сегодняшней дате организации [today] и сдвигает дату окончания на длину периода
     * ([freezePeriodError] — проверки периода). Абонемент с сохранённой заморозкой, даже
     * закончившейся, не замораживается: её сначала снимает [unfreeze]. Автор — сотрудник из контекста.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun freeze(
        start: LocalDate,
        end: LocalDate,
        reason: String,
        today: LocalDate,
    ): ActiveMembership

    /**
     * Снимает заморозку в день [day] по основанию [reason] и уменьшает дату окончания на
     * неиспользованные дни ([MembershipFreeze.unusedDaysFrom]). Без заморозки — ошибка.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun unfreeze(day: LocalDate, reason: UnfreezeReason): ActiveMembership

    /**
     * Продлевает абонемент до [newEndDate] по причине [reason] (сохраняется без пробелов по краям)
     * и пишет продление в историю. Новая дата должна быть позже текущей даты окончания, причина —
     * непустой; абонемент с сохранённой заморозкой не продлевается. Если на дату [today] абонемент
     * перестаёт быть «к закрытию», эпизод уведомления закрывается. Автор — сотрудник из контекста.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun extend(newEndDate: LocalDate, reason: String, today: LocalDate): ActiveMembership

    /** Отмечает отправку уведомления «к закрытию» в текущем эпизоде и пишет её в историю. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun markCloseNotified(): ActiveMembership

    /**
     * Списывает с абонемента одно занятие [sessionId] от [sessionDate] за клиента-владельца и пишет
     * списание в историю. Остаток уменьшается на 1 (кроме безлимита) guarded-обновлением: если
     * параллельная транзакция успела обнулить остаток, бросает [MembershipDebitConflict].
     * С режимом «при списании последнего» абонемент, остаток которого стал 0, закрывается датой [today].
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun debit(sessionId: SessionId, sessionDate: LocalDate, today: LocalDate): Membership

    /**
     * Возвращает занятие действующего списания [debit] на абонемент. Если после возврата абонемент
     * на дату [today] больше не «к закрытию», эпизод уведомления закрывается: следующее «к закрытию»
     * снова уведомит выдавшего.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun reverse(debit: MembershipDebit, today: LocalDate): ActiveMembership

    /**
     * Гасит долг: списывает неоплаченные занятия [sessions] клиента-владельца, в том числе вне срока,
     * и пишет каждое в историю как погашение долга. Дата начала не меняется. Занятий не больше
     * остатка (кроме безлимита), иначе ошибка; уже списанное занятие — ошибка `ALREADY_DEBITED`.
     * С режимом «при списании последнего» абонемент, остаток которого стал 0, закрывается датой [today].
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun repayDebt(sessions: List<DebtSession>, today: LocalDate): Membership
}

/** Неоплаченное занятие [sessionId] от [sessionDate], которое гасится с абонемента. */
data class DebtSession(
    /** Занятие. */
    val sessionId: SessionId,
    /** Дата занятия. */
    val sessionDate: LocalDate,
)

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

    /**
     * Возвращает занятие действующего списания [debit] на закрытый абонемент: корректировка
     * допустима, остаток растёт, состояние остаётся «Закрыт».
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun reverse(debit: MembershipDebit): ClosedMembership
}

/** Вид списания. */
enum class DebitKind {
    /** Списание при проведении занятия или правке отметки. */
    REGULAR,

    /** Погашение долга сотрудником. */
    DEBT,
}

/** Действующее списание занятия [sessionId] клиента [clientId] с абонемента [membershipId]. */
data class MembershipDebit(
    /** Идентификатор списания. */
    val id: Uuid,
    /** Абонемент, с которого списано занятие. */
    val membershipId: MembershipId,
    /** Клиент-участник. */
    val clientId: ClientId,
    /** Занятие. */
    val sessionId: SessionId,
    /** Дата занятия. */
    val sessionDate: LocalDate,
    /** Вид списания. */
    val kind: DebitKind,
)

/**
 * Подбор или списание не удались из-за параллельных списаний с тех же абонементов.
 * Это временный сбой, а не отсутствие абонемента: обработка повторяется воркером событий.
 */
class MembershipDebitConflict(message: String) : RuntimeException(message)

/** Отозванный абонемент: финальное состояние, в списаниях не участвует. */
interface RevokedMembership : Membership {
    /** Дата начала, если абонемент был активирован до отзыва. */
    val startDate: LocalDate?

    /** Дата окончания, если абонемент был активирован до отзыва. */
    val endDate: LocalDate?
}
