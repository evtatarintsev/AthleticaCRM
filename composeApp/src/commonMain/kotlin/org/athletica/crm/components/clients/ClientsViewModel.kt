package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest

/** Состояние загрузки списка клиентов. */
sealed class ClientsState {
    /** Загрузка в процессе. */
    data object Loading : ClientsState()

    /** Список клиентов загружен. */
    data class Loaded(val clients: List<ClientListItem>) : ClientsState()

    /** Ошибка загрузки. */
    data class Error(val error: ClientsApiError) : ClientsState()
}

/**
 * ViewModel экрана списка клиентов.
 * Загружает список через [api] и обновляет [state].
 */
class ClientsViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<ClientsState>(ClientsState.Loading)
        private set

    init {
        load()
    }

    /** Перезагружает список клиентов. */
    fun load() {
        scope.launch {
            state = ClientsState.Loading
            api.clientList(ClientListRequest()).fold(
                ifLeft = { state = ClientsState.Error(it.toClientsApiError()) },
                ifRight = { state = ClientsState.Loaded(it.clients) },
            )
        }
    }
}
