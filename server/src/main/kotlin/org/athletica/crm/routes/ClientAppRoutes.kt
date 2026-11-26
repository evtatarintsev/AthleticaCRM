package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppProfileRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientapp.ClientAppAccesses
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.usecases.clientapp.ClientAppConnections
import kotlin.time.Clock

/**
 * Клиентское API кабинета `/client-app/...` под [CLIENT_JWT]: приглашения и ответ на них, профили
 * аккаунта и данные выбранного профиля (главная, баланс). Запрос по профилю отвечает только по подключённому доступу
 * (`CLIENT_NOT_FOUND` иначе) и после ответа отмечает активность доступа в [accesses].
 */
fun Route.clientAppRoutes(
    db: Database,
    views: ReadViews,
    connections: ClientAppConnections,
    accesses: ClientAppAccesses,
) {
    authenticate(CLIENT_JWT) {
        route("/client-app") {
            get("/invitations") {
                either {
                    context(call.clientAccountContext()) { db.transaction { views.clientAppInvitations.invitations() } }
                }.fold({ call.respondError(it) }, { call.respond(it) })
            }.contract(getContract<Unit, ClientAppInvitationsResponse>())

            post("/connect") {
                either {
                    val request = call.body<ClientAppInvitationRequest>()
                    context(call.clientAccountContext()) { connections.connect(request.orgId) }
                }.fold({ call.respondError(it) }, { call.respond(Unit) })
            }.contract(postContract<ClientAppInvitationRequest, Unit>())

            post("/not-me") {
                either {
                    val request = call.body<ClientAppInvitationRequest>()
                    context(call.clientAccountContext()) { connections.notMe(request.orgId) }
                }.fold({ call.respondError(it) }, { call.respond(Unit) })
            }.contract(postContract<ClientAppInvitationRequest, Unit>())

            get("/profiles") {
                either {
                    context(call.clientAccountContext()) { db.transaction { views.clientAppProfiles.profiles() } }
                }.fold({ call.respondError(it) }, { call.respond(it) })
            }.contract(getContract<Unit, ClientAppProfilesResponse>())

            profileGet("/home", db, accesses) { views.clientAppHome.home() }
            profileGet("/balance", db, accesses) { views.clientAppBalance.balance() }
        }
    }
}

/**
 * GET-маршрут [path] данных профиля: строит [ClientProfileContext] по `orgId` и `clientId` из query
 * (нет подключённого доступа — `CLIENT_NOT_FOUND`), отвечает результатом [body] и после ответа
 * отмечает активность доступа в [accesses].
 */
private inline fun <reified Res : Any> Route.profileGet(
    path: String,
    db: Database,
    accesses: ClientAppAccesses,
    crossinline body: suspend context(ClientProfileContext, Transaction, Raise<AppError>) () -> Res,
): Route =
    get(path) {
        either<AppError, Unit> {
            val request = call.request.queryParameters.decode<ClientAppProfileRequest>()
            context(call.clientAccountContext()) {
                val (profile, response) =
                    db.transaction {
                        val profile = ClientProfileContexts.profile(request.orgId, request.clientId)
                        profile to context(profile) { body() }
                    }
                call.respond(response)
                context(profile) { db.transaction { accesses.touch(Clock.System.now()) } }
            }
        }.onLeft { call.respondError(it) }
    }.contract(getContract<ClientAppProfileRequest, Res>())
