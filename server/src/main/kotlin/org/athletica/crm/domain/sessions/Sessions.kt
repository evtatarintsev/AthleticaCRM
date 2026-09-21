package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction

/** Репозиторий занятий организации. */
interface Sessions {
    /**
     * Создаёт разовое занятие вне расписания; возвращает `null`, если занятие с таким [id] уже есть.
     * Занятия из расписания материализует [ScheduleSync], а не этот метод.
     * [employees] — преподаватели; каждый должен иметь доступ к филиалу группы,
     * иначе ошибка `EMPLOYEE_NOT_FOUND`.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: SessionId,
        groupId: GroupId,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        hallId: HallId,
        notes: String?,
        employees: List<Employee>,
    ): Session?

    /** Возвращает список занятий группы за период [from]..[to]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(
        groupId: GroupId,
        from: LocalDate,
        to: LocalDate,
    ): List<Session>

    /** Возвращает список всех занятий организации за период [from]..[to]. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun listAll(
        from: LocalDate,
        to: LocalDate,
    ): List<Session>

    /** Возвращает занятие по идентификатору. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: SessionId): Session
}
