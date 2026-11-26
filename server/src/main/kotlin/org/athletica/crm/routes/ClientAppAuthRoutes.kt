package org.athletica.crm.routes

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.either
import com.auth0.jwt.interfaces.Payload
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.response.ResponseCookies
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.athletica.crm.api.schemas.clientapp.ClientAppLoginRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppRequestCodeRequest
import org.athletica.crm.api.schemas.clientapp.ClientAppRequestCodeResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppSessionsResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppTerminateSessionRequest
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.entityids.ClientAppAccountId
import org.athletica.crm.core.entityids.ClientAppSessionId
import org.athletica.crm.core.entityids.toClientAppAccountId
import org.athletica.crm.core.entityids.toClientAppSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.SessionRevoked
import org.athletica.crm.domain.clientapp.ClientAppConfig
import org.athletica.crm.domain.clientapp.auth.CodeIssue
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.auth.userAgentInfo
import org.athletica.crm.read.clientapp.ClientAppSessionsQuery
import org.athletica.crm.security.ClientAppJwtConfig
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.clientapp.ClientAppAccountDeletion
import org.athletica.crm.usecases.clientapp.ClientAppSignIn
import org.athletica.crm.usecases.clientapp.SignedIn
import org.athletica.crm.usecases.clientapp.VerificationCodeDelivery
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid

/** Имя провайдера аутентификации клиентского API. */
const val CLIENT_JWT = "client-jwt"

/**
 * Маршруты входа в кабинет `/client-app/auth/...`: запрос кода, вход и обновление — без токена,
 * выход — под [CLIENT_JWT]. Доставку кода [delivery] запускает в фоне [scope] после ответа.
 * Под [CLIENT_JWT] здесь же сеансы аккаунта из [views] с их завершением и удаление аккаунта [deletion]:
 * выход и удаление стирают cookie кабинета.
 */
context(jwt: ClientAppJwtConfig, config: ClientAppConfig)
fun Route.clientAppAuthRoutes(
    db: Database,
    views: ReadViews,
    signIn: ClientAppSignIn,
    deletion: ClientAppAccountDeletion,
    delivery: VerificationCodeDelivery,
    scope: CoroutineScope,
) {
    authenticate(CLIENT_JWT) {
        route("/client-app") {
            get("/sessions") {
                either {
                    val query = ClientAppSessionsQuery(Clock.System.now(), jwt.refreshTokenTtl)
                    context(call.clientAccountContext()) { db.transaction { views.clientAppSessions.sessions(query) } }
                }.fold({ call.respondError(it) }, { call.respond(it) })
            }.contract(getContract<Unit, ClientAppSessionsResponse>())

            post("/sessions/terminate") {
                either {
                    val request = call.body<ClientAppTerminateSessionRequest>()
                    context(call.clientAccountContext()) { signIn.terminate(request.sessionId) }
                }.fold({ call.respondError(it) }, { call.respond(Unit) })
            }.contract(postContract<ClientAppTerminateSessionRequest, Unit>())

            post("/account/delete") {
                either {
                    context(call.clientAccountContext()) { deletion.delete(Clock.System.now()) }
                    call.response.cookies.clearClientAppCookies()
                    call.respond(Unit)
                }.onLeft { call.respondError(it) }
            }.contract(postContract<Unit, Unit>())
        }
    }

    route("/client-app/auth") {
        post("/request-code") {
            either {
                val request = call.body<ClientAppRequestCodeRequest>()
                val issue = signIn.requestCode(request.phone, call.langFromRequest(), Clock.System.now())
                call.respond(ClientAppRequestCodeResponse(issue is CodeIssue.Issued, issue.retryAfter.inWholeSeconds, config.termsVersion))
                if (issue is CodeIssue.Issued) {
                    PhoneDigits.from(request.phone)?.let { phone -> scope.launch { delivery.deliver(phone, issue.id, issue.code, request.viaSms) } }
                }
            }.onLeft { call.respondError(it) }
        }.contract(postContract<ClientAppRequestCodeRequest, ClientAppRequestCodeResponse>())

        post("/login") {
            either {
                val request = call.body<ClientAppLoginRequest>()
                val signedIn =
                    signIn.login(
                        request.phone,
                        request.code,
                        request.acceptedTermsVersion,
                        sessionName(call.request.header(HttpHeaders.UserAgent)),
                        call.langFromRequest(),
                        Clock.System.now(),
                    )
                call.response.cookies.setClientAppCookies(signedIn)
                call.respond(Unit)
            }.onLeft { call.respondError(it) }
        }.contract(postContract<ClientAppLoginRequest, Unit>())

        post("/refresh") {
            either {
                val token = call.request.clientAppRefreshToken()
                val session = signIn.refresh(token.accountId, token.sessionId, token.jti, Clock.System.now())
                call.response.cookies.setClientAppCookies(SignedIn(token.accountId, session))
                call.respond(Unit)
            }.onLeft { call.respondError(it) }
        }.contract(postContract<Unit, Unit>())

        authenticate(CLIENT_JWT) {
            post("/logout") {
                either {
                    context(call.clientAccountContext()) { signIn.logout() }
                    call.response.cookies.clearClientAppCookies()
                    call.respond(Unit)
                }.onLeft { call.respondError(it) }
            }.contract(postContract<Unit, Unit>())
        }
    }
}

/**
 * Принципал провайдера [CLIENT_JWT] из проверенного [payload]: access токен с аккаунтом и сеансом,
 * сеанс которого жив по [isActive]. `null` — запрос получит 401.
 */
