package org.athletica.crm.read.channels

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.channels.ChannelIntegrationSchema
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.api.schemas.channels.ChannelSettingsSchema
import org.athletica.crm.api.schemas.channels.ChannelShareSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.toChannelConfig
import org.athletica.crm.core.secretMask
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [ChannelListView] поверх PostgreSQL. */
class DbChannelListView : ChannelListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun list(): ChannelListResponse =
        ChannelListResponse(
            tr
                .sql(
                    """
                    SELECT id, name, config::text AS config, enabled
                    FROM channel_integrations
                    WHERE org_id = :orgId
                    ORDER BY created_at
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row -> row.toSchema() },
        )

    /** Собирает элемент списка из строки выборки. */
    private fun Row.toSchema(): ChannelIntegrationSchema {
        val config = asString("config").toChannelConfig()
        return ChannelIntegrationSchema(
            id = asUuid("id").toChannelIntegrationId(),
            name = asString("name"),
            settings = config.settings(),
            enabled = asBoolean("enabled"),
            share = config.share(),
        )
    }

    /** Настройки провайдера для показа: токены — маской, пароль SMSC не передаётся. */
    private fun ChannelConfig.settings(): ChannelSettingsSchema =
        when (this) {
            is ChannelConfig.SmscSms -> ChannelSettingsSchema.SmscSms(login = login)
            is ChannelConfig.TelegramBot -> ChannelSettingsSchema.TelegramBot(tokenMask = botToken.secretMask(), botUsername = botUsername)
            is ChannelConfig.TwilioSms ->
                ChannelSettingsSchema.TwilioSms(accountSid = accountSid, authTokenMask = authToken.secretMask(), from = from)
        }

    /** Ссылка на бота для каналов-ботов с известным именем; `null` для остальных. */
    private fun ChannelConfig.share(): ChannelShareSchema? =
        when (this) {
            is ChannelConfig.TelegramBot -> botUsername.takeIf { it.isNotBlank() }?.let { ChannelShareSchema("https://t.me/$it") }
            is ChannelConfig.SmscSms, is ChannelConfig.TwilioSms -> null
        }
}
