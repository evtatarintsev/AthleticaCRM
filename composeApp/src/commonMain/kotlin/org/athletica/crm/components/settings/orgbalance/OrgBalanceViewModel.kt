package org.athletica.crm.components.settings.orgbalance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.orgbalance.OrgBalanceJournalEntry
import org.athletica.crm.components.settings.SettingsApiError
import org.athletica.crm.core.money.Money
import org.athletica.crm.openUrl

/** Состояние загрузки баланса и истории операций. */
sealed interface OrgBalanceLoadState {
    /** Загрузка ещё идёт или не запускалась. */
    data object Loading : OrgBalanceLoadState

    /** Загрузка завершилась ошибкой [error]. */
    data class Error(val error: SettingsApiError) : OrgBalanceLoadState

    /** Данные загружены: текущий баланс [totalAmount] и история операций [history]. */
    data class Loaded(
        val totalAmount: Money,
        val history: List<OrgBalanceJournalEntry>,
    ) : OrgBalanceLoadState
}

/** Состояние процесса инициирования платежа. */
sealed interface OrgBalancePaymentState {
    /** Платёж не инициируется. */
    data object Idle : OrgBalancePaymentState

    /** Идёт обращение к API для получения URL формы оплаты. */
    data object Initiating : OrgBalancePaymentState

    /** URL успешно получен, браузер открыт; диалог должен закрыться. */
    data object Launched : OrgBalancePaymentState

    /** Произошла ошибка при инициировании; [error] нужно показать пользователю. */
    data class Error(val error: SettingsApiError) : OrgBalancePaymentState
}

/**
 * Снимок состояния экрана «Баланс организации».
 * [load] — состояние загрузки данных.
 * [payment] — состояние инициирования платежа.
 */
data class OrgBalanceState(
    val load: OrgBalanceLoadState = OrgBalanceLoadState.Loading,
    val payment: OrgBalancePaymentState = OrgBalancePaymentState.Idle,
)

/**
 * ViewModel экрана «Баланс организации».
 * Загружает баланс и историю через [api]; инициирует платёж через ЮKassa.
 */
class OrgBalanceViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(OrgBalanceState())
        private set

    /** Запрашивает текущий баланс и историю операций с сервера. */
    fun load() {
        scope.launch {
            state = state.copy(load = OrgBalanceLoadState.Loading)
            try {
                api.orgBalance.detail().fold(
                    ifRight = { response ->
                        state =
                            state.copy(
                                load =
                                    OrgBalanceLoadState.Loaded(
                                        totalAmount = response.totalAmount,
                                        history = response.history,
                                    ),
                            )
                    },
                    ifLeft = { error ->
                        state = state.copy(load = OrgBalanceLoadState.Error(SettingsApiError.fromResponse(error)))
                    },
                )
            } catch (e: Exception) {
                state = state.copy(load = OrgBalanceLoadState.Error(SettingsApiError.fromResponse(e)))
            }
        }
    }

    /**
     * Инициирует платёж на сумму [amount].
     * При успехе открывает браузер с URL формы оплаты ЮKassa и переводит [payment] в [OrgBalancePaymentState.Launched].
     * При ошибке переводит [payment] в [OrgBalancePaymentState.Error].
     */
    fun initiatePayment(amount: Money) {
        scope.launch {
            state = state.copy(payment = OrgBalancePaymentState.Initiating)
            try {
                api.payments.initiate(amount, "Пополнение баланса").fold(
                    ifRight = { response ->
                        openUrl(response.confirmationUrl)
                        state = state.copy(payment = OrgBalancePaymentState.Launched)
                    },
                    ifLeft = { error ->
                        state =
                            state.copy(payment = OrgBalancePaymentState.Error(SettingsApiError.fromResponse(error)))
                    },
                )
            } catch (e: Exception) {
                state = state.copy(payment = OrgBalancePaymentState.Error(SettingsApiError.fromResponse(e)))
            }
        }
    }

    /** Сбрасывает состояние платежа в [OrgBalancePaymentState.Idle]. */
    fun clearPaymentState() {
        state = state.copy(payment = OrgBalancePaymentState.Idle)
    }
}
