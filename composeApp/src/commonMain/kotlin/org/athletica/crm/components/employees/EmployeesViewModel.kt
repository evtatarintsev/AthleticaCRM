package org.athletica.crm.components.employees

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.EmployeeListItem

/** Состояние экрана списка сотрудников. */
sealed class EmployeesState {
    /** Загрузка в процессе. */
    data object Loading : EmployeesState()

    /** Список загружен. */
    data class Loaded(val employees: List<EmployeeListItem>) : EmployeesState()

    /** Ошибка загрузки. */
    data class Error(val error: EmployeesApiError) : EmployeesState()
}

/**
 * ViewModel экрана списка сотрудников.
 * Загружает список через [api]; повторная загрузка — через [load].
 */
class EmployeesViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<EmployeesState>(EmployeesState.Loading)
        private set

    /** Загружает (или перезагружает) список сотрудников. */
    fun load() {
        scope.launch {
            state = EmployeesState.Loading
            api.employees.list().fold(
                ifLeft = { state = EmployeesState.Error(it.toEmployeesApiError()) },
                ifRight = { state = EmployeesState.Loaded(it.employees) },
            )
        }
    }
}
