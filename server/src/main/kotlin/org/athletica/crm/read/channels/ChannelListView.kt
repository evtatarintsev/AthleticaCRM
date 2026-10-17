package org.athletica.crm.read.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция списка интеграций каналов для раздела настроек «Каналы».
 * Секреты провайдера не отдаёт: токены — только маской, пароли — никак. Ссылка для раздачи
 * клиентам доступна без секретов, поэтому список можно показывать любому сотруднику.
 */
interface ChannelListView {
    /** Возвращает интеграции текущей организации в порядке создания. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): ChannelListResponse
}
