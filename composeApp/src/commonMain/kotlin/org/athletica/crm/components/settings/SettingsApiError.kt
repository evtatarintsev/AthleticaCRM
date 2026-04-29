package org.athletica.crm.components.settings

import androidx.compose.runtime.Composable
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.error_service_unavailable
import org.athletica.crm.generated.resources.error_session_expired
import org.jetbrains.compose.resources.stringResource

/** Типизированные ошибки, возникающие при работе с API настроек. */
sealed class SettingsApiError {
    /** Сессия истекла — требуется повторная авторизация. */
    data object SessionExpired : SettingsApiError()

    /** Сервер временно недоступен. */
    data object ServiceUnavailable : SettingsApiError()

    /** Сервер вернул ошибку валидации с описанием [message]. */
    data class ServerValidation(val message: String) : SettingsApiError()

    companion object {
        /** Преобразует [ApiClientError] в типизированную ошибку settings-модуля. */
        fun fromResponse(error: ApiClientError): SettingsApiError = error.toSettingsApiError()

        /** Преобразует необработанное исключение в недоступность сервиса. */
        fun fromResponse(e: Exception): SettingsApiError = ServiceUnavailable
    }
}

/** Преобразует сетевую ошибку в типизированную ошибку settings-модуля. */
fun ApiClientError.toSettingsApiError(): SettingsApiError =
    when (this) {
        is ApiClientError.Unauthenticated -> SettingsApiError.SessionExpired
        is ApiClientError.ValidationError -> SettingsApiError.ServerValidation(message)
        is ApiClientError.Unavailable -> SettingsApiError.ServiceUnavailable
    }

/** Возвращает локализованную строку для отображения ошибки пользователю. */
@Composable
fun SettingsApiError.message(): String =
    when (this) {
        SettingsApiError.SessionExpired -> stringResource(Res.string.error_session_expired)
        SettingsApiError.ServiceUnavailable -> stringResource(Res.string.error_service_unavailable)
        is SettingsApiError.ServerValidation -> message
    }
