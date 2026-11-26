package org.athletica.crm.domain.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.storage.Transaction

/** Репозиторий доступов к кабинету клиента. */
interface ClientAppAccesses {
    /**
     * Доступы по каждому телефону клиента [clientId] организации запроса в порядке контактов,
     * по одному на номер. Номер без приглашения — [NotInvitedClientAppAccess].
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byClient(clientId: ClientId): List<ClientAppAccess>

    /**
     * Доступы номера [phone] во всех организациях, которые сейчас могут давать данные:
     * кабинет организации включён и клиент не в архиве. Остальные доступы сохраняют статус,
     * но не возвращаются — восстановление клиента или включение кабинета возвращает их как были.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun byPhone(phone: PhoneDigits): List<StoredClientAppAccess>
}
