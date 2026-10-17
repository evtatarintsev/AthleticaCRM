package org.athletica.crm.usecases.channels

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.channels.ChannelConfigChange
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelConfigVerifier
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database

/**
 * Меняет имя интеграции [id] на [name] и её настройки провайдера по частичному изменению [change].
 * Не переданные секреты берутся из сохранённого конфига. У провайдера через [verifier] конфиг
 * проверяется только если учётные данные изменились, и вне транзакции: сетевой запрос не держит
 * соединение пула. Включённость интеграции не меняется.
 */
context(db: Database, ctx: EmployeeRequestContext, raise: Raise<DomainError>)
suspend fun updateChannel(
    channels: ChannelIntegrations,
    verifier: ChannelConfigVerifier,
    id: ChannelIntegrationId,
    name: String,
    change: ChannelConfigChange,
) {
    val current = db.transaction { channels.config(id) }
    val merged = change.mergedInto(current)
    val config =
        if (merged == current) {
            merged
        } else {
            verifier.verified(merged)
        }
    db.transaction { channels.update(id, name, config) }
}

/**
 * Сохранённый конфиг [current] с полями из изменения; секрет `null` остаётся сохранённым, имя
 * Telegram-бота — тоже (при новом токене его обновит проверка). `CHANNEL_PROVIDER_MISMATCH`,
 * если провайдер изменения отличается от сохранённого.
 */
context(ctx: RequestContext, raise: Raise<DomainError>)
fun ChannelConfigChange.mergedInto(current: ChannelConfig): ChannelConfig =
    when (this) {
        is ChannelConfigChange.SmscSms ->
            when (current) {
                is ChannelConfig.SmscSms -> current.copy(login = login, password = password ?: current.password)
                is ChannelConfig.TelegramBot, is ChannelConfig.TwilioSms -> raise(providerMismatch())
            }
        is ChannelConfigChange.TelegramBot ->
            when (current) {
                is ChannelConfig.TelegramBot -> current.copy(botToken = botToken ?: current.botToken)
                is ChannelConfig.SmscSms, is ChannelConfig.TwilioSms -> raise(providerMismatch())
            }
        is ChannelConfigChange.TwilioSms ->
            when (current) {
                is ChannelConfig.TwilioSms ->
                    current.copy(accountSid = accountSid, authToken = authToken ?: current.authToken, from = from)
                is ChannelConfig.SmscSms, is ChannelConfig.TelegramBot -> raise(providerMismatch())
            }
    }

/** Ошибка «провайдера канала изменить нельзя». */
context(ctx: RequestContext)
private fun providerMismatch(): CommonDomainError = CommonDomainError("CHANNEL_PROVIDER_MISMATCH", Messages.ChannelProviderMismatch.localize())
