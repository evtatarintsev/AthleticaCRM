package org.athletica.crm.components.employees

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.core.entityids.EmployeeId

/** Состояние экрана карточки сотрудника. */
sealed class EmployeeDetailState {
    /** Загрузка данных. */
    data object Loading : EmployeeDetailState()

    /** Ошибка загрузки. */
    data class Error(val error: EmployeesApiError) : EmployeeDetailState()

    /** Данные загружены. */
    data class Loaded(val employee: EmployeeDetailResponse) : EmployeeDetailState()
}

/**
 * ViewModel экрана карточки сотрудника.
 * Загружает данные по [employeeId] через [api].
 */
class EmployeeDetailViewModel(
    private val api: ApiClient,
    private val employeeId: EmployeeId,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<EmployeeDetailState>(EmployeeDetailState.Loading)
        private set

    init {
        load()
    }

    /** Загружает (или перезагружает) данные сотрудника. */
    fun load() {
        scope.launch {
            state = EmployeeDetailState.Loading
            api.employeeDetail(employeeId).fold(
                ifLeft = { state = EmployeeDetailState.Error(it.toEmployeesApiError()) },
                ifRight = { state = EmployeeDetailState.Loaded(it) },
            )
        }
    }
}
