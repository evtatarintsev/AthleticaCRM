package org.athletica.crm.api.schemas.clientapp

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientAppSessionId
import kotlin.time.Instant

/**
 * Сеанс аккаунта [id] — браузер или устройство с названием [name] (`Safari, iOS`, `null` — не распознано):
 * вход [createdAt], последняя активность [lastSeenAt]; [current] — сеанс, из которого сделан запрос.
 */
@Serializable
data class ClientAppSessionSchema(
    val id: ClientAppSessionId,
    val name: String?,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val current: Boolean,
)

/** Сеансы аккаунта [sessions], от недавних к давним. */
@Serializable
data class ClientAppSessionsResponse(
    val sessions: List<ClientAppSessionSchema>,
)

/** Завершение сеанса [sessionId] другого устройства аккаунта. */
@Serializable
data class ClientAppTerminateSessionRequest(
    val sessionId: ClientAppSessionId,
)
