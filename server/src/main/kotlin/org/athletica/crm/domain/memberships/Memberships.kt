package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.storage.Transaction

/**
 * Абонементы клиентов организации.
 *
 * Абонемент выдаётся один раз и хранит собственный снимок параметров, поэтому правка
 * тарифа на ранее выданные абонементы не влияет. Состояние меняется только переходами
 * типов [Membership].
 */
interface Memberships {
    /**
     * Выдаёт абонемент [id] клиенту [clientId] в дату [issuedOn] (в часовом поясе организации)
     * и сохраняет его вместе с записью истории о выдаче.
     *
     * Абонемент действует в группах [groupIds] организации — хотя бы одной.
     * Остаток занятий равен [sessions] (`null` — безлимит). Начальное состояние определяют
     * [activation] и правила выдачи ([issueOutcome]): при активации при выдаче в историю
     * пишется и активация. Выдавший — сотрудник из контекста.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun issue(
        id: MembershipId,
        clientId: ClientId,
        tariffPlanId: TariffPlanId?,
        name: String,
        sessions: Int?,
        groupIds: List<GroupId>,
        duration: MembershipDuration,
        price: Money,
        activation: Activation,
        closeMode: CloseMode,
        issuedOn: LocalDate,
    ): Membership

    /**
     * Возвращает абонемент [id] организации, блокируя его строку до конца транзакции
     * (`FOR UPDATE`): за ним следует команда перехода.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: MembershipId): Membership

    /**
     * Подбирает абонемент клиента [clientId] для списания занятия группы [groupId] от [sessionDate]
     * при сегодняшней дате организации [today] (правило `MembershipCandidateSql`) и блокирует его
     * строку. После блокировки кандидат перепроверяется ([isDebitCandidate]); если его перехватила
     * параллельная транзакция, подбор повторяется до трёх раз.
     *
     * `null` — подходящих абонементов нет (нормальный исход). Если кандидаты были, но все попытки
     * перехвачены, бросает [MembershipDebitConflict].
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun debitCandidate(
        clientId: ClientId,
        groupId: GroupId,
        sessionDate: LocalDate,
        today: LocalDate,
    ): Membership?

    /** Действующее списание за участие клиента [clientId] в занятии [sessionId]; `null` — нет. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun activeDebit(sessionId: SessionId, clientId: ClientId): MembershipDebit?

    /** Возвращает абонементы клиента, новые сверху. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun forClient(clientId: ClientId): List<Membership>
}
