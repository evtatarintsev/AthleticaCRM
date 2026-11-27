package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Запрос расписания профиля: неделя, в которую попадает [weekOf] (`null` — текущая неделя организации),
 * и момент запроса [now], из которого выводятся «сегодня» и «занятие началось».
 */
data class ClientAppScheduleQuery(
    val weekOf: LocalDate?,
    val now: Instant,
)

/** Расписание выбранного профиля кабинета за неделю. */
interface ClientAppScheduleView {
    /** Занятия клиента [ctx] за неделю [query] и ближайшее занятие; нет подключённого доступа — `CLIENT_NOT_FOUND`. */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun schedule(query: ClientAppScheduleQuery): ClientAppScheduleResponse
}
