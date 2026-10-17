package org.athletica.crm.routes

import org.athletica.crm.api.schemas.messaging.AssignChatRequest
import org.athletica.crm.api.schemas.messaging.ClientChannelsRequest
import org.athletica.crm.api.schemas.messaging.ClientChannelsResponse
import org.athletica.crm.api.schemas.messaging.ConversationRequest
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.InboxCounterResponse
import org.athletica.crm.api.schemas.messaging.InboxListRequest
import org.athletica.crm.api.schemas.messaging.InboxListResponse
import org.athletica.crm.api.schemas.messaging.MarkConversationReadRequest
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.api.schemas.messaging.TriageChatMessagesResponse
import org.athletica.crm.api.schemas.messaging.TriageChatRequest
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.clients.Clients
import org.athletica.crm.domain.conversations.ConversationReadStates
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.messagedelivery.Deliveries
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.messaging.InboxQuery
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.messaging.InboundStores
import org.athletica.crm.usecases.messaging.assignChat
import org.athletica.crm.usecases.messaging.conversationView
import org.athletica.crm.usecases.messaging.markConversationRead
import org.athletica.crm.usecases.messaging.sendMessage
import kotlin.time.Instant

/**
 * Регистрирует маршруты диалогов с клиентами, отправки сообщений и приёма/разбора входящих.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.messagingRoutes(
    channels: ChannelIntegrations,
    conversations: Conversations,
    readStates: ConversationReadStates,
    clients: Clients,
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

        post<MarkConversationReadRequest, Unit>("/conversation/read") { request ->
            val upTo =
                Instant.parseOrNull(request.upTo)
                    ?: raise(CommonDomainError("INVALID_TIMESTAMP", Messages.InvalidTimestamp.localize()))
            db.transaction {
                markConversationRead(request.clientId, upTo, clients, conversations, readStates)
            }
        }

        get<InboxListRequest, InboxListResponse>("/inbox/list") { request ->
            db.transaction {
                views.inbox.list(request.toQuery())
            }
        }

        get<Unit, InboxCounterResponse>("/inbox/counter") {
            db.transaction {
                views.inbox.counter()
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

/** Максимальный размер страницы «Входящих». */
private const val MAX_INBOX_PAGE_SIZE = 100

/** Нормализует запрос страницы «Входящих»: ограничивает размер страницы и смещение. */
private fun InboxListRequest.toQuery(): InboxQuery =
    InboxQuery(
        filter = filter,
        limit = limit.coerceIn(1, MAX_INBOX_PAGE_SIZE),
        offset = offset.coerceAtLeast(0),
    )
