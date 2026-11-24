package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction

/**
 * Данные занятия, общие для всех состояний.
 * От состояния зависят только допустимые переходы, см. [Session].
 */
interface SessionData {
    /** Идентификатор занятия. */
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
}

/**
 * Одно занятие группы — конкретный экземпляр расписания — в одном из сохранённых состояний.
 *
 * Каждое состояние — отдельный тип, и переходы есть только у допустимого:
 * `Запланировано` → `Проведено` или `Отменено`. Переход пишет строку занятия условием
 * на текущее состояние, поэтому устаревший экземпляр не проведёт и не отменит занятие повторно.
 */
sealed interface Session : SessionData {
    /** Статус занятия, соответствующий состоянию. */
    val status: SessionStatus
        get() =
            when (this) {
                is ScheduledSession -> SessionStatus.SCHEDULED
                is CompletedSession -> SessionStatus.COMPLETED
                is CancelledSession -> SessionStatus.CANCELLED
            }
}

/** Запланированное занятие: его можно провести, отменить, перенести и сменить преподавателей. */
interface ScheduledSession : Session {
    /**
     * Отменяет занятие и публикует событие об отмене; отметки журнала перестают учитываться.
     * Если занятие уже не запланировано (изменено параллельно) — ошибка `SESSION_CANNOT_CANCEL`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun cancel(): CancelledSession

    /**
     * Переносит занятие на дату [newDate], время [newStartTime]..[newEndTime] в зал [newHallId].
     * Если занятие уже не запланировано — ошибка `SESSION_CANNOT_RESCHEDULE`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun reschedule(
        newDate: LocalDate,
        newStartTime: LocalTime,
        newEndTime: LocalTime,
        newHallId: HallId,
    ): ScheduledSession

    /**
     * Проводит занятие — закрывает журнал посещаемости и публикует событие с итоговым составом.
     * Ошибки: `SESSION_CANNOT_COMPLETE` (занятие уже не запланировано), `SESSION_NOT_STARTED`
     * (время начала не наступило), `SESSION_JOURNAL_INCOMPLETE` (есть неотмеченные участники).
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun complete(): CompletedSession

    /**
     * Заменяет состав преподавателей занятия.
     * Каждый [Employee] из [employees] должен иметь доступ к филиалу группы,
     * иначе ошибка `EMPLOYEE_NOT_FOUND`; если занятие уже не запланировано —
     * `SESSION_CANNOT_ASSIGN_EMPLOYEES`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun setEmployees(employees: List<Employee>): ScheduledSession
}

/** Проведённое занятие: журнал закрыт, переходов нет. */
interface CompletedSession : Session

/** Отменённое занятие: переходов нет. */
interface CancelledSession : Session

/** Отменяет занятие [this]; не запланированное — ошибка `SESSION_CANNOT_CANCEL`. */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Session.cancel(): CancelledSession = scheduledOr(cannotCancel()).cancel()

/** Переносит занятие [this], см. [ScheduledSession.reschedule]; не запланированное — `SESSION_CANNOT_RESCHEDULE`. */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Session.reschedule(
    newDate: LocalDate,
    newStartTime: LocalTime,
    newEndTime: LocalTime,
    newHallId: HallId,
): ScheduledSession {
    ensureValidSessionTime(newStartTime, newEndTime)
    return scheduledOr(cannotReschedule()).reschedule(newDate, newStartTime, newEndTime, newHallId)
}

/** Проводит занятие [this], см. [ScheduledSession.complete]; не запланированное — `SESSION_CANNOT_COMPLETE`. */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Session.complete(): CompletedSession = scheduledOr(cannotComplete()).complete()

/** Меняет преподавателей занятия [this] на [employees]; не запланированное — `SESSION_CANNOT_ASSIGN_EMPLOYEES`. */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun Session.setEmployees(employees: List<Employee>): ScheduledSession = scheduledOr(cannotAssignEmployees()).setEmployees(employees)

/** Занятие [this] как запланированное; иначе ошибка [error]. */
context(raise: Raise<AppError>)
private fun Session.scheduledOr(error: CommonDomainError): ScheduledSession =
    when (this) {
        is ScheduledSession -> this
        is CompletedSession, is CancelledSession -> raise(error)
    }

/** Ошибка отмены не запланированного занятия. */
internal fun cannotCancel() = CommonDomainError("SESSION_CANNOT_CANCEL", "Можно отменить только запланированное занятие")

/** Ошибка переноса не запланированного занятия. */
internal fun cannotReschedule() = CommonDomainError("SESSION_CANNOT_RESCHEDULE", "Можно перенести только запланированное занятие")

/** Ошибка проведения не запланированного занятия. */
internal fun cannotComplete() = CommonDomainError("SESSION_CANNOT_COMPLETE", "Можно завершить только запланированное занятие")

/** Ошибка назначения преподавателей не запланированному занятию. */
internal fun cannotAssignEmployees() = CommonDomainError("SESSION_CANNOT_ASSIGN_EMPLOYEES", "Можно назначить преподавателей только для запланированного занятия")
