package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.core.entityids.ClientId

/**
 * ViewModel экрана создания клиента.
 * Отправляет запрос создания через [api] и вызывает [onCreated] при успехе.
 */
class ClientCreateViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onCreated: () -> Unit,
) {
    var state by mutableStateOf<ClientSaveState>(ClientSaveState.Idle)
        private set

    /** Создаёт клиента по данным [form]. */
    fun onCreate(form: ClientForm) {
        scope.launch {
            state = ClientSaveState.Saving
            api.clients
                .create(
                    CreateClientRequest(
                        id = ClientId.new(),
                        name = form.name,
                        avatarId = form.avatarId,
                        birthday = form.birthday,
                        gender = form.gender,
                        leadSourceId = form.leadSourceId,
                    ),
                ).fold(
                    ifLeft = { state = ClientSaveState.Error(it.toClientsApiError()) },
                    ifRight = {
                        state = ClientSaveState.Idle
                        onCreated()
                    },
                )
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onErrorDismissed() {
        state = ClientSaveState.Idle
    }
}
