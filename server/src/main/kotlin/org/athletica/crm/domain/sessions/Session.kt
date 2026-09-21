package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction

/** Одно занятие группы — конкретный экземпляр расписания. */
interface Session {
    val id: SessionId

    /** Группа, которой принадлежит занятие. */
    val groupId: GroupId

    /** Дата проведения занятия. */
    val date: LocalDate

    /** Время начала занятия. */
    val startTime: LocalTime

    /** Время окончания занятия. */
    val endTime: LocalTime

    /** Зал проведения занятия. */
    val hallId: HallId

    /**
     * Статус занятия: `scheduled`, `completed`, `cancelled`.
     * Соответствует enum-типу `session_status` в БД.
     */
    val status: String

    /** Занятие было перенесено с оригинальной даты/времени. */
    val isRescheduled: Boolean

    /** Версия слота расписания, породившая занятие; `null` — занятие создано вручную. */
    val originSlotId: SlotId?

    /** Дата, на которую версия слота предписала занятие (неизменна при переносе). Null для ручных занятий. */
    val originDate: LocalDate?

    /**
     * Занятие создано вручную и не управляется расписанием.
     * Выводится из отсутствия происхождения, поэтому рассинхрон с [originSlotId] невозможен.
     */
    val isManual: Boolean get() = originSlotId == null

    /** Произвольные заметки к занятию. */
    val notes: String?

    /** Преподаватели, закреплённые за занятием. */
    val employeeIds: List<EmployeeId>

    /** Признак, что состав преподавателей меняли вручную на уровне занятия. */
    val isEmployeeAssignmentOverridden: Boolean

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun cancel()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun reschedule(
        newDate: LocalDate,
        newStartTime: LocalTime,
        newEndTime: LocalTime,
        newHallId: HallId,
    )

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun complete()

    /**
     * Заменяет состав преподавателей занятия.
     * Каждый [Employee] из [employees] должен иметь доступ к филиалу группы,
     * иначе ошибка `EMPLOYEE_NOT_FOUND`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun setEmployees(employees: List<Employee>)
}
