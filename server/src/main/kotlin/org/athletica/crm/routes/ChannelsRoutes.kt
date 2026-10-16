package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.api.schemas.channels.CreateChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.DeleteChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.UpdateChannelIntegrationRequest
import org.athletica.crm.domain.channels.ChannelConfigVerifier
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты настройки интеграций каналов связи. Настройки канала проверяются у провайдера
 * через [ChannelConfigVerifier] до открытия транзакции.
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
            val config = verifier.verified(request.config)
            db.transaction {
                channels.add(id = request.id, name = request.name, config = config)
            }
        }

        post<UpdateChannelIntegrationRequest, Unit>("/update") { request ->
            val config = verifier.verified(request.config)
            db.transaction {
                channels.update(id = request.id, name = request.name, config = config, enabled = request.enabled)
            }
        }

        post<DeleteChannelIntegrationRequest, Unit>("/delete") { request ->
            db.transaction {
                channels.remove(request.id)
            }
        }
    }
}
