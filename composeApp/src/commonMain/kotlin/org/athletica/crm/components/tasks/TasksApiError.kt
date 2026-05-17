package org.athletica.crm.components.tasks

import org.athletica.crm.api.client.ApiClientError

sealed class TasksApiError {
    data object Unauthorized : TasksApiError()
    data class ServiceUnavailable(val cause: Throwable) : TasksApiError()
    data class ValidationError(val code: String, val message: String) : TasksApiError()
    data object Unknown : TasksApiError()
}

fun ApiClientError.toTasksApiError(): TasksApiError =
    when (this) {
        is ApiClientError.Unauthenticated -> TasksApiError.Unauthorized
        is ApiClientError.Unavailable -> TasksApiError.ServiceUnavailable(cause)
        is ApiClientError.ValidationError -> TasksApiError.ValidationError(code, message)
    }
