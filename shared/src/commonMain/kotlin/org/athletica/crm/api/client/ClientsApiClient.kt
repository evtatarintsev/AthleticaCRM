package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.core.entityids.ClientId

class ClientsApiClient(private val http: HttpClient) {
    /** Возвращает страницу клиентов организации по параметрам [request]. */
    suspend fun list(request: ClientListRequest): Either<ApiClientError, ClientListResponse> = requestCatching { http.get("/api/clients/list") }

    /** Экспортирует список клиентов в CSV формате. */
    suspend fun export(request: ClientListRequest, format: String = "csv"): Either<ApiClientError, ByteArray> =
        requestCatching {
            http.post("/api/clients/export") {
                contentType(ContentType.Application.Json)
                url {
                    parameters.append("format", format)
                }
                setBody(request)
            }
        }

    /** Возвращает полные данные клиента по [id]. */
    suspend fun detail(id: ClientId): Either<ApiClientError, ClientDetailResponse> =
        requestCatching {
            http.get("/api/clients/detail") {
                url { parameters.append("id", id.toString()) }
            }
        }

    /** Создаёт нового клиента по данным [request]. Возвращает созданного клиента. */
    suspend fun create(request: CreateClientRequest): Either<ApiClientError, ClientDetailResponse> =
        requestCatching {
            http.post("/api/clients/create") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Редактирует существующего клиента по данным [request]. Возвращает обновлённого клиента. */
    suspend fun edit(request: EditClientRequest): Either<ApiClientError, ClientDetailResponse> =
        requestCatching {
            http.post("/api/clients/edit") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Добавляет список клиентов из [request] в группу. */
    suspend fun addToGroup(request: AddClientsToGroupRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/clients/add-to-group") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет клиента из группы по данным [request]. */
    suspend fun removeFromGroup(request: RemoveClientFromGroupRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/clients/remove-from-group") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает историю операций по балансу клиента с [id], отсортированную от новых к старым. */
    suspend fun balanceHistory(id: ClientId): Either<ApiClientError, ClientBalanceHistoryResponse> =
        requestCatching {
            http.get("/api/clients/balance/history") {
                url { parameters.append("id", id.toString()) }
            }
        }

    /** Выполняет административную корректировку баланса клиента. Возвращает обновлённые данные клиента. */
    suspend fun adjustBalance(request: AdjustBalanceRequest): Either<ApiClientError, ClientDetailResponse> =
        requestCatching {
            http.post("/api/clients/balance/adjust") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет документ [docId], прикреплённый к клиенту [clientId]. */
    suspend fun deleteDoc(request: DeleteClientDocRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/clients/docs/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Прикрепляет загруженный файл к клиенту как документ. */
    suspend fun attachDoc(request: AttachClientDocRequest): Either<ApiClientError, Unit> =
        requestCatching {
            http.post("/api/clients/docs/attach") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
