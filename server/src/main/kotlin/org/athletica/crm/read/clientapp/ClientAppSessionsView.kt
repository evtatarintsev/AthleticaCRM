package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppSessionsResponse
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Duration
import kotlin.time.Instant

/** Сеансы аккаунта кабинета — браузеры и устройства; видны только самому владельцу аккаунта. */
interface ClientAppSessionsView {
    /** Сеансы аккаунта запроса по [query], от недавних к давним; сеанс запроса отмечен текущим. */
    context(ctx: ClientAccountContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun sessions(query: ClientAppSessionsQuery): ClientAppSessionsResponse
}

/** Запрос сеансов аккаунта. */
data class ClientAppSessionsQuery(
    /** Момент запроса. */
    val now: Instant,
    /** Сеансы без активности дольше этого срока не показываются: их токен обновления уже истёк. */
    val maxIdle: Duration,
)
