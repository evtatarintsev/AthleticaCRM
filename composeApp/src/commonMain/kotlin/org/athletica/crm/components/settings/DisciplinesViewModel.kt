package org.athletica.crm.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.disciplines.CreateDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.UpdateDisciplineRequest
import org.athletica.crm.core.entityids.DisciplineId

/** Состояние загрузки списка дисциплин. */
sealed class DisciplinesLoadState {
    /** Загрузка в процессе. */
    data object Loading : DisciplinesLoadState()

    /** Список загружен. */
    data class Loaded(val items: List<DirectoryItem<DisciplineId>>) : DisciplinesLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: SettingsApiError) : DisciplinesLoadState()
}

/** Состояние операции сохранения дисциплины. */
sealed class DisciplinesSaveState {
    /** Ожидание действия пользователя. */
    data object Idle : DisciplinesSaveState()

    /** Сохранение выполняется. */
    data object Saving : DisciplinesSaveState()

    /** Ошибка сохранения. */
    data class Error(val error: SettingsApiError) : DisciplinesSaveState()
}

/**
 * ViewModel экрана «Дисциплины».
 * Загружает список, поддерживает создание, редактирование и удаление.
 */
class DisciplinesViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var loadState by mutableStateOf<DisciplinesLoadState>(DisciplinesLoadState.Loading)
        private set

    var saveState by mutableStateOf<DisciplinesSaveState>(DisciplinesSaveState.Idle)
        private set

    init {
        load()
    }

    /** Загружает список дисциплин. */
    fun load() {
        scope.launch {
            loadState = DisciplinesLoadState.Loading
            api.disciplineList().fold(
                ifLeft = { loadState = DisciplinesLoadState.Error(it.toSettingsApiError()) },
                ifRight = { response ->
                    loadState =
                        DisciplinesLoadState.Loaded(
                            response.disciplines.map { DirectoryItem(id = it.id, name = it.name) },
                        )
                },
            )
        }
    }

    /** Создаёт дисциплину; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onCreate(
        item: DirectoryItem<DisciplineId>,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            saveState = DisciplinesSaveState.Saving
            api.createDiscipline(CreateDisciplineRequest(id = item.id, name = item.name)).fold(
                ifLeft = { saveState = DisciplinesSaveState.Error(it.toSettingsApiError()) },
                ifRight = {
                    saveState = DisciplinesSaveState.Idle
                    load()
                    onSuccess()
                },
            )
        }
    }

    /** Обновляет дисциплину; при успехе перезагружает список и вызывает [onSuccess]. */
    fun onUpdate(
        item: DirectoryItem<DisciplineId>,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            saveState = DisciplinesSaveState.Saving
            api.updateDiscipline(UpdateDisciplineRequest(id = item.id, name = item.name)).fold(
                ifLeft = { saveState = DisciplinesSaveState.Error(it.toSettingsApiError()) },
                ifRight = {
                    saveState = DisciplinesSaveState.Idle
                    load()
                    onSuccess()
                },
            )
        }
    }

    /** Удаляет дисциплины по [ids]; при успехе перезагружает список. */
    fun onDelete(ids: Set<DisciplineId>) {
        scope.launch {
            api.deleteDiscipline(DeleteDisciplineRequest(ids = ids.toList())).onRight { load() }
        }
    }

    /** Сбрасывает ошибку сохранения. */
    fun onSaveErrorDismissed() {
        if (saveState is DisciplinesSaveState.Error) {
            saveState = DisciplinesSaveState.Idle
        }
    }
}
