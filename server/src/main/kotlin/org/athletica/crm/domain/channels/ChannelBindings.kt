package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelBindingId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Связка канального адреса с клиентом в рамках интеграции.
 * [address] — непрозрачный адрес в адресном пространстве канала (chat_id, vk_id, …).
 */
data class ChannelBinding(
    val id: ChannelBindingId,
    val integrationId: ChannelIntegrationId,
    val clientId: ClientId,
    val address: String,
)

/**
 * Хранилище связок «канальный адрес ↔ клиент». Изолировано по организации.
 * Нужно связанным каналам: адрес отправки устанавливается в рантайме и не совпадает с контактом.
 * Используется в обе стороны — приём ([clientFor]) и отправка ([addressFor]).
 */
interface ChannelBindings {
    /**
     * Создаёт или переустанавливает связку адреса [address] интеграции [integrationId] на клиента [clientId].
     * Идемпотентно по паре (интеграция, адрес): повторный вызов перепривязывает адрес на нового клиента.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun link(integrationId: ChannelIntegrationId, clientId: ClientId, address: String): ChannelBinding

    /** Клиент, привязанный к адресу [address] интеграции [integrationId]; `null`, если связки нет. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun clientFor(integrationId: ChannelIntegrationId, address: String): ClientId?

    /** Адрес клиента [clientId] в интеграции [integrationId]; `null`, если клиент не привязан. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun addressFor(integrationId: ChannelIntegrationId, clientId: ClientId): String?
}
