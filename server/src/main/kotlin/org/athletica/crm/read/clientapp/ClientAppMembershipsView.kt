package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipsResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/** Запрос абонементов профиля: [now] — момент запроса, из него выводится «сегодня» организации. */
data class ClientAppMembershipsQuery(
    val now: Instant,
)

/** Абонементы выбранного профиля кабинета с историей списаний. */
interface ClientAppMembershipsView {
    /** Абонементы клиента [ctx] на момент [query]; нет подключённого доступа — `CLIENT_NOT_FOUND`. */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun memberships(query: ClientAppMembershipsQuery): ClientAppMembershipsResponse
}
