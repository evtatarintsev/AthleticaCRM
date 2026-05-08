package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.core.customfields.CustomFieldDefinition

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
 * Загружает список клиентов и доступные кастомные поля для динамического отображения колонок.
 */
class ClientsViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<ClientsState>(ClientsState.Loading)
        private set

    /**
     * Список доступных кастомных полей для клиентов.
     * Используется при конвертации сохранённых настроек отображения.
     */
    var availableCustomFields by mutableStateOf<List<CustomFieldDefinition>>(emptyList())
        private set

    /** Перезагружает список клиентов и кастомные поля. */
    fun load() {
        scope.launch {
            state = ClientsState.Loading
            // Параллельно загружаем клиентов и кастомные поля
            val clientsResult = api.clients.list(ClientListRequest())
            val customFieldsResult = api.customFields.list("CLIENT")

            clientsResult.fold(
                ifLeft = { state = ClientsState.Error(it.toClientsApiError()) },
                ifRight = { state = ClientsState.Loaded(it.clients) },
            )

            customFieldsResult.fold(
                ifLeft = { /* ошибка загрузки кастомных полей — продолжаем без них */ },
                ifRight = { availableCustomFields = it },
            )
        }
    }
}
