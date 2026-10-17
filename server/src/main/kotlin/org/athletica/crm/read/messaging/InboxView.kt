package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.messaging.InboxCounterResponse
import org.athletica.crm.api.schemas.messaging.InboxFilterSchema
import org.athletica.crm.api.schemas.messaging.InboxListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Нормализованный запрос страницы «Входящих». */
data class InboxQuery(
    /** Фильтр списка. */
    val filter: InboxFilterSchema,
    /** Размер страницы. */
    val limit: Int,
    /** Смещение от начала отфильтрованной выборки. */
    val offset: Int,
)

/**
 * Read-проекция раздела «Входящие»: беседы клиентов организации и неразобранные чаты
 * в одной ленте, свежие первыми, с непрочитанным.
 */
interface InboxView {
    /** Возвращает страницу списка по [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(query: InboxQuery): InboxListResponse

    /** Возвращает число записей, требующих внимания: беседы с непрочитанным и неразобранные чаты. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun counter(): InboxCounterResponse
}
