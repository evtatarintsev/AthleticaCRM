package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.storage.Transaction

/**
 * Коллекция интеграций каналов организации: хранит их и выдаёт по id.
 * Все запросные операции изолированы по организации; персистентность — здесь, а не на элементе.
 */
interface ChannelIntegrations {
    /**
     * Возвращает интеграцию-отправитель по [id]; `CHANNEL_NOT_FOUND`, если не найдена или принадлежит
     * другой организации. Строка интеграции блокируется от удаления до конца транзакции, чтобы
     * параллельный [remove] не удалил канал между выбором и записью доставки.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: ChannelIntegrationId): ChannelIntegration

    /** Добавляет новую (активную) интеграцию [id] с настройками [config]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun add(id: ChannelIntegrationId, name: String, config: ChannelConfig)

    /**
     * Сохранённые настройки провайдера интеграции [id] вместе с секретами — для слияния с частичным
     * изменением; `CHANNEL_NOT_FOUND`, если не найдена или принадлежит другой организации.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun config(id: ChannelIntegrationId): ChannelConfig

    /**
     * Меняет имя и настройки провайдера интеграции [id], не трогая включённость;
     * `CHANNEL_NOT_FOUND`, если не найдена или принадлежит другой организации.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun update(
        id: ChannelIntegrationId,
        name: String,
        config: ChannelConfig,
    )

    /**
     * Включает или выключает интеграцию [id] без проверки настроек у провайдера;
     * `CHANNEL_NOT_FOUND`, если не найдена или принадлежит другой организации.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun setEnabled(id: ChannelIntegrationId, enabled: Boolean)

    /**
     * Удаляет интеграцию [id]. `CHANNEL_IN_USE`, если через неё уже шли сообщения (исходящие доставки
     * или входящие): история ссылается на канал, такой канал отключают, а не удаляют. Строка интеграции
     * блокируется до проверки, поэтому параллельная отправка либо завершится до неё и будет учтена,
     * либо дождётся удаления и получит `CHANNEL_NOT_FOUND`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun remove(id: ChannelIntegrationId)

    /**
     * Worker-путь: возвращает интеграцию по [id] без привязки к организации, готовую к отправке
     * ([ChannelIntegration.send]). Используется фоновым диспетчером. `null`, если интеграция удалена.
     */
    context(tr: Transaction)
    suspend fun senderById(id: ChannelIntegrationId): ChannelIntegration?
}
