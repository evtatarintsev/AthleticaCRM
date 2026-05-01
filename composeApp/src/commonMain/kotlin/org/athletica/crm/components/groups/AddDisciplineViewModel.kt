package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.core.entityids.DisciplineId

/** Состояние шторки выбора дисциплины. */
sealed class AddDisciplineState {
    /** Загрузка в процессе. */
    data object Loading : AddDisciplineState()

    /** Список загружен. */
    data class Loaded(val disciplines: List<DisciplineDetailResponse>) : AddDisciplineState()

    /** Ошибка загрузки. */
    data class Error(val error: GroupsApiError) : AddDisciplineState()
}

/**
 * ViewModel шторки выбора дисциплины.
 * Загружает все дисциплины при создании; фильтрует [existingDisciplineIds].
 */
class AddDisciplineViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val existingDisciplineIds: Set<DisciplineId>,
) {
    var state by mutableStateOf<AddDisciplineState>(AddDisciplineState.Loading)
        private set

    init {
        load()
    }

    private fun load() {
        scope.launch {
            api.disciplines.list().fold(
                ifLeft = { state = AddDisciplineState.Error(it.toGroupsApiError()) },
                ifRight = { response ->
                    state =
                        AddDisciplineState.Loaded(
                            response.disciplines.filter { it.id !in existingDisciplineIds },
                        )
                },
            )
        }
    }
}
