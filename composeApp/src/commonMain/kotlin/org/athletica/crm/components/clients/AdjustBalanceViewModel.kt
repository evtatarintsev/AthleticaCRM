package org.athletica.crm.components.clients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.core.entityids.ClientId

/** Состояние диалога корректировки баланса. */
sealed class AdjustBalanceState {
    /** Ожидание отправки. */
    data object Idle : AdjustBalanceState()

    /** Запрос выполняется. */
    data object Submitting : AdjustBalanceState()

    /** Сервер вернул ошибку. */
    data class Error(val error: ClientsApiError) : AdjustBalanceState()
}

/**
 * ViewModel диалога корректировки баланса клиента.
 * Отправляет запрос на [api] и вызывает [onSuccess] с обновлёнными данными.
 */
class AdjustBalanceViewModel(
    private val api: ApiClient,
    private val clientId: ClientId,
    private val scope: CoroutineScope,
    private val onSuccess: (ClientDetailResponse) -> Unit,
) {
    var state by mutableStateOf<AdjustBalanceState>(AdjustBalanceState.Idle)
        private set

    /**
     * Отправляет корректировку на [amount] с комментарием [note].
     * Знак [amount] задаёт направление: положительный — пополнение, отрицательный — списание.
     */
    fun onSubmit(amount: Double, note: String) {
        scope.launch {
            state = AdjustBalanceState.Submitting
            api
                .adjustClientBalance(AdjustBalanceRequest(clientId, amount, note))
                .fold(
                    ifLeft = { state = AdjustBalanceState.Error(it.toClientsApiError()) },
                    ifRight = { updated ->
                        state = AdjustBalanceState.Idle
                        onSuccess(updated)
                    },
                )
        }
    }

    /** Сбрасывает ошибку. */
    fun onErrorDismissed() {
        state = AdjustBalanceState.Idle
    }
}
