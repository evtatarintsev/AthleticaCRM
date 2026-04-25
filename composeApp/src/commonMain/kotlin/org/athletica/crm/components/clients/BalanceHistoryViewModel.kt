package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.core.entityids.ClientId

/** Состояние шторки истории баланса. */
sealed class BalanceHistoryState {
    /** Загрузка в процессе. */
    data object Loading : BalanceHistoryState()

    /** Записи загружены. */
    data class Loaded(val entries: List<BalanceJournalEntry>) : BalanceHistoryState()

    /** Ошибка загрузки. */
    data class Error(val error: ClientsApiError) : BalanceHistoryState()
}

/**
 * ViewModel шторки истории операций по балансу клиента.
 * Загружает записи журнала через [api] по [clientId].
 */
class BalanceHistoryViewModel(
    private val api: ApiClient,
    private val clientId: ClientId,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<BalanceHistoryState>(BalanceHistoryState.Loading)
        private set

    init {
        load()
    }

    private fun load() {
        scope.launch {
            api.clientBalanceHistory(clientId).fold(
                ifLeft = { state = BalanceHistoryState.Error(it.toClientsApiError()) },
                ifRight = { state = BalanceHistoryState.Loaded(it.entries) },
            )
        }
    }
}
