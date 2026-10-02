package org.athletica.crm.read.attendance

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.attendance.ClientAttendanceResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция посещаемости клиента за период по всем группам.
 * Учитывает только проведённые занятия.
 */
interface ClientAttendanceView {
    /** Отметки клиента по периоду из [query], по дате и времени занятия. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(query: ClientAttendanceQuery): ClientAttendanceResponse
}

/** Параметры посещаемости клиента; период проверен маршрутом. */
data class ClientAttendanceQuery(
    /** Клиент. */
    val clientId: ClientId,
    /** Первый день периода включительно. */
    val from: LocalDate,
    /** Последний день периода включительно. */
    val to: LocalDate,
)
