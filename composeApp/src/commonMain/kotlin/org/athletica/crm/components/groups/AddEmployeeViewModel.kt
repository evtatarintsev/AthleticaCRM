package org.athletica.crm.components.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.groups.SetGroupEmployeesRequest
import org.athletica.crm.components.employees.toEmployeesApiError
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId

/** Состояние шторки добавления преподавателя в группу. */
sealed class AddEmployeeState {
    /** Загрузка списка сотрудников. */
    data object Loading : AddEmployeeState()

    /** Ошибка загрузки. */
    data class Error(val error: org.athletica.crm.components.employees.EmployeesApiError) : AddEmployeeState()

    /**
     * Список сотрудников загружен.
     * [isAdding] — запрос добавления в выбранную группу выполняется.
     */
    data class Loaded(
        val employees: List<EmployeeListItem>,
        val isAdding: Boolean = false,
    ) : AddEmployeeState()
}

/**
 * ViewModel шторки добавления преподавателей в группу.
 * Загружает список активных сотрудников и добавляет выбранного в группу.
 */
class AddEmployeeViewModel(
    private val api: ApiClient,
    private val groupId: GroupId,
    private val existingEmployeeIds: Set<EmployeeId>,
    private val scope: CoroutineScope,
    private val onEmployeeAdded: () -> Unit,
) {
    var state by mutableStateOf<AddEmployeeState>(AddEmployeeState.Loading)
        private set

    init {
        loadEmployees()
    }

    private fun loadEmployees() {
        scope.launch {
            api.employees.list().fold(
                ifLeft = { state = AddEmployeeState.Error(it.toEmployeesApiError()) },
                ifRight = { response ->
                    val available = response.employees.filter { it.id !in existingEmployeeIds && it.isActive }
                    state = AddEmployeeState.Loaded(available)
                },
            )
        }
    }

    /** Добавляет выбранного преподавателя в группу. */
    fun onEmployeeSelected(employeeId: EmployeeId) {
        val loaded = state as? AddEmployeeState.Loaded ?: return
        state = loaded.copy(isAdding = true)

        val newEmployeeIds = existingEmployeeIds + employeeId

        scope.launch {
            api.groups.setEmployees(SetGroupEmployeesRequest(groupId, newEmployeeIds.toList())).fold(
                ifLeft = { state = loaded.copy(isAdding = false) },
                ifRight = { onEmployeeAdded() },
            )
        }
    }
}
