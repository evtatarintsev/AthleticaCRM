package org.athletica.crm.read.clients

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция журнала операций по личному счёту клиента.
 *
 * Имя сотрудника, выполнившего операцию, подставляется join'ом: доменная запись
 * журнала хранит только [org.athletica.crm.core.entityids.EmployeeId], а сборка
 * читаемого представления — задача read-слоя.
 */
interface ClientBalanceHistoryView {
    /**
     * Возвращает операции клиента [clientId] от новых к старым.
     * Ошибка, если клиент не найден в организации.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byClient(clientId: ClientId): ClientBalanceHistoryResponse
}
