package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.core.entityids.ClientId

/** Клиент API для диалогов с клиентами и отправки сообщений. */
class MessagingApiClient(private val http: HttpClient) {
    /** Возвращает ленту диалога с клиентом [clientId] (сообщения по всем каналам). */
    suspend fun conversation(clientId: ClientId): Either<ApiClientError, ConversationResponse> =
        requestCatching {
            http.get("/api/messaging/conversation") {
                url { parameters.append("clientId", clientId.toString()) }
            }
        }

    /** Ставит сообщение в очередь на отправку клиенту. Возвращает обновлённую ленту диалога. */
    suspend fun send(request: SendMessageRequest): Either<ApiClientError, ConversationResponse> =
        requestCatching {
            http.post("/api/messaging/send") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
