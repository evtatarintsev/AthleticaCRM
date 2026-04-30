package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.leadSources.CreateLeadSourceRequest
import org.athletica.crm.api.schemas.leadSources.DeleteLeadSourceRequest
import org.athletica.crm.api.schemas.leadSources.UpdateLeadSourceRequest
import org.athletica.crm.core.entityids.LeadSourceId

/** Состояние загрузки списка источников клиентов. */
sealed class ClientSourcesLoadState {
    /** Загрузка в процессе. */
    data object Loading : ClientSourcesLoadState()

    /** Список загружен. */
    data class Loaded(val items: List<DirectoryItem<LeadSourceId>>) : ClientSourcesLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : ClientSourcesLoadState()
}

/** Состояние операции сохранения источника клиента. */
sealed class ClientSourcesSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : ClientSourcesSaveState()

    /** Сохранение выполняется. */
    data object Saving : ClientSourcesSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : ClientSourcesSaveState()
}

/**
 * ViewModel экрана «Источники клиентов».
 * Загружает список, поддерживает создание, редактирование и удаление.
 */
class ClientSourcesViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var loadState by mutableStateOf<ClientSourcesLoadState>(ClientSourcesLoadState.Loading)
        private set

    var saveState by mutableStateOf<ClientSourcesSaveState>(ClientSourcesSaveState.Idle)
        private set

    init {
        load()
    }

    /** Загружает список источников клиентов. */
    fun load() {
        scope.launch {
            loadState = ClientSourcesLoadState.Loading
            api.leadSourceList().fold(
                ifLeft = { loadState = ClientSourcesLoadState.Error(it.toSettingsApiError()) },
                ifRight = { response ->
                    loadState =
                        ClientSourcesLoadState.Loaded(
                            response.leadSources.map { DirectoryItem(id = it.id, name = it.name) },
                        )
                },
            )
        }
    }

    /** Создаёт источник клиента; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onCreate(
        item: DirectoryItem<LeadSourceId>,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            saveState = ClientSourcesSaveState.Saving
            api.createLeadSource(CreateLeadSourceRequest(id = item.id, name = item.name)).fold(
                ifLeft = { saveState = ClientSourcesSaveState.Error(it.toSettingsApiError()) },
                ifRight = {
                    saveState = ClientSourcesSaveState.Idle
                    load()
                    onSuccess()
                },
            )
        }
    }

    /** Обновляет источник клиента; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onUpdate(
        item: DirectoryItem<LeadSourceId>,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            saveState = ClientSourcesSaveState.Saving
            api.updateLeadSource(UpdateLeadSourceRequest(id = item.id, name = item.name)).fold(
                ifLeft = { saveState = ClientSourcesSaveState.Error(it.toSettingsApiError()) },
                ifRight = {
                    saveState = ClientSourcesSaveState.Idle
                    load()
                    onSuccess()
                },
            )
        }
    }

    /** Удаляет источники клиентов по [ids]; при успехе перезагружает список. */
    fun onDelete(ids: Set<LeadSourceId>) {
        scope.launch {
            api.deleteLeadSource(DeleteLeadSourceRequest(ids = ids.toList())).onRight { load() }
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onSaveErrorDismissed() {
        if (saveState is ClientSourcesSaveState.Error) {
            saveState = ClientSourcesSaveState.Idle
        }
    }
}
