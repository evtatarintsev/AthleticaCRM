package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppBalanceResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Баланс выбранного профиля кабинета и его история. */
interface ClientAppBalanceView {
    /** Баланс, неоплаченные занятия и движения клиента [ctx]; нет подключённого доступа — `CLIENT_NOT_FOUND`. */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun balance(): ClientAppBalanceResponse
}
