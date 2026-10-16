package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.messaging.TriageChatMessagesResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Read-проекция сообщений одного неразобранного чата для страницы «Входящие». */
interface TriageChatView {
    /** Неразобранные сообщения чата [address] интеграции [integrationId] в порядке получения. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun messages(integrationId: ChannelIntegrationId, address: String): TriageChatMessagesResponse
}
