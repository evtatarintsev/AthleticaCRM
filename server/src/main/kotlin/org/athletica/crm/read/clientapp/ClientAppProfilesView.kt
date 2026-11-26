package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Профили аккаунта кабинета для переключателя. */
interface ClientAppProfilesView {
    /**
     * Подключённые профили номера аккаунта — клиенты не в архиве организаций с включённым кабинетом —
     * и число организаций с неподключёнными приглашениями. Профили принадлежат аккаунту, а не сеансу.
     */
    context(ctx: ClientAccountContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun profiles(): ClientAppProfilesResponse
}
