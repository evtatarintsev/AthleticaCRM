package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationsResponse
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Неподключённые приглашения аккаунта кабинета — без данных клиентов. */
interface ClientAppInvitationsView {
    /**
     * Организации с включённым кабинетом, где у номера аккаунта есть приглашённые доступы неархивных
     * клиентов: название, момент последнего приглашения и число клиентов.
     */
    context(ctx: ClientAccountContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun invitations(): ClientAppInvitationsResponse
}
