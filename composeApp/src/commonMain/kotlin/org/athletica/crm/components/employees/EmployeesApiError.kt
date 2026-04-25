package org.athletica.crm.components.employees

import androidx.compose.runtime.Composable
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.error_service_unavailable
import org.athletica.crm.generated.resources.error_session_expired
import org.jetbrains.compose.resources.stringResource

/** Типизированные ошибки, возникающие при работе с API сотрудников. */
sealed class EmployeesApiError {
    /** Сессия истекла — требуется повторная авторизация. */
    data object SessionExpired : EmployeesApiError()

    /** Сервер временно недоступен. */
    data object ServiceUnavailable : EmployeesApiError()

    /** Сервер вернул ошибку валидации с описанием [message]. */
    data class ServerValidation(val message: String) : EmployeesApiError()
}

/** Преобразует сетевую ошибку в типизированную ошибку employees-модуля. */
fun ApiClientError.toEmployeesApiError(): EmployeesApiError =
    when (this) {
        is ApiClientError.Unauthenticated -> EmployeesApiError.SessionExpired
        is ApiClientError.ValidationError -> EmployeesApiError.ServerValidation(message)
        is ApiClientError.Unavailable -> EmployeesApiError.ServiceUnavailable
    }

/** Возвращает локализованную строку для отображения ошибки пользователю. */
@Composable
fun EmployeesApiError.message(): String =
    when (this) {
        EmployeesApiError.SessionExpired -> stringResource(Res.string.error_session_expired)
        EmployeesApiError.ServiceUnavailable -> stringResource(Res.string.error_service_unavailable)
        is EmployeesApiError.ServerValidation -> message
    }
