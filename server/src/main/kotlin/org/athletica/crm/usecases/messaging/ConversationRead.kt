package org.athletica.crm.usecases.messaging

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.conversations.ConversationReadStates
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Отмечает беседу с клиентом [clientId] прочитанной до момента [upTo] — времени последнего сообщения,
 * которое сотрудник увидел. Прочитанность общая для организации; горизонт только растёт.
 * Если беседы у клиента ещё нет, ничего не делает. `CLIENT_NOT_FOUND`, если клиента нет в организации.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun markConversationRead(
    clientId: ClientId,
    upTo: Instant,
    clients: Clients,
    conversations: Conversations,
    readStates: ConversationReadStates,
) {
    clients.byId(clientId)
    val conversation = conversations.byClient(clientId) ?: return
    readStates.markRead(conversation.id, upTo)
}
