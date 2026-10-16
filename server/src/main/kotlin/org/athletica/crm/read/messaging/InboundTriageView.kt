package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.messaging.TriageListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция очереди ручного разбора: неразобранные чаты связанных каналов текущей организации,
 * по одной записи на чат.
 */
interface InboundTriageView {
    /** Возвращает неразобранные чаты, отсортированные по последнему сообщению — новые первыми. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): TriageListResponse
}
