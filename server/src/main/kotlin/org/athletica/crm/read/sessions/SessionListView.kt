package org.athletica.crm.read.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.sessions.SessionListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция списка занятий за период: занятия с названием группы и составом тренеров.
 * Ничего не создаёт и не изменяет — материализацией занятий владеет
 * [org.athletica.crm.domain.sessions.ScheduleSync].
 */
interface SessionListView {
    /** Возвращает занятия, удовлетворяющие [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(query: SessionListQuery): SessionListResponse
}

/** Параметры выборки занятий; нормализация запроса — работа маршрута. */
data class SessionListQuery(
    /** Первый день периода, включительно. */
    val from: LocalDate,
    /** Последний день периода, включительно. */
    val to: LocalDate,
    /** Группа, занятия которой запрошены; `null` — все группы организации. */
    val groupId: GroupId? = null,
)
