package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.clientapp.ClientAppAbsenceNoticeRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppProfileRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppScheduleResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.clientapp.ClientAppAbsenceNotices
import org.athletica.crm.domain.clientapp.ClientAppAccesses
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.clientapp.ClientAppMembershipsQuery
import org.athletica.crm.read.clientapp.ClientAppScheduleQuery
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.usecases.clientapp.ClientAppConnections
import kotlin.time.Clock

/**
 * Клиентское API кабинета `/client-app/...` под [CLIENT_JWT]: приглашения и ответ на них, профили
 * аккаунта, данные выбранного профиля (главная, баланс, абонементы, расписание) и «Не придём» через [notices].
 * Запрос по профилю отвечает только по подключённому доступу (`CLIENT_NOT_FOUND` иначе) и после ответа
 * отмечает активность доступа в [accesses].
 */
fun Route.clientAppRoutes(
    db: Database,
    views: ReadViews,
    connections: ClientAppConnections,
    accesses: ClientAppAccesses,
    notices: ClientAppAbsenceNotices,
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
            profileGet("/memberships", db, accesses) { views.clientAppMemberships.memberships(ClientAppMembershipsQuery(Clock.System.now())) }
            profileGet<ClientAppScheduleRequest, ClientAppScheduleResponse>("/schedule", db, accesses, { ClientAppProfileRequest(it.orgId, it.clientId) }) {
                views.clientAppSchedule.schedule(ClientAppScheduleQuery(it.weekOf, Clock.System.now()))
            }

            profilePost<ClientAppAbsenceNoticeRequest>("/absence-notices/create", db, accesses, { ClientAppProfileRequest(it.orgId, it.clientId) }) {
                notices.notify(it.sessionId, Clock.System.now())
            }
            profilePost<ClientAppAbsenceNoticeRequest>("/absence-notices/cancel", db, accesses, { ClientAppProfileRequest(it.orgId, it.clientId) }) {
                notices.cancel(it.sessionId, Clock.System.now())
            }
        }
    }
}

/** GET-маршрут [path] данных профиля по запросу [ClientAppProfileRequest]; см. общий `profileGet`. */
private inline fun <reified Res : Any> Route.profileGet(
    path: String,
    db: Database,
    accesses: ClientAppAccesses,
    crossinline body: suspend context(ClientProfileContext, Transaction, Raise<AppError>) () -> Res,
): Route = profileGet<ClientAppProfileRequest, Res>(path, db, accesses, { it }) { body() }

/**
 * GET-маршрут [path] данных профиля: строит [ClientProfileContext] по профилю [profileOf] запроса из query
 * (нет подключённого доступа — `CLIENT_NOT_FOUND`), отвечает результатом [body] и после ответа
 * отмечает активность доступа в [accesses].
 */
private inline fun <reified Req : Any, reified Res : Any> Route.profileGet(
    path: String,
    db: Database,
    accesses: ClientAppAccesses,
    crossinline profileOf: (Req) -> ClientAppProfileRequest,
    crossinline body: suspend context(ClientProfileContext, Transaction, Raise<AppError>) (Req) -> Res,
): Route =
    get(path) {
        either<AppError, Unit> {
            val request = call.request.queryParameters.decode<Req>()
            val key = profileOf(request)
            context(call.clientAccountContext()) {
                val (profile, response) =
                    db.transaction {
                        val profile = ClientProfileContexts.profile(key.orgId, key.clientId)
                        profile to context(profile) { body(request) }
                    }
                call.respond(response)
                context(profile) { db.transaction { accesses.touch(Clock.System.now()) } }
            }
        }.onLeft { call.respondError(it) }
    }.contract(getContract<Req, Res>())

/**
 * POST-действие [path] над данными профиля: строит [ClientProfileContext] по профилю [profileOf] тела запроса
 * (нет подключённого доступа — `CLIENT_NOT_FOUND`), выполняет [body] в транзакции и после ответа
 * отмечает активность доступа в [accesses].
 */
private inline fun <reified Req : Any> Route.profilePost(
    path: String,
    db: Database,
    accesses: ClientAppAccesses,
    crossinline profileOf: (Req) -> ClientAppProfileRequest,
    crossinline body: suspend context(ClientProfileContext, Transaction, Raise<AppError>) (Req) -> Unit,
): Route =
    post(path) {
        either<AppError, Unit> {
            val request = call.body<Req>()
            val key = profileOf(request)
            context(call.clientAccountContext()) {
                val profile =
                    db.transaction {
                        ClientProfileContexts.profile(key.orgId, key.clientId).also { context(it) { body(request) } }
                    }
                call.respond(Unit)
                context(profile) { db.transaction { accesses.touch(Clock.System.now()) } }
            }
        }.onLeft { call.respondError(it) }
    }.contract(postContract<Req, Unit>())
