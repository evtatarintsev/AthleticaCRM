package org.athletica.crm.components.clients

import androidx.compose.runtime.Composable
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.error_service_unavailable
import org.athletica.crm.generated.resources.error_session_expired
import org.jetbrains.compose.resources.stringResource

/** Типизированные ошибки API для экранов клиентов. */
sealed class ClientsApiError {
    /** Сессия истекла — пользователь не аутентифицирован. */
    data object SessionExpired : ClientsApiError()

    /** Сервис недоступен или нет соединения. */
    data object ServiceUnavailable : ClientsApiError()

    /** Ошибка валидации от сервера с текстом [message]. */
    data class ServerValidation(val message: String) : ClientsApiError()
}

/** Конвертирует [ApiClientError] в [ClientsApiError]. */
fun ApiClientError.toClientsApiError(): ClientsApiError =
    when (this) {
        is ApiClientError.Unauthenticated -> ClientsApiError.SessionExpired
        is ApiClientError.ValidationError -> ClientsApiError.ServerValidation(message)
        is ApiClientError.Unavailable -> ClientsApiError.ServiceUnavailable
    }

/** Возвращает локализованное сообщение об ошибке. */
@Composable
fun ClientsApiError.message(): String =
    when (this) {
        ClientsApiError.SessionExpired -> stringResource(Res.string.error_session_expired)
        ClientsApiError.ServiceUnavailable -> stringResource(Res.string.error_service_unavailable)
        is ClientsApiError.ServerValidation -> message
    }
