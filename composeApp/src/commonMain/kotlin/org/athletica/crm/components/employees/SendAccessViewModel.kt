package org.athletica.crm.components.employees

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.client.ApiClient
import org.athletica.crm.api.schemas.employees.SendEmployeeAccessRequest
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.EmployeeId

/** Состояние диалога отправки доступа сотруднику. */
sealed class SendAccessState {
    /** Ожидание отправки. */
    data object Idle : SendAccessState()

    /** Запрос выполняется. */
    data object Submitting : SendAccessState()

    /** Сервер вернул ошибку. */
    data class Error(val error: EmployeesApiError) : SendAccessState()
}

/**
 * ViewModel диалога отправки доступа сотруднику.
 * Отправляет запрос через [api] и вызывает [onSuccess] при успехе.
 */
class SendAccessViewModel(
    private val api: ApiClient,
    private val employeeId: EmployeeId,
    private val scope: CoroutineScope,
    private val onSuccess: () -> Unit,
) {
    var state by mutableStateOf<SendAccessState>(SendAccessState.Idle)
        private set

    /** Отправляет доступ на [email] с паролем [password]. */
    fun onSend(email: String, password: String) {
        scope.launch {
            state = SendAccessState.Submitting
            api
                .sendEmployeeAccess(
                    SendEmployeeAccessRequest(
                        employeeId = employeeId,
                        email = EmailAddress(email.trim()),
                        password = password,
                    ),
                ).fold(
                    ifLeft = { state = SendAccessState.Error(it.toEmployeesApiError()) },
                    ifRight = {
                        state = SendAccessState.Idle
                        onSuccess()
                    },
                )
        }
    }

    /** Сбрасывает ошибку. */
    fun onErrorDismissed() {
        state = SendAccessState.Idle
    }
}
