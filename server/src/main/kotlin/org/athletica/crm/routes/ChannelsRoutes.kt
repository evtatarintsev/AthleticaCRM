package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.api.schemas.channels.CreateChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.DeleteChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.NewChannelConfig
import org.athletica.crm.api.schemas.channels.SetChannelEnabledRequest
import org.athletica.crm.api.schemas.channels.UpdateChannelIntegrationRequest
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelConfigVerifier
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.channels.updateChannel

/**
 * Регистрирует маршруты настройки интеграций каналов связи. Настройки нового канала и изменённые
 * учётные данные проверяются у провайдера через [ChannelConfigVerifier] до открытия транзакции;
 * включение и выключение канала провайдера не затрагивает.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.channelsRoutes(
    channels: ChannelIntegrations,
    verifier: ChannelConfigVerifier,
    views: ReadViews,
) {
    route("/channels") {
        get<Unit, ChannelListResponse>("/list") {
            db.transaction {
                views.channelList.list()
            }
        }

        post<CreateChannelIntegrationRequest, Unit>("/create") { request ->
            val config = verifier.verified(request.config.toChannelConfig())
            db.transaction {
                channels.add(id = request.id, name = request.name, config = config)
            }
        }

        post<UpdateChannelIntegrationRequest, Unit>("/update") { request ->
            updateChannel(channels, verifier, request.id, request.name, request.config)
        }

        post<SetChannelEnabledRequest, Unit>("/set-enabled") { request ->
            db.transaction {
                channels.setEnabled(request.id, request.enabled)
            }
        }

        post<DeleteChannelIntegrationRequest, Unit>("/delete") { request ->
            db.transaction {
                channels.remove(request.id)
            }
        }
    }
}

/** Доменный конфиг нового канала; имя Telegram-бота заполнит проверка у провайдера. */
private fun NewChannelConfig.toChannelConfig(): ChannelConfig =
    when (this) {
        is NewChannelConfig.SmscSms -> ChannelConfig.SmscSms(login = login, password = password)
        is NewChannelConfig.TelegramBot -> ChannelConfig.TelegramBot(botToken = botToken)
        is NewChannelConfig.TwilioSms -> ChannelConfig.TwilioSms(accountSid = accountSid, authToken = authToken, from = from)
    }
