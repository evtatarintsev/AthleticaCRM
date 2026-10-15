package org.athletica.crm.read.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция списка интеграций каналов для раздела настроек «Каналы».
 * Отдаёт `config` целиком (включая секреты провайдера) — он нужен форме редактирования,
 * поэтому доменный объект отправки [org.athletica.crm.domain.channels.ChannelIntegration] его не раскрывает.
 */
interface ChannelListView {
    /** Возвращает интеграции текущей организации в порядке создания. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): ChannelListResponse
}
