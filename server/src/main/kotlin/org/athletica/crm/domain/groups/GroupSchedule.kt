package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Расписание группы как история правил во времени.
 *
 * Метода, возвращающего слоты без указания момента времени, здесь нет и быть не должно:
 * у слота есть период действия, поэтому «расписание группы» без даты — бессмысленный вопрос.
 * Забыть фильтр по времени невозможно, потому что его нечем забыть.
 */
interface GroupSchedule {
    /** Слоты группы [groupId], действующие на [date]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun slotsOn(groupId: GroupId, date: LocalDate): List<ScheduleSlot>

    /** Слоты группы [groupId], период действия которых пересекает [from]..[to] включительно. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun slotsDuring(groupId: GroupId, from: LocalDate, to: LocalDate): List<ScheduleSlot>

    /**
     * Устанавливает расписание группы [groupId] начиная с [effectiveFrom] и навсегда:
     * всё, что действовало начиная с этой даты, включая ранее запланированные изменения,
     * заменяется на [slots]. Расписание до [effectiveFrom] сохраняется.
     *
     * [effectiveFrom] в прошлом отклоняется; `null` означает сегодняшний день.
     * Пустой [slots] означает, что с этой даты у группы нет расписания.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun setFrom(groupId: GroupId, effectiveFrom: LocalDate?, slots: List<NewSlot>)
}
