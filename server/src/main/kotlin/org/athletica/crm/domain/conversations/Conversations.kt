package org.athletica.crm.domain.conversations

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Репозиторий диалогов с клиентами. Все операции изолированы по организации. */
interface Conversations {
    /**
     * Возвращает диалог клиента [clientId], создавая его при первом обращении (get-or-create).
     * Гарантирует единственность диалога на клиента (UNIQUE org_id, client_id).
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun conversationFor(clientId: ClientId): Conversation

    /** Обновляет момент последнего сообщения диалога [conversationId] на «сейчас». */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun touch(conversationId: ConversationId)
}
