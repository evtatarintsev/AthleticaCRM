package org.athletica.crm.read.attendance

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.attendance.GroupAttendanceResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция сводки посещаемости группы за период.
 * Учитывает только проведённые занятия: незакрытый журнал — черновик, отменённое занятие
 * аннулирует свои отметки.
 */
interface GroupAttendanceView {
    /** Сводка по группе и периоду из [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun summary(query: GroupAttendanceQuery): GroupAttendanceResponse
}

/** Параметры сводки по группе; период проверен маршрутом. */
data class GroupAttendanceQuery(
    /** Группа. */
    val groupId: GroupId,
    /** Первый день периода включительно. */
    val from: LocalDate,
    /** Последний день периода включительно. */
    val to: LocalDate,
)
