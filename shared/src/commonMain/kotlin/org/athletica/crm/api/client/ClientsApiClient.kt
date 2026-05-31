package org.athletica.crm.api.client

import arrow.core.Either
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.athletica.crm.api.schemas.clients.AddClientContactRequest
import org.athletica.crm.api.schemas.clients.AddClientNoteRequest
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.api.schemas.clients.ClientContactListResponse
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.ClientExportRequest
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.ClientNotesListResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.api.schemas.clients.DeleteClientContactRequest
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.DeleteClientNoteRequest
import org.athletica.crm.api.schemas.clients.EditClientNoteRequest
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportCommitResponse
import org.athletica.crm.api.schemas.clients.import.ClientImportParseRequest
import org.athletica.crm.api.schemas.clients.import.ClientImportParseResponse
import org.athletica.crm.core.entityids.ClientId

class ClientsApiClient(private val http: HttpClient) {
    /** Возвращает страницу клиентов организации по параметрам [request]. */
    suspend fun list(request: ClientListRequest): Either<ApiClientError, ClientListResponse> = requestCatching { http.get("/api/clients/list") }

    /** Экспортирует список клиентов в файл указанного [format] с полями из [request]. */
    suspend fun export(
        request: ClientExportRequest,
        format: String = "csv",
    ): Either<ApiClientError, ByteArray> =
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

    /** Возвращает список не удалённых заметок клиента [id] в порядке от новых к старым. */
    suspend fun notesList(id: ClientId): Either<ApiClientError, ClientNotesListResponse> =
        requestCatching {
            http.get("/api/clients/notes/list") {
                url { parameters.append("clientId", id.toString()) }
            }
        }

    /** Создаёт новую заметку клиенту. Возвращает обновлённый список заметок. */
    suspend fun addNote(request: AddClientNoteRequest): Either<ApiClientError, ClientNotesListResponse> =
        requestCatching {
            http.post("/api/clients/notes/add") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Изменяет текст заметки. Доступно только её автору; возвращает обновлённый список. */
    suspend fun editNote(request: EditClientNoteRequest): Either<ApiClientError, ClientNotesListResponse> =
        requestCatching {
            http.post("/api/clients/notes/edit") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет заметку (soft-delete). Доступно только её автору; возвращает обновлённый список. */
    suspend fun deleteNote(request: DeleteClientNoteRequest): Either<ApiClientError, ClientNotesListResponse> =
        requestCatching {
            http.post("/api/clients/notes/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Разбирает ранее загруженный CSV: возвращает заголовки, образцы и уникальные значения колонок. */
    suspend fun importParse(request: ClientImportParseRequest): Either<ApiClientError, ClientImportParseResponse> =
        requestCatching {
            http.post("/api/clients/import/parse") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Валидирует или фактически выполняет импорт клиентов согласно маппингу. */
    suspend fun importCommit(request: ClientImportCommitRequest): Either<ApiClientError, ClientImportCommitResponse> =
        requestCatching {
            http.post("/api/clients/import/commit") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Возвращает список контактов клиента [id] по каналам связи. */
    suspend fun contactsList(id: ClientId): Either<ApiClientError, ClientContactListResponse> =
        requestCatching {
            http.get("/api/clients/contacts/list") {
                url { parameters.append("clientId", id.toString()) }
            }
        }

    /** Добавляет клиенту контакт в рамках канала. Возвращает обновлённый список контактов. */
    suspend fun addContact(request: AddClientContactRequest): Either<ApiClientError, ClientContactListResponse> =
        requestCatching {
            http.post("/api/clients/contacts/add") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** Удаляет контакт клиента. Возвращает обновлённый список контактов. */
    suspend fun deleteContact(request: DeleteClientContactRequest): Either<ApiClientError, ClientContactListResponse> =
        requestCatching {
            http.post("/api/clients/contacts/delete") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
