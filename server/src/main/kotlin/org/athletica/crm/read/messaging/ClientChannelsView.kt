package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.messaging.ClientChannelsResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция каналов для страницы переписки: включённые каналы организации и то, можно ли через
 * каждый написать клиенту. Доступность считает сервер по способу адресации вида канала
 * ([org.athletica.crm.core.messaging.ChannelAddressing]), чтобы фронт не дублировал это правило.
 * Настройки провайдеров (секреты) в ответ не попадают.
 */
interface ClientChannelsView {
    /** Возвращает включённые каналы с доступностью для клиента [clientId]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(clientId: ClientId): ClientChannelsResponse
}