suspend fun clientAppPrincipal(
    payload: Payload,
    isActive: suspend (ClientAppSessionId, ClientAppAccountId) -> Boolean,
): JWTPrincipal? {
    val claims = payload.clientClaims() ?: return null
    if (payload.getClaim(JwtConfig.CLAIM_TYPE).asString() != JwtConfig.TYPE_ACCESS) {
        return null
    }
    return JWTPrincipal(payload).takeIf { isActive(claims.second, claims.first) }
}

/** Access токен кабинета из его cookie; заголовок `Authorization` клиентский API не принимает. */
fun ApplicationRequest.clientAppAuthHeader(): HttpAuthHeader? = cookies[ClientAppJwtConfig.COOKIE_ACCESS_TOKEN]?.let { HttpAuthHeader.Single("Bearer", it) }

/** Контекст аккаунта из принципала [CLIENT_JWT]; вызывается только под `authenticate(CLIENT_JWT)`. */
fun RoutingCall.clientAccountContext(): ClientAccountContext {
    val (accountId, sessionId) = checkNotNull(principal<JWTPrincipal>()?.payload?.clientClaims()) { "Нет принципала $CLIENT_JWT" }
    return ClientAccountContext(accountId, sessionId, langFromRequest())
}

/** Аккаунт и сеанс из claim-ов клиентского токена; `null`, если их нет. */
private fun Payload.clientClaims(): Pair<ClientAppAccountId, ClientAppSessionId>? {
    val accountId = getClaim(ClientAppJwtConfig.CLAIM_ACCOUNT_ID).asString()?.let { Uuid.parseOrNull(it) } ?: return null
    val sessionId = getClaim(JwtConfig.CLAIM_SESSION_ID).asString()?.let { Uuid.parseOrNull(it) } ?: return null
    return accountId.toClientAppAccountId() to sessionId.toClientAppSessionId()
}

/** Название сеанса для списка сеансов — браузер и система без версий (`Safari, iOS`) из [userAgent]. */
fun sessionName(userAgent: String?): String? {
    val info = userAgentInfo(userAgent)
    return listOfNotNull(info.browser?.substringBefore(' '), info.os).joinToString(", ").ifEmpty { null }
}

/** Проверенный refresh токен кабинета. */
private data class ClientAppRefreshToken(
    /** Аккаунт из claim `accountId`. */
    val accountId: ClientAppAccountId,
    /** Сеанс из claim `sid`. */
    val sessionId: ClientAppSessionId,
    /** Идентификатор токена — claim `jti`. */
    val jti: Uuid,
)

/** Refresh токен кабинета из его cookie; нет, не подписан, истёк или не refresh — [SessionRevoked] (HTTP 401). */
context(jwt: ClientAppJwtConfig, raise: Raise<AppError>)
private fun ApplicationRequest.clientAppRefreshToken(): ClientAppRefreshToken {
    val payload =
        cookies[ClientAppJwtConfig.COOKIE_REFRESH_TOKEN]
            ?.let { runCatching { jwt.verifier.verify(it) }.getOrNull() }
            ?.takeIf { it.getClaim(JwtConfig.CLAIM_TYPE).asString() == JwtConfig.TYPE_REFRESH }
            ?: raise(SessionRevoked)
    val (accountId, sessionId) = payload.clientClaims() ?: raise(SessionRevoked)
    val jti = payload.id?.let { Uuid.parseOrNull(it) } ?: raise(SessionRevoked)
    return ClientAppRefreshToken(accountId, sessionId, jti)
}

/**
 * Ставит HttpOnly cookie кабинета с токенами сеанса [signedIn]: `SameSite=Strict`, без `Domain` —
 * только хост кабинета; access — на `/api/client-app`, refresh — только на маршрут обновления.
 */
context(jwt: ClientAppJwtConfig, config: ClientAppConfig)
private fun ResponseCookies.setClientAppCookies(signedIn: SignedIn) {
    append(clientAppCookie(ClientAppJwtConfig.COOKIE_ACCESS_TOKEN, jwt.accessToken(signedIn.accountId, signedIn.session), jwt.accessTokenTtl, ACCESS_COOKIE_PATH))
    append(clientAppCookie(ClientAppJwtConfig.COOKIE_REFRESH_TOKEN, jwt.refreshToken(signedIn.accountId, signedIn.session), jwt.refreshTokenTtl, REFRESH_COOKIE_PATH))
}

/** Удаляет cookie кабинета (выход). */
context(config: ClientAppConfig)
private fun ResponseCookies.clearClientAppCookies() {
    append(clientAppCookie(ClientAppJwtConfig.COOKIE_ACCESS_TOKEN, "", Duration.ZERO, ACCESS_COOKIE_PATH))
    append(clientAppCookie(ClientAppJwtConfig.COOKIE_REFRESH_TOKEN, "", Duration.ZERO, REFRESH_COOKIE_PATH))
}

/** Cookie кабинета [name] со значением [value], временем жизни [maxAge] и путём [path]; нулевой [maxAge] удаляет cookie. */
context(config: ClientAppConfig)
private fun clientAppCookie(
    name: String,
    value: String,
    maxAge: Duration,
    path: String,
): Cookie =
    Cookie(
        name = name,
        value = value,
        maxAge = maxAge.inWholeSeconds.toInt(),
        httpOnly = true,
        secure = config.secureCookies,
        path = path,
        extensions = mapOf("SameSite" to "Strict"),
    )

/** Путь access cookie кабинета: всё клиентское API. */
private const val ACCESS_COOKIE_PATH = "/api/client-app"

/** Путь refresh cookie кабинета: только маршрут обновления. */
private const val REFRESH_COOKIE_PATH = "/api/client-app/auth/refresh"
