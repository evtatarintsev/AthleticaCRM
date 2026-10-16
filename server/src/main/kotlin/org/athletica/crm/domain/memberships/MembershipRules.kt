package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.core.subscription.EndingSoonReason
import org.athletica.crm.core.subscription.TermPosition
import org.athletica.crm.i18n.Messages

/** Срок действия абонемента: [value] [unit]. */
data class MembershipDuration(
    /** Числовое значение срока. */
    val value: Int,
    /** Единица срока. */
    val unit: DurationUnit,
) {
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

/** Остаток занятий активного абонемента. */
sealed interface Remaining {
    /** Есть [sessions] занятий. */
    data class Left(
        /** Количество оставшихся занятий, больше нуля. */
        val sessions: Int,
    ) : Remaining

    /** Занятия закончились (остаток 0). */
    data object Exhausted : Remaining

    /** Безлимит. */
    data object Unlimited : Remaining
}

/**
 * Выводимые на дату свойства активного абонемента; не хранятся.
 * «К закрытию» ([closePending]) — срок вышел или остаток исчерпан.
 */
data class ActiveStatus(
    /** Положение даты относительно срока. */
    val termPosition: TermPosition,
    /** Остаток занятий. */
    val remaining: Remaining,
    /** Причины признака «заканчивается»; пусто — не заканчивается. */
    val endingSoon: Set<EndingSoonReason>,
) {
    /** Абонемент «к закрытию»: срок вышел или занятия закончились. */
    val closePending: Boolean
        get() = termPosition == TermPosition.EXPIRED || remaining == Remaining.Exhausted

    /**
     * Нужно ли уведомление «к закрытию» при режиме закрытия [closeMode]: абонемент «к закрытию»,
     * а режим его сам не закроет. «Вручную» — при любом «к закрытию»; «при списании последнего» —
     * при вышедшем сроке и оставшихся занятиях; «по истечении срока» — никогда.
     */
    fun needsCloseNotification(closeMode: CloseMode): Boolean =
        when (closeMode) {
            CloseMode.MANUAL -> closePending
            CloseMode.ON_LAST_SESSION -> termPosition == TermPosition.EXPIRED && remaining is Remaining.Left
            CloseMode.ON_EXPIRY -> false
        }

    companion object {
        /** Порог «заканчивается по дате»: до даты окончания столько дней или меньше. */
        const val ENDING_SOON_DAYS = 7

        /** Порог «заканчивается по занятиям»: остаток ровно столько занятий. */
        const val ENDING_SOON_SESSIONS = 1

        /**
         * Свойства абонемента со сроком [startDate]..[endDate] (включительно) и остатком
         * [sessionsRemaining] (`null` — безлимит) на дату [date].
         */
        fun of(
            startDate: LocalDate,
            endDate: LocalDate,
            sessionsRemaining: Int?,
            date: LocalDate,
        ): ActiveStatus {
            val termPosition =
                when {
                    date < startDate -> TermPosition.NOT_STARTED
                    date > endDate -> TermPosition.EXPIRED
                    else -> TermPosition.IN_TERM
                }
            val remaining =
                when {
                    sessionsRemaining == null -> Remaining.Unlimited
                    sessionsRemaining <= 0 -> Remaining.Exhausted
                    else -> Remaining.Left(sessionsRemaining)
                }
            val endingSoon =
                if (termPosition == TermPosition.IN_TERM) {
                    buildSet {
                        if (date.daysUntil(endDate) <= ENDING_SOON_DAYS) {
                            add(EndingSoonReason.BY_DATE)
                        }
                        if (remaining == Remaining.Left(ENDING_SOON_SESSIONS)) {
                            add(EndingSoonReason.BY_SESSIONS)
                        }
                    }
                } else {
                    emptySet()
                }
            return ActiveStatus(termPosition, remaining, endingSoon)
        }
    }
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
context(ctx: RequestContext, raise: Raise<DomainError>)
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
