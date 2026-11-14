package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.athletica.crm.core.PositiveInt
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.core.subscription.FreezeStatus
import org.athletica.crm.i18n.Messages

/** Срок действия абонемента: [value] [unit]. */
data class MembershipDuration(
    /** Числовое значение срока. */
    private val length: PositiveInt,
    /** Единица срока. */
    val unit: DurationUnit,
) {
    val value: Int get() = length.value

    /**
     * Дата окончания срока, начавшегося [startDate], — последний день действия включительно.
     *
     * Срок в днях: `startDate + N дней − 1 день`. Срок в месяцах: `target = startDate + N месяцев`;
     * если в целевом месяце нет числа [startDate] и `kotlinx.datetime` обрезал его до последнего
     * дня месяца, окончание — `target`, иначе `target − 1 день`.
     */
    fun endDateFrom(startDate: LocalDate): LocalDate =
        when (unit) {
            DurationUnit.DAYS -> startDate.plus(DatePeriod(days = value)).minus(DatePeriod(days = 1))
            DurationUnit.MONTHS -> {
                val target = startDate.plus(DatePeriod(months = value))
                if (target.day < startDate.day) {
                    target
                } else {
                    target.minus(DatePeriod(days = 1))
                }
            }
        }
}

/** Режим активации, заданный при выдаче, вместе с его датами. */
sealed interface Activation {
    /** Режим активации. */
    val mode: ActivationMode

    /** Активация сразу при выдаче. */
    data object Immediate : Activation {
        override val mode = ActivationMode.IMMEDIATE
    }

    /** Активация с даты [date]; она же дата начала. */
    data class OnDate(
        /** Дата активации и начала действия. */
        val date: LocalDate,
    ) : Activation {
        override val mode = ActivationMode.ON_DATE
    }

    /** Активация первым посещением; [deadline] — необязательная крайняя дата активации. */
    data class OnFirstVisit(
        /** Крайняя дата активации; `null` — без крайней даты. */
        val deadline: LocalDate?,
    ) : Activation {
        override val mode = ActivationMode.ON_FIRST_VISIT
    }
}

/**
 * Текущая заморозка активного абонемента: период [start]..[end] включительно. Дата окончания
 * абонемента сдвигается на [days] при оформлении, поэтому всегда позже [end].
 */
data class MembershipFreeze(
    /** Первый день заморозки. */
    val start: LocalDate,
    /** Последний день заморозки включительно. */
    val end: LocalDate,
    /** Причина заморозки. */
    val reason: String,
    /** Сотрудник, оформивший заморозку; `null`, если он удалён. */
    val frozenBy: EmployeeId?,
) {
    /** Длина периода в днях включительно. */
    val days: Int
        get() = start.daysUntil(end) + 1

    /** Состояние на дату [today]; `null` — заморозка закончилась. */
    fun statusAt(today: LocalDate): FreezeStatus? =
        when {
            today > end -> null
            today < start -> FreezeStatus.SCHEDULED
            else -> FreezeStatus.ACTIVE
        }

    /** Попадает ли дата [date] в период заморозки. */
    fun covers(date: LocalDate): Boolean = date in start..end

    /**
     * Неиспользованные дни при снятии заморозки в день [day]: с [day] (но не раньше [start]) по [end]
     * включительно, сам [day] уже не заморожен; 0, если [day] позже [end].
     */
    fun unusedDaysFrom(day: LocalDate): Int = maxOf(0, maxOf(day, start).daysUntil(end) + 1)
}

/**
 * Ошибка периода заморозки [start]..[end] с причиной [reason] для абонемента с датой окончания
 * [endDate] при сегодняшней дате организации [today]; `null` — период допустим.
 */
