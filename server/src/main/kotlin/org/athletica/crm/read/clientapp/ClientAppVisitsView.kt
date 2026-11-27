package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.clientapp.ClientAppVisitsResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Запрос истории посещений профиля: месяц, в который попадает [monthOf] (`null` — текущий месяц
 * организации на момент [now]).
 */
data class ClientAppVisitsQuery(
    val monthOf: LocalDate?,
    val now: Instant,
)

/** История посещений выбранного профиля кабинета за месяц. */
interface ClientAppVisitsView {
    /** Проведённые занятия клиента [ctx] за месяц [query] с отметкой «был» / «не был»; нет подключённого доступа — `CLIENT_NOT_FOUND`. */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun visits(query: ClientAppVisitsQuery): ClientAppVisitsResponse
}
