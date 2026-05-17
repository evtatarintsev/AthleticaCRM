package org.athletica.crm.components.tasks

import org.athletica.crm.api.client.ApiClientError

sealed class TasksApiError {
    data object NetworkError : TasksApiError()
    data object Unauthorized : TasksApiError()
    data class ServerError(val code: String, val message: String) : TasksApiError()
    data object Unknown : TasksApiError()
}

fun ApiClientError.toTasksApiError(): TasksApiError =
    when (this) {
        is ApiClientError.NetworkError -> TasksApiError.NetworkError
        is ApiClientError.Unauthenticated -> TasksApiError.Unauthorized
        is ApiClientError.ServerError -> TasksApiError.ServerError(code, message)
        is ApiClientError.Unknown -> TasksApiError.Unknown
    }
