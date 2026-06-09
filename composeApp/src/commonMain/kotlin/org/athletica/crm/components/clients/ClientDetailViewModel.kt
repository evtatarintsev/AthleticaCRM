package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ArchiveClientRequest
import org.athletica.crm.api.schemas.clients.AttachClientDocRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.DeleteClientDocRequest
import org.athletica.crm.api.schemas.clients.RemoveClientFromGroupRequest
import org.athletica.crm.api.schemas.clients.RestoreClientRequest
import org.athletica.crm.api.schemas.memberships.MembershipListRequest
import org.athletica.crm.api.schemas.memberships.MembershipSchema
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
    data class Loaded(
        val client: ClientDetailResponse,
        /** Абонементы клиента, новые сверху; пусто, пока грузятся или их нет. */
        val memberships: List<MembershipSchema> = emptyList(),
    ) : ClientDetailState()
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

    /** Загружает (или перезагружает) данные клиента и его абонементы. */
    fun load() {
        scope.launch {
            state = ClientDetailState.Loading
            api.clients.detail(clientId).fold(
                ifLeft = { state = ClientDetailState.Error(it.toClientsApiError()) },
                ifRight = {
                    state = ClientDetailState.Loaded(it)
                    loadMemberships()
                },
            )
        }
    }

    /** Догружает абонементы клиента и кладёт их в текущее загруженное состояние. */
    private fun loadMemberships() {
        scope.launch {
            api.memberships.list(MembershipListRequest(clientId)).onRight { response ->
                val loaded = state as? ClientDetailState.Loaded ?: return@onRight
                state = loaded.copy(memberships = response.memberships)
            }
        }
    }

    /** Обновляет клиента данными из [client] (например, после корректировки баланса), сохраняя абонементы. */
    fun onClientUpdated(client: ClientDetailResponse) {
        val memberships = (state as? ClientDetailState.Loaded)?.memberships ?: emptyList()
        state = ClientDetailState.Loaded(client, memberships)
    }

    /** Отправляет клиента в архив и перезагружает карточку. */
    fun onArchive() {
        scope.launch {
            api.clients.archive(ArchiveClientRequest(listOf(clientId))).onRight { load() }
        }
    }

    /** Восстанавливает клиента из архива и перезагружает карточку. */
    fun onRestore() {
        scope.launch {
            api.clients.restore(RestoreClientRequest(listOf(clientId))).onRight { load() }
        }
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
