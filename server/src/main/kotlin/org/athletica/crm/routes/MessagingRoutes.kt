package org.athletica.crm.routes

import org.athletica.crm.api.schemas.messaging.AssignChatRequest
import org.athletica.crm.api.schemas.messaging.ClientChannelsRequest
import org.athletica.crm.api.schemas.messaging.ClientChannelsResponse
import org.athletica.crm.api.schemas.messaging.ConversationRequest
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.api.schemas.messaging.TriageChatMessagesResponse
import org.athletica.crm.api.schemas.messaging.TriageChatRequest
import org.athletica.crm.api.schemas.messaging.TriageListResponse
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.messagedelivery.Deliveries
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.messaging.InboundStores
import org.athletica.crm.usecases.messaging.assignChat
import org.athletica.crm.usecases.messaging.conversationView
import org.athletica.crm.usecases.messaging.sendMessage

/**
 * Регистрирует маршруты диалогов с клиентами, отправки сообщений и приёма/разбора входящих.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.messagingRoutes(
    channels: ChannelIntegrations,
    conversations: Conversations,
    deliveries: Deliveries,
    inbound: InboundStores,
    views: ReadViews,
) {
    route("/messaging") {
        get<ConversationRequest, ConversationResponse>("/conversation") { request ->
            db.transaction {
                conversationView(request.clientId, conversations, deliveries)
            }
        }

        get<ClientChannelsRequest, ClientChannelsResponse>("/channels") { request ->
            db.transaction {
                views.clientChannels.list(request.clientId)
            }
        }

        post<SendMessageRequest, ConversationResponse>("/send") { request ->
            db.transaction {
                sendMessage(request, channels, conversations, deliveries)
            }
        }

        get<Unit, TriageListResponse>("/triage/list") {
            db.transaction {
                views.inboundTriage.list()
            }
        }

        get<TriageChatRequest, TriageChatMessagesResponse>("/triage/chat") { request ->
            db.transaction {
                views.triageChat.messages(request.integrationId, request.address)
            }
        }

        post<AssignChatRequest, Unit>("/triage/assign") { request ->
            db.transaction {
                assignChat(request.integrationId, request.address, request.clientId, inbound)
            }
        }
    }
}
