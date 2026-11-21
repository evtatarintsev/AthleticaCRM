package org.athletica.crm.api.schemas.auth

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.EmployeeId
import kotlin.time.Instant

/** Платформа устройства сеанса; по ней выбирается иконка. */
@Serializable
enum class DevicePlatform {
    MACOS,
    WINDOWS,
    LINUX,
    IOS,
    ANDROID,
    OTHER,
}

/** Активный сеанс входа. */
@Serializable
data class SessionItem(
    /** Идентификатор сеанса. */
    val id: AuthSessionId,
    /** Браузер с мажорной версией, например «Chrome 142»; `null`, если не распознан. */
    val browser: String?,
    /** Операционная система, например «macOS»; `null`, если не распознана. */
    val os: String?,
    /** Платформа устройства. */
    val platform: DevicePlatform,
    /** Последний известный IP-адрес. */
    val ip: String?,
    /** Момент входа. */
    val createdAt: Instant,
    /** Момент последней активности (обновления токена). */
    val lastSeenAt: Instant,
    /** Сеанс, из которого сделан запрос. */
    val current: Boolean,
)

/** Активные сеансы, от недавних к давним. */
@Serializable
data class SessionListResponse(
    /** Сеансы. */
    val items: List<SessionItem>,
)

/** Запрос на завершение своего сеанса. */
@Serializable
data class TerminateSessionRequest(
    /** Завершаемый сеанс. */
    val sessionId: AuthSessionId,
)

/** Запрос сеансов сотрудника. */
@Serializable
data class EmployeeSessionsRequest(
    /** Сотрудник. */
    val employeeId: EmployeeId,
)

/** Запрос на завершение сеанса сотрудника. */
@Serializable
data class TerminateEmployeeSessionRequest(
    /** Сотрудник. */
    val employeeId: EmployeeId,
    /** Завершаемый сеанс. */
    val sessionId: AuthSessionId,
)