context(ctx: RequestContext)
fun freezePeriodError(
    start: LocalDate,
    end: LocalDate,
    reason: String,
    endDate: LocalDate,
    today: LocalDate,
): DomainError? =
    when {
        reason.isBlank() -> CommonDomainError("FREEZE_REASON_REQUIRED", Messages.MembershipFreezeReasonRequired.localize())
        end < start -> CommonDomainError("FREEZE_PERIOD_INVALID", Messages.MembershipFreezePeriodInvalid.localize())
        start < today -> CommonDomainError("FREEZE_IN_PAST", Messages.MembershipFreezeInPast.localize())
        start > endDate -> CommonDomainError("FREEZE_AFTER_END", Messages.MembershipFreezeAfterEnd.localize())
        else -> null
    }

/**
 * Подходит ли абонемент для списания занятия группы [groupId] от [sessionDate] при сегодняшней
 * дате организации [today]. Повторяет правило `MembershipCandidateSql` и служит перепроверкой
 * кандидата после блокировки его строки.
 */
fun Membership.isDebitCandidate(groupId: GroupId, sessionDate: LocalDate, today: LocalDate): Boolean {
    val hasSessions = sessionsRemaining.let { it == null || it > 0 }
    if (!hasSessions || groups.none { it.id == groupId }) {
        return false
    }
    return when (this) {
        is ActiveMembership -> sessionDate in startDate..endDate
        is InactiveMembership.OnDate -> {
            val activateOn = activation.date
            activateOn <= today && sessionDate in activateOn..duration.endDateFrom(activateOn)
        }
        is InactiveMembership.OnFirstVisit -> {
            val deadline = activation.deadline
            sessionDate >= issuedOn && (deadline == null || sessionDate <= deadline)
        }
        is ClosedMembership, is RevokedMembership -> false
    }
}

/** Начальное состояние выдаваемого абонемента. */
sealed interface IssueOutcome {
    /** Абонемент активируется при выдаче с датой начала [startDate]. */
    data class Active(
        /** Дата начала действия. */
        val startDate: LocalDate,
    ) : IssueOutcome

    /** Абонемент выдаётся неактивированным. */
    data object Inactive : IssueOutcome
}

/**
 * Правила выдачи: проверяет сочетание режима активации [activation], режима закрытия [closeMode],
 * количества занятий [sessions] (`null` — безлимит) и групп [groupIds] и определяет начальное
 * состояние при выдаче в дату [issuedOn].
 *
 * - Хотя бы одна группа.
 * - «При списании последнего» недопустим для безлимита.
 * - Крайняя дата первого посещения не раньше [issuedOn].
 * - «Сразу» активирует с [issuedOn]; «с даты» не позже [issuedOn] активирует с этой даты.
 */
context(ctx: RequestContext, raise: Raise<AppError>)
fun issueOutcome(
    activation: Activation,
    closeMode: CloseMode,
    sessions: Int?,
    groupIds: List<GroupId>,
    issuedOn: LocalDate,
): IssueOutcome {
    if (groupIds.isEmpty()) {
        raise(CommonDomainError("MEMBERSHIP_GROUPS_REQUIRED", Messages.MembershipGroupsRequired.localize()))
    }
    if (closeMode == CloseMode.ON_LAST_SESSION && sessions == null) {
        raise(CommonDomainError("UNLIMITED_CLOSE_ON_LAST_SESSION", Messages.MembershipUnlimitedCloseOnLastSession.localize()))
    }
    return when (activation) {
        Activation.Immediate -> IssueOutcome.Active(issuedOn)
        is Activation.OnDate ->
            if (activation.date <= issuedOn) {
                IssueOutcome.Active(activation.date)
            } else {
                IssueOutcome.Inactive
            }
        is Activation.OnFirstVisit -> {
            if (activation.deadline != null && activation.deadline < issuedOn) {
                raise(CommonDomainError("ACTIVATION_DEADLINE_BEFORE_ISSUE", Messages.MembershipDeadlineBeforeIssue.localize()))
            }
            IssueOutcome.Inactive
        }
    }
}
