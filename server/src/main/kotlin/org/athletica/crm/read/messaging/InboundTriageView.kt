package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.messaging.TriageListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция очереди ручного разбора входящих (триаж): неразобранные записи инбокса
 * текущей организации.
 */
interface InboundTriageView {
    /** Возвращает неразобранные входящие в порядке поступления — от старых к новым. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): TriageListResponse
}
