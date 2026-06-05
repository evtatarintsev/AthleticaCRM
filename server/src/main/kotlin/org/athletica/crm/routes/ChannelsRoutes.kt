package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.channels.ChannelIntegrationSchema
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.api.schemas.channels.CreateChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.DeleteChannelIntegrationRequest
import org.athletica.crm.api.schemas.channels.UpdateChannelIntegrationRequest
import org.athletica.crm.domain.channels.ChannelIntegration
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты настройки интеграций каналов связи.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.channelsRoutes(channels: ChannelIntegrations) {
    route("/channels") {
        get<Unit, ChannelListResponse>("/list") {
            db.transaction {
                ChannelListResponse(channels.list().map { it.toSchema() })
            }
        }

        post<CreateChannelIntegrationRequest, Unit>("/create") { request ->
            db.transaction {
                channels.create(
                    ChannelIntegration(
                        id = request.id,
                        name = request.name,
                        config = request.config,
                        enabled = true,
                    ),
                )
            }
        }

        post<UpdateChannelIntegrationRequest, Unit>("/update") { request ->
            db.transaction {
                val existing = channels.byId(request.id)
                channels.update(
                    existing.copy(
                        name = request.name,
                        config = request.config,
                        enabled = request.enabled,
                    ),
                )
            }
        }

        post<DeleteChannelIntegrationRequest, Unit>("/delete") { request ->
            db.transaction {
                channels.delete(request.id)
            }
        }
    }
}

private fun ChannelIntegration.toSchema(): ChannelIntegrationSchema =
    ChannelIntegrationSchema(
        id = id,
        channelType = channelType,
        name = name,
        config = config,
        enabled = enabled,
    )
