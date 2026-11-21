package org.athletica.crm.read.auth

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.auth.SessionListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Duration
import kotlin.time.Instant

/** Read-проекция активных сеансов сотрудника для профиля и карточки сотрудника. */
interface SessionListView {
    /**
     * Сеансы по запросу [query], от недавних к давним; сеанс запроса отмечен текущим.
     * Ошибка `EMPLOYEE_NOT_FOUND`, если сотрудника нет в организации.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun list(query: SessionListQuery): SessionListResponse
}

/** Запрос сеансов. */
data class SessionListQuery(
    /** Сотрудник, чьи сеансы нужны; для своего профиля — сотрудник запроса. */
    val employeeId: EmployeeId,
    /** Момент запроса. */
    val now: Instant,
    /** Сеансы без активности дольше этого срока не показываются. */
    val maxIdle: Duration,
)
