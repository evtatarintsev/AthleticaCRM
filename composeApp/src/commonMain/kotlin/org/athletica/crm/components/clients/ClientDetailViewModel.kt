package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.core.entityids.ClientDocId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.openUrl
import org.athletica.crm.pickAnyFile

/** Состояние экрана карточки клиента. */
sealed class ClientDetailState {
    /** Загрузка данных клиента. */
    data object Loading : ClientDetailState()

    /** Ошибка загрузки. */
    data class Error(val error: ClientsApiError) : ClientDetailState()

    /** Данные клиента загружены. */
    data class Loaded(val client: ClientDetailResponse) : ClientDetailState()
}

/**
 * ViewModel экрана карточки клиента.
 * Загружает данные по [clientId] и управляет операциями над документами и группами.
 */
class ClientDetailViewModel(
    private val api: ApiClient,
    private val clientId: ClientId,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<ClientDetailState>(ClientDetailState.Loading)
        private set

    var isUploadingDoc by mutableStateOf(false)
        private set

    init {
        load()
    }

    /** Загружает (или перезагружает) данные клиента. */
    fun load() {
        scope.launch {
            state = ClientDetailState.Loading
            api.clients.detail(clientId).fold(
                ifLeft = { state = ClientDetailState.Error(it.toClientsApiError()) },
                ifRight = { state = ClientDetailState.Loaded(it) },
            )
        }
    }

    /** Обновляет клиента данными из [client] (например, после корректировки баланса). */
    fun onClientUpdated(client: ClientDetailResponse) {
        state = ClientDetailState.Loaded(client)
    }

    /** Удаляет клиента из группы [groupId] и перезагружает карточку. */
    fun onRemoveFromGroup(groupId: GroupId) {
        scope.launch {
            api.clients.removeFromGroup(RemoveClientFromGroupRequest(listOf(clientId), groupId))
            load()
        }
    }

    /** Открывает системный файл-пикер, загружает файл и прикрепляет документ к клиенту. */
    fun onUploadDoc() {
        scope.launch {
            isUploadingDoc = true
            val file = pickAnyFile()
            if (file != null) {
                api.documents.upload(file.first, file.second, file.third).onRight { upload ->
                    val docId = ClientDocId.new()
                    api.clients
                        .attachDoc(
                            AttachClientDocRequest(
                                docId,
                                clientId = clientId,
                                uploadId = upload.id,
                                name = upload.originalName,
                            ),
                        ).onRight { load() }
                }
            }
            isUploadingDoc = false
        }
    }

    /** Удаляет документ [docId] и перезагружает карточку. */
    fun onDeleteDoc(docId: ClientDocId) {
        scope.launch {
            api.clients.deleteDoc(DeleteClientDocRequest(clientId, docId)).onRight { load() }
        }
    }

    /** Открывает документ [uploadId] по URL для просмотра / скачивания. */
    fun onShareDoc(uploadId: UploadId) {
        scope.launch {
            api.documents.info(uploadId).onRight { openUrl(it.url) }
        }
    }
}
