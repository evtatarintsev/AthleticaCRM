package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.SetGroupEmployeesRequest
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId

/** Состояние экрана деталей группы. */
sealed class GroupDetailState {
    /** Загрузка данных группы. */
    data object Loading : GroupDetailState()

    /** Ошибка загрузки. */
    data class Error(val error: GroupsApiError) : GroupDetailState()

    /** Данные группы загружены. */
    data class Loaded(val group: GroupDetailResponse) : GroupDetailState()
}

/**
 * ViewModel экрана деталей группы.
 * Загружает данные по [groupId] и управляет составом преподавателей.
 */
class GroupDetailViewModel(
    private val api: ApiClient,
    private val groupId: GroupId,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<GroupDetailState>(GroupDetailState.Loading)
        private set

    init {
        load()
    }

    /** Загружает или перезагружает данные группы. */
    fun load() {
        scope.launch {
            state = GroupDetailState.Loading
            api.groups.detail(groupId).fold(
                ifLeft = { state = GroupDetailState.Error(it.toGroupsApiError()) },
                ifRight = { state = GroupDetailState.Loaded(it) },
            )
        }
    }

    /** Удаляет преподавателя из группы и перезагружает данные. */
    fun onRemoveEmployee(employeeId: EmployeeId) {
        val loaded = state as? GroupDetailState.Loaded ?: return
        val newEmployeeIds = loaded.group.employees.map { it.id }.filter { it != employeeId }

        scope.launch {
            api.groups.setEmployees(SetGroupEmployeesRequest(groupId, newEmployeeIds)).fold(
                ifLeft = { /* Ошибку обновления можно вывести в SnackBar или Toast */ },
                ifRight = { load() },
            )
        }
    }
}
