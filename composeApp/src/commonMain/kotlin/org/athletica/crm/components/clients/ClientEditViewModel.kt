package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.EditClientRequest
import org.athletica.crm.core.entityids.ClientId

/** Состояние загрузки данных клиента перед редактированием. */
sealed class ClientEditLoadState {
    /** Загрузка в процессе. */
    data object Loading : ClientEditLoadState()

    /** Данные загружены и форма готова к редактированию. */
    data class Loaded(val client: ClientDetailResponse) : ClientEditLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: ClientsApiError) : ClientEditLoadState()
}

/**
 * ViewModel экрана редактирования клиента.
 * Загружает данные клиента по [clientId], затем обрабатывает сохранение изменений.
 * При успешном сохранении вызывает [onSaved] с обновлёнными данными.
 */
class ClientEditViewModel(
    private val api: ApiClient,
    private val clientId: ClientId,
    private val scope: CoroutineScope,
    private val onSaved: (ClientDetailResponse) -> Unit,
) {
    var loadState by mutableStateOf<ClientEditLoadState>(ClientEditLoadState.Loading)
        private set

    var saveState by mutableStateOf<ClientSaveState>(ClientSaveState.Idle)
        private set

    init {
        load()
    }

    private fun load() {
        scope.launch {
            loadState = ClientEditLoadState.Loading
            api.clients.detail(clientId).fold(
                ifLeft = { loadState = ClientEditLoadState.Error(it.toClientsApiError()) },
                ifRight = { loadState = ClientEditLoadState.Loaded(it) },
            )
        }
    }

    /** Сохраняет изменения формы [form]. */
    fun onSave(form: ClientForm) {
        val client = (loadState as? ClientEditLoadState.Loaded)?.client ?: return
        scope.launch {
            saveState = ClientSaveState.Saving
            api.clients
                .edit(
                    EditClientRequest(
                        id = client.id,
                        name = form.name,
                        avatarId = form.avatarId,
                        birthday = form.birthday,
                        gender = form.gender,
                        leadSourceId = form.leadSourceId,
                    ),
                ).fold(
                    ifLeft = { saveState = ClientSaveState.Error(it.toClientsApiError()) },
                    ifRight = { updated: ClientDetailResponse ->
                        saveState = ClientSaveState.Idle
                        onSaved(updated)
                    },
                )
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onSaveErrorDismissed() {
        saveState = ClientSaveState.Idle
    }
}
