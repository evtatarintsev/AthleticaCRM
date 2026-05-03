package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.EditGroupRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.core.entityids.GroupId

/** Состояние загрузки при редактировании группы. */
sealed class GroupEditLoadState {
    /** Загрузка данных группы. */
    data object Loading : GroupEditLoadState()

    /** Ошибка загрузки. */
    data class Error(val error: GroupsApiError) : GroupEditLoadState()

    /** Данные группы загружены и готовы к редактированию. */
    data class Loaded(val group: GroupDetailResponse) : GroupEditLoadState()
}

/**
 * ViewModel экрана редактирования группы.
 * Загружает данные группы и управляет процессом сохранения изменений.
 */
class GroupEditViewModel(
    private val api: ApiClient,
    private val groupId: GroupId,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit,
) {
    var loadState by mutableStateOf<GroupEditLoadState>(GroupEditLoadState.Loading)
        private set

    var saveState by mutableStateOf<GroupSaveState>(GroupSaveState.Idle)
        private set

    init {
        load()
    }

    /** Загружает данные группы. */
    private fun load() {
        scope.launch {
            loadState = GroupEditLoadState.Loading
            api.groups.detail(groupId).fold(
                ifLeft = { loadState = GroupEditLoadState.Error(it.toGroupsApiError()) },
                ifRight = { loadState = GroupEditLoadState.Loaded(it) },
            )
        }
    }

    /** Отправляет запрос на редактирование группы из данных формы [form]. */
    fun onSave(form: GroupForm) {
        scope.launch {
            saveState = GroupSaveState.Saving
            api
                .groups.edit(
                    EditGroupRequest(
                        id = groupId,
                        name = form.name,
                        schedule = form.schedule,
                        disciplineIds = form.selectedDisciplines.map { it.id },
                        employeeIds = form.selectedEmployees.map { it.id },
                    ),
                ).fold(
                    ifLeft = { saveState = GroupSaveState.Error(it.toGroupsApiError()) },
                    ifRight = {
                        saveState = GroupSaveState.Idle
                        onSaved()
                    },
                )
        }
    }
}
