package org.athletica.crm.routes

import org.athletica.crm.api.schemas.messaging.AssignInboundRequest
import org.athletica.crm.api.schemas.messaging.ConversationRequest
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.ReceiveInboundRequest
import org.athletica.crm.api.schemas.messaging.ReceiveInboundResponse
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.api.schemas.messaging.TriageListResponse
import org.athletica.crm.domain.channels.ChannelBindings
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.inbound.InboundReceipts
import org.athletica.crm.domain.messagedelivery.Deliveries
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.messaging.assignInbound
import org.athletica.crm.usecases.messaging.conversationView
import org.athletica.crm.usecases.messaging.receiveInbound
import org.athletica.crm.usecases.messaging.sendMessage

/**
 * Регистрирует маршруты диалогов с клиентами, отправки сообщений и приёма/разбора входящих.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.messagingRoutes(
    channels: ChannelIntegrations,
    contacts: ClientContacts,
    conversations: Conversations,
    deliveries: Deliveries,
    bindings: ChannelBindings,
    receipts: InboundReceipts,
    views: ReadViews,
) {
    route("/messaging") {
        get<ConversationRequest, ConversationResponse>("/conversation") { request ->
            db.transaction {
                conversationView(request.clientId, conversations, deliveries, contacts)
            }
        }

        post<SendMessageRequest, ConversationResponse>("/send") { request ->
            db.transaction {
                sendMessage(request, channels, contacts, conversations, deliveries)
            }
        }

        post<ReceiveInboundRequest, ReceiveInboundResponse>("/inbound") { request ->
            db.transaction {
                receiveInbound(request, bindings, receipts, conversations)
            }
        }

        get<Unit, TriageListResponse>("/triage/list") {
            db.transaction {
                views.inboundTriage.list()
            }
        }

        post<AssignInboundRequest, Unit>("/triage/assign") { request ->
            db.transaction {
                assignInbound(request.receiptId, request.clientId, bindings, receipts, conversations)
            }
        }
    }
}
