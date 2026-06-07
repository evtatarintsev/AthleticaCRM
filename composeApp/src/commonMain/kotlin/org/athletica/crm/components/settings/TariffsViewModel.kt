package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.tariffs.ArchiveTariffPlanRequest
import org.athletica.crm.api.schemas.tariffs.CreateTariffPlanRequest
import org.athletica.crm.api.schemas.tariffs.TariffPlanListRequest
import org.athletica.crm.api.schemas.tariffs.TariffPlanSchema
import org.athletica.crm.api.schemas.tariffs.UpdateTariffPlanRequest
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Currency

/** Состояние загрузки тарифов. */
sealed interface TariffsData {
    /** Загрузка в процессе. */
    data object Loading : TariffsData

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : TariffsData

    /** Тарифы загружены вместе с валютой организации (нужна для формы создания). */
    data class Loaded(val items: List<TariffPlanSchema>, val currency: Currency) : TariffsData
}

/** Состояние операции сохранения тарифа. */
sealed interface TariffsSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : TariffsSaveState

    /** Сохранение выполняется. */
    data object Saving : TariffsSaveState

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : TariffsSaveState
}

/** Снимок состояния экрана тарифов: данные списка и статус сохранения. */
data class TariffsState(
    val data: TariffsData = TariffsData.Loading,
    val save: TariffsSaveState = TariffsSaveState.Idle,
)

/**
 * ViewModel экрана «Тарифы абонементов».
 *
 * Держит одну ячейку состояния [state]. Загружает валюту организации и список тарифов
 * (включая архивные), поддерживает создание, редактирование и архивирование/восстановление.
 */
class TariffsViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf(TariffsState())
        private set

    init {
        load()
    }

    /** Загружает валюту организации и список тарифов (вместе с архивными). */
    fun load() {
        scope.launch {
            state = state.copy(data = TariffsData.Loading)
            api.org.settings().fold(
                ifLeft = { state = state.copy(data = TariffsData.Error(it.toSettingsApiError())) },
                ifRight = { settings ->
                    api.tariffs.list(TariffPlanListRequest(includeArchived = true)).fold(
                        ifLeft = { state = state.copy(data = TariffsData.Error(it.toSettingsApiError())) },
                        ifRight = { response ->
                            state = state.copy(data = TariffsData.Loaded(response.tariffs, settings.currency))
                        },
                    )
                },
            )
        }
    }

    /** Создаёт тариф; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onCreate(
        request: CreateTariffPlanRequest,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            state = state.copy(save = TariffsSaveState.Saving)
            api.tariffs.create(request).fold(
                ifLeft = { state = state.copy(save = TariffsSaveState.Error(it.toSettingsApiError())) },
                ifRight = {
                    state = state.copy(save = TariffsSaveState.Idle)
                    onSuccess()
                    load()
                },
            )
        }
    }

    /** Изменяет тариф; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onUpdate(
        request: UpdateTariffPlanRequest,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            state = state.copy(save = TariffsSaveState.Saving)
            api.tariffs.update(request).fold(
                ifLeft = { state = state.copy(save = TariffsSaveState.Error(it.toSettingsApiError())) },
                ifRight = {
                    state = state.copy(save = TariffsSaveState.Idle)
                    onSuccess()
                    load()
                },
            )
        }
    }

    /** Архивирует или восстанавливает тариф [id]; при успехе перезагружает список. */
    fun onArchive(
        id: TariffPlanId,
        archived: Boolean,
    ) {
        scope.launch {
            api.tariffs.archive(ArchiveTariffPlanRequest(id = id, archived = archived)).onRight { load() }
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onSaveErrorDismissed() {
        if (state.save is TariffsSaveState.Error) {
            state = state.copy(save = TariffsSaveState.Idle)
        }
    }
}
