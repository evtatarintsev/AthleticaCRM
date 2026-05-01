package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.halls.CreateHallRequest
import org.athletica.crm.api.schemas.halls.DeleteHallRequest
import org.athletica.crm.api.schemas.halls.UpdateHallRequest
import org.athletica.crm.core.entityids.HallId

/** Состояние загрузки списка залов. */
sealed class HallsLoadState {
    /** Загрузка в процессе. */
    data object Loading : HallsLoadState()

    /** Список загружен. */
    data class Loaded(val items: List<DirectoryItem<HallId>>) : HallsLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : HallsLoadState()
}

/** Состояние операции сохранения зала. */
sealed class HallsSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : HallsSaveState()

    /** Сохранение выполняется. */
    data object Saving : HallsSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : HallsSaveState()
}

/**
 * ViewModel экрана «Залы».
 * Загружает список, поддерживает создание, редактирование и удаление.
 */
class HallsViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var loadState by mutableStateOf<HallsLoadState>(HallsLoadState.Loading)
        private set

    var saveState by mutableStateOf<HallsSaveState>(HallsSaveState.Idle)
        private set

    init {
        load()
    }

    /** Загружает список залов текущего филиала. */
    fun load() {
        scope.launch {
            loadState = HallsLoadState.Loading
            api.halls.list().fold(
                ifLeft = { loadState = HallsLoadState.Error(it.toSettingsApiError()) },
                ifRight = { response ->
                    loadState =
                        HallsLoadState.Loaded(
                            response.halls.map { DirectoryItem(id = it.id, name = it.name) },
                        )
                },
            )
        }
    }

    /** Создаёт зал; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onCreate(
        item: DirectoryItem<HallId>,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            saveState = HallsSaveState.Saving
            api.halls.create(CreateHallRequest(id = item.id, name = item.name)).fold(
                ifLeft = { saveState = HallsSaveState.Error(it.toSettingsApiError()) },
                ifRight = {
                    saveState = HallsSaveState.Idle
                    load()
                    onSuccess()
                },
            )
        }
    }

    /** Обновляет зал; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onUpdate(
        item: DirectoryItem<HallId>,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            saveState = HallsSaveState.Saving
            api.halls.update(UpdateHallRequest(id = item.id, name = item.name)).fold(
                ifLeft = { saveState = HallsSaveState.Error(it.toSettingsApiError()) },
                ifRight = {
                    saveState = HallsSaveState.Idle
                    load()
                    onSuccess()
                },
            )
        }
    }

    /** Удаляет залы по [ids]; при успехе перезагружает список. */
    fun onDelete(ids: Set<HallId>) {
        scope.launch {
            api.halls.delete(DeleteHallRequest(ids = ids.toList())).onRight { load() }
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onSaveErrorDismissed() {
        if (saveState is HallsSaveState.Error) {
            saveState = HallsSaveState.Idle
        }
    }
}
