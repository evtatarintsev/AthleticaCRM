package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.core.entityids.GroupId

/**
 * ViewModel экрана создания группы.
 * По завершении успешного создания вызывает [onCreated].
 */
class GroupCreateViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onCreated: () -> Unit,
) {
    var saveState by mutableStateOf<GroupSaveState>(GroupSaveState.Idle)
        private set

    /** Отправляет запрос на создание группы из данных формы [form]. */
    fun onCreate(form: GroupForm) {
        scope.launch {
            saveState = GroupSaveState.Saving
            api
                .groups.create(
                    GroupCreateRequest(
                        id = GroupId.new(),
                        name = form.name,
                        schedule = form.schedule,
                        disciplineIds = form.selectedDisciplines.map { it.id },
                        employeeIds = form.selectedEmployees.map { it.id },
                    ),
                ).fold(
                    ifLeft = { saveState = GroupSaveState.Error(it.toGroupsApiError()) },
                    ifRight = {
                        saveState = GroupSaveState.Idle
                        onCreated()
                    },
                )
        }
    }
}
