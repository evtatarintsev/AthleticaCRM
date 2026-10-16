package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Настроенная интеграция канала связи — объект отправки. Провайдер-специфичные настройки (`config`)
 * не видны: работа с интеграцией не требует знания вида канала. Управление настройками — отдельная
 * read-проекция ([org.athletica.crm.read.channels.ChannelListView]).
 *
 * Адрес получателя разрешается по способу адресации вида канала (прямые — из контакта, связанные —
 * из связки), отправка — через транспорт провайдера ([ChannelTransport]); см. [DbChannelIntegration].
 */
interface ChannelIntegration {
    /** Идентификатор интеграции. */
    val id: ChannelIntegrationId

    /** Отображаемое имя интеграции. */
    val name: String

    /** Активна ли интеграция. */
    val enabled: Boolean

    /**
     * Разрешает адрес получателя для клиента [clientId] в адресном пространстве канала.
     * [contactId] — выбранный контакт-адресат для прямых каналов (`null` — взять первый подходящий);
     * связанные каналы (Telegram/VK) его игнорируют и берут адрес из связки.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun recipientAddress(clientId: ClientId, contactId: ClientContactId?): String

    /**
     * Отправляет текст [body] на подготовленный адрес [address] через провайдера канала.
     * Worker-путь: адрес уже разрешён на этапе постановки доставки. Чистый транспорт, без БД.
     */
    suspend fun send(address: String, body: String): SendResult
}
