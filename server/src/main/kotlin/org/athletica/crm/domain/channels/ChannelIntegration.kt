package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.ChannelProvider
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.storage.Transaction

/**
 * Настроенная интеграция канала связи организации.
 *
 * [config] — типизированный провайдер-специфичный конфиг; его интерпретация — задача адаптера канала.
 * [provider] и [channelType] выводятся из [config], отдельных полей-дискриминаторов нет.
 */
interface ChannelIntegration {
    /** Идентификатор интеграции. */
    val id: ChannelIntegrationId

    /** Отображаемое имя интеграции. */
    val name: String

    /** Провайдер-специфичный конфиг. */
    val config: ChannelConfig

    /** Активна ли интеграция. */
    val enabled: Boolean

    /** Провайдер интеграции. */
    val provider: ChannelProvider get() = config.provider

    /** Тип канала интеграции. */
    val channelType: ChannelType get() = config.provider.channelType

    /** Сохраняет интеграцию: INSERT при создании либо UPDATE существующей. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /** Удаляет интеграцию. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete()

    /** Возвращает копию с новыми параметрами (без записи в БД). */
    fun withNew(name: String, config: ChannelConfig, enabled: Boolean): ChannelIntegration
}
