package org.athletica.crm.components.settings.orgbalance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.orgbalance.OrgBalanceJournalEntry
import org.athletica.crm.components.settings.SettingsApiError

/** Состояние загрузки экрана баланса организации. */
sealed interface OrgBalanceLoadState {
    /** Загрузка ещё идёт или не запускалась. */
    data object Loading : OrgBalanceLoadState

    /** Загрузка завершилась ошибкой [error]. */
    data class Error(val error: SettingsApiError) : OrgBalanceLoadState

    /** Данные загружены: текущий баланс [totalAmount] и история операций [history]. */
    data class Loaded(
        val totalAmount: Double,
        val history: List<OrgBalanceJournalEntry>,
    ) : OrgBalanceLoadState
}

/**
 * ViewModel экрана «Баланс организации».
 * Загружает баланс и историю операций через [api].
 */
class OrgBalanceViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var loadState by mutableStateOf<OrgBalanceLoadState>(OrgBalanceLoadState.Loading)
        private set

    /** Запрашивает баланс и историю операций с сервера. */
    fun load() {
        scope.launch {
            loadState = OrgBalanceLoadState.Loading
            try {
                api.orgBalance.detail().fold(
                    ifRight = { response ->
                        loadState =
                            OrgBalanceLoadState.Loaded(
                                totalAmount = response.totalAmount,
                                history = response.history,
                            )
                    },
                    ifLeft = { error ->
                        loadState = OrgBalanceLoadState.Error(SettingsApiError.fromResponse(error))
                    },
                )
            } catch (e: Exception) {
                loadState = OrgBalanceLoadState.Error(SettingsApiError.fromResponse(e))
            }
        }
    }
}
