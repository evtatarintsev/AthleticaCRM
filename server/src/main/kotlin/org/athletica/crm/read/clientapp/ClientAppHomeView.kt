package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppHomeResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Главная кабинета по выбранному профилю. */
interface ClientAppHomeView {
    /** Главная профиля [ctx]; нет подключённого доступа — `CLIENT_NOT_FOUND`. */
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun home(): ClientAppHomeResponse
}
