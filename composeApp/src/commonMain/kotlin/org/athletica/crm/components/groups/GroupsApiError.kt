package org.athletica.crm.components.groups

import androidx.compose.runtime.Composable
import org.athletica.crm.api.client.ApiClientError
import org.athletica.crm.generated.resources.Res
import org.athletica.crm.generated.resources.error_service_unavailable
import org.athletica.crm.generated.resources.error_session_expired
import org.jetbrains.compose.resources.stringResource

/** Типизированные ошибки, возникающие при работе с API групп. */
sealed class GroupsApiError {
    /** Сессия истекла — требуется повторная авторизация. */
    data object SessionExpired : GroupsApiError()

    /** Сервер временно недоступен. */
    data object ServiceUnavailable : GroupsApiError()

    /** Сервер вернул ошибку валидации с описанием [message]. */
    data class ServerValidation(val message: String) : GroupsApiError()
}

/** Преобразует сетевую ошибку в типизированную ошибку groups-модуля. */
fun ApiClientError.toGroupsApiError(): GroupsApiError =
    when (this) {
        is ApiClientError.Unauthenticated -> GroupsApiError.SessionExpired
        is ApiClientError.ValidationError -> GroupsApiError.ServerValidation(message)
        is ApiClientError.Unavailable -> GroupsApiError.ServiceUnavailable
    }

/** Возвращает локализованную строку для отображения ошибки пользователю. */
@Composable
fun GroupsApiError.message(): String =
    when (this) {
        GroupsApiError.SessionExpired -> stringResource(Res.string.error_session_expired)
        GroupsApiError.ServiceUnavailable -> stringResource(Res.string.error_service_unavailable)
        is GroupsApiError.ServerValidation -> message
    }
