package org.athletica.crm.routes

import org.athletica.crm.api.schemas.messaging.ConversationRequest
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.messaging.conversationView
import org.athletica.crm.usecases.messaging.sendMessage

/**
 * Регистрирует маршруты диалогов с клиентами и отправки сообщений.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.messagingRoutes(
    channels: ChannelIntegrations,
    contacts: ClientContacts,
    conversations: Conversations,
) {
    route("/messaging") {
        get<ConversationRequest, ConversationResponse>("/conversation") { request ->
            db.transaction {
                conversationView(request.clientId, conversations)
            }
        }

        post<SendMessageRequest, ConversationResponse>("/send") { request ->
            db.transaction {
                sendMessage(request, channels, contacts, conversations)
            }
        }
    }
}
