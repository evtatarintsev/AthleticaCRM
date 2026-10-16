package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Собеседник в связанном канале (чат Telegram и т.п.) — техническая информация о чате.
 * В контакты клиента не попадает и в карточке клиента не редактируется.
 */
data class ChannelChat(
    /** Интеграция канала, в которой идёт чат. */
    val integrationId: ChannelIntegrationId,
    /** Адрес чата в адресном пространстве канала. */
    val address: String,
    /** Отображаемое имя собеседника в канале. */
    val displayName: String,
    /** Имя пользователя в канале без `@`; `null`, если не задано. */
    val username: String?,
    /** Номер телефона, присланный самим собеседником; `null`, если не присылал. */
    val phone: String?,
)

/** Хранилище чатов связанных каналов. Изолировано по организации. */
interface ChannelChats {
    /**
     * Создаёт или обновляет чат [address] интеграции [integrationId] и возвращает его актуальное состояние.
     * Имя и username перезаписываются; [phone] записывается, только если передан, —
     * `null` не затирает уже сохранённый номер.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun upsert(
        integrationId: ChannelIntegrationId,
        address: String,
        displayName: String,
        username: String?,
        phone: String? = null,
    ): ChannelChat

    /** Чат [address] интеграции [integrationId]; `null`, если такого чата в организации нет. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byAddress(integrationId: ChannelIntegrationId, address: String): ChannelChat?
}
