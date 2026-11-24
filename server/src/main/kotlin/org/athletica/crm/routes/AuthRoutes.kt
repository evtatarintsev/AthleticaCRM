package org.athletica.crm.routes

import arrow.core.raise.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import arrow.core.raise.either
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.ResponseCookies
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.post
import org.athletica.crm.api.schemas.auth.AuthBranchesRequest
import org.athletica.crm.api.schemas.auth.AuthBranchesResponse
import org.athletica.crm.api.schemas.auth.ConfirmEmailRequest
import org.athletica.crm.api.schemas.auth.ConfirmEmailResponse
import org.athletica.crm.api.schemas.auth.EmailConfirmationStatus
import org.athletica.crm.api.schemas.auth.ForgotPasswordRequest
import org.athletica.crm.api.schemas.auth.LoginRequest
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.ResetPasswordRequest
import org.athletica.crm.api.schemas.auth.SessionListResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.auth.SwitchBranchRequest
import org.athletica.crm.api.schemas.auth.TerminateSessionRequest
import org.athletica.crm.api.schemas.branches.BranchDetailResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toAuthSessionId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.SessionRevoked
import org.athletica.crm.domain.auth.Auth
import org.athletica.crm.domain.auth.AuthSessions
import org.athletica.crm.domain.auth.ConfirmResult
import org.athletica.crm.domain.auth.EmailVerifications
import org.athletica.crm.domain.auth.IssuedSession
import org.athletica.crm.domain.auth.PasswordResets
import org.athletica.crm.domain.auth.SignUp
import org.athletica.crm.domain.branch.Branches
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.auth.SessionListQuery
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid

context(db: Database, jwtConfig: JwtConfig)
fun Route.authRoutes(
    auth: Auth,
    emailVerifications: EmailVerifications,
    passwordResets: PasswordResets,
    sessions: AuthSessions,
) {
    post("/auth/confirm-email") {
        either {
            val request = call.body<ConfirmEmailRequest>()
            val result = db.transaction { emailVerifications.confirm(request.token, call.langFromRequest(), call.clientIp()) }
            ConfirmEmailResponse(
                when (result) {
                    ConfirmResult.CONFIRMED -> EmailConfirmationStatus.CONFIRMED
                    ConfirmResult.ALREADY_CONFIRMED -> EmailConfirmationStatus.ALREADY_CONFIRMED
                },
            )
        }.fold({ call.respondError(it) }, { call.respond(it) })
    }.contract(postContract<ConfirmEmailRequest, ConfirmEmailResponse>())

    post("/auth/forgot-password") {
        either {
            val request = call.body<ForgotPasswordRequest>()
            db.transaction { passwordResets.request(request.email, call.langFromRequest()) }
        }.fold({ call.respondError(it) }, { call.respond(Unit) })
    }.contract(postContract<ForgotPasswordRequest, Unit>())

    post("/auth/reset-password") {
        either {
            val request = call.body<ResetPasswordRequest>()
            db.transaction { passwordResets.reset(request.token, request.newPassword, call.langFromRequest(), call.clientIp()) }
        }.fold({ call.respondError(it) }, { call.respond(Unit) })
    }.contract(postContract<ResetPasswordRequest, Unit>())

    post("/auth/branches") {
        val request = call.receive<AuthBranchesRequest>()
        either {
            db.transaction {
                val available = auth.availableBranches(request.username, request.password, call.langFromRequest(), call.clientIp())
                AuthBranchesResponse(available.map { BranchDetailResponse(it.id, it.name) })
            }
        }.fold(
            { call.respondError(it) },
            { call.respond(it) },
        )
    }.contract(postContract<AuthBranchesRequest, AuthBranchesResponse>())

    post("/auth/sign-up") {
        call.eitherToAuthResponse {
            val request = call.body<SignUpRequest>()
            db.transaction {
                val user =
                    auth.signup(
                        SignUp(request.companyName, request.userName, request.login, request.password, request.timezone, request.currency),
                        call.langFromRequest(),
                        call.clientIp(),
                    )
                user to call.newSession(sessions, user)
            }
        }
    }.contract(postContract<SignUpRequest, LoginResponse>())

    post("/auth/login") {
        call.eitherToAuthResponse {
            val request = call.receive<LoginRequest>()
            db.transaction {
                val user = auth.login(request.username, request.password, request.branchId, call.langFromRequest(), call.clientIp())
                user to call.newSession(sessions, user)
            }
        }
    }.contract(postContract<LoginRequest, LoginResponse>())

    post("/auth/refresh-token") {
        call.eitherToAuthResponse {
            val token = call.request.refreshToken().verifiedJwtToken(jwtConfig)
            val (userId, branchId) = token.userIdAndBranchClaim()
            val (sessionId, jti) = token.sessionAndJtiClaim()
            db.transaction {
                sessions
                    .rotate(sessionId, jti, call.clientIp(), Clock.System.now())
                    ?.let { auth.userById(userId, branchId) to it }
            } ?: raise(CommonDomainError("SESSION_REVOKED", "Session is revoked"))
        }
    }.contract(postContract<Unit, LoginResponse>())
}

context(db: Database)
fun RouteWithContext.logout(auth: Auth) {
    post<Unit, Unit>("/auth/logout") { _, call ->
        db.transaction {
            auth.logout()
        }
        call.response.cookies.clearJwtCookies()
    }
}

/**
 * Регистрирует маршруты сеансов текущего пользователя из [sessions] и проекции [views]:
 * GET /auth/sessions — список, POST /auth/sessions/terminate — завершить один не текущий,
 * POST /auth/sessions/terminate-others — завершить все, кроме текущего.
 */
context(db: Database, jwtConfig: JwtConfig)
fun RouteWithContext.authSessionsRoutes(sessions: AuthSessions, views: ReadViews) {
    get<Unit, SessionListResponse>("/auth/sessions") {
        val ctx = contextOf<EmployeeRequestContext>()
        db.transaction {
            views.sessionList.list(SessionListQuery(ctx.employee.id, Clock.System.now(), jwtConfig.refreshTokenTtl))
        }
    }

    post<TerminateSessionRequest, Unit>("/auth/sessions/terminate") { request ->
        val ctx = contextOf<EmployeeRequestContext>()
        ensure(request.sessionId != ctx.sessionId) {
            CommonDomainError("CANNOT_TERMINATE_CURRENT_SESSION", Messages.CannotTerminateCurrentSession.localize())
        }
        db.transaction { sessions.delete(request.sessionId, ctx.userId) }
    }

    post<Unit, Unit>("/auth/sessions/terminate-others") {
        val ctx = contextOf<EmployeeRequestContext>()
        db.transaction { sessions.deleteOthers(ctx.userId, ctx.sessionId) }
    }
}

/**
 * Регистрирует маршрут GET /auth/my-branches.
 * Возвращает список филиалов, доступных текущему аутентифицированному сотруднику.
 */
context(db: Database, branches: Branches)
fun RouteWithContext.myBranchesRoute() {
    get<Unit, AuthBranchesResponse>("/auth/my-branches") {
        db.transaction {
            AuthBranchesResponse(branches.list().map { BranchDetailResponse(it.id, it.name) })
        }
    }
}

/**
 * Регистрирует маршрут POST /auth/switch-branch.
 * Переключает текущего пользователя на другой доступный филиал
 * и выпускает новые JWT-токены с обновлённым [branchId] в той же сессии из [sessions].
 */
context(db: Database, jwtConfig: JwtConfig)
fun RouteWithContext.switchBranchRoute(auth: Auth, sessions: AuthSessions) {
    post<SwitchBranchRequest, LoginResponse>("/auth/switch-branch") { request, call ->
        val sessionId = contextOf<EmployeeRequestContext>().sessionId ?: raise(SessionRevoked)
        val (user, session) =
            db.transaction {
                auth.switchBranch(request.branchId) to sessions.reissue(sessionId, Clock.System.now())
            }
        val newAccessToken = jwtConfig.makeAccessToken(user, session.id)
        val newRefreshToken = jwtConfig.makeRefreshToken(user.id, user.branchId, session)
        call.response.cookies.setJwtCookies(newAccessToken, newRefreshToken)
        LoginResponse(accessToken = newAccessToken, refreshToken = newRefreshToken)
    }
}

/**
 * Выполняет [block] в контексте [Raise], при успехе выпускает новые JWT-токены пользователя
 * в его сессии и отвечает [LoginResponse]. Ошибку передаёт в [respondError].
 */
context(jwtConfig: JwtConfig)
suspend inline fun RoutingCall.eitherToAuthResponse(block: Raise<AppError>.() -> Pair<AuthenticatedUser, IssuedSession>) {
    either {
        val (user, session) = block()
        val newAccessToken = jwtConfig.makeAccessToken(user, session.id)
        val newRefreshToken = jwtConfig.makeRefreshToken(user.id, user.branchId, session)

        response.cookies.setJwtCookies(newAccessToken, newRefreshToken)
        respond(LoginResponse(accessToken = newAccessToken, refreshToken = newRefreshToken))
    }.onLeft { respondError(it) }
}

/**
 * Верифицирует JWT-токен с помощью [jwtConfig].
 * Возвращает декодированный токен; если подпись невалидна либо токен истёк — поднимает ошибку.
 */
context(raise: Raise<AppError>)
fun String.verifiedJwtToken(jwtConfig: JwtConfig): DecodedJWT =
    runCatching { jwtConfig.verifier.verify(this) }
        .getOrNull() ?: raise(CommonDomainError("", ""))

/**
 * Извлекает refresh-токен из запроса.
 * Сначала ищет в заголовке `X-Refresh-Token`, затем в cookie [JwtConfig.COOKIE_REFRESH_TOKEN].
 * Возвращает токен; если он отсутствует — поднимает ошибку.
 */
context(raise: Raise<AppError>)
fun ApplicationRequest.refreshToken(): String =
    call.request.header("X-Refresh-Token")
        ?: call.request.cookies[JwtConfig.COOKIE_REFRESH_TOKEN]
        ?: raise(CommonDomainError("", ""))

/**
 * Извлекает идентификатор пользователя и филиал из claim-ов токена.
 * Проверяет, что тип токена — [JwtConfig.TYPE_REFRESH], иначе поднимает ошибку.
 */
context(raise: Raise<AppError>)
fun DecodedJWT.userIdAndBranchClaim(): Pair<UserId, BranchId> {
    if (getClaim(JwtConfig.CLAIM_TYPE).asString() != JwtConfig.TYPE_REFRESH) {
        raise(CommonDomainError("", ""))
    }
    val userId = Uuid.parse(getClaim(JwtConfig.CLAIM_USER_ID).asString()).toUserId()
    val branchId = Uuid.parse(getClaim(JwtConfig.CLAIM_BRANCH_ID).asString()).toBranchId()
    return userId to branchId
}

/**
 * Открывает в [sessions] новую сессию пользователя [user] с User-Agent и IP этого запроса;
 * заодно удаляет его сессии, неактивные дольше срока жизни refresh токена.
 */
context(tr: Transaction, raise: Raise<AppError>, jwtConfig: JwtConfig)
suspend fun RoutingCall.newSession(sessions: AuthSessions, user: AuthenticatedUser): IssuedSession {
    val now = Clock.System.now()
    return sessions.new(user.id, user.orgId, request.header(HttpHeaders.UserAgent), clientIp(), now, now - jwtConfig.refreshTokenTtl)
}

/**
 * Извлекает из refresh токена сессию и идентификатор самого токена (`jti`).
 * Токен без них поднимает ошибку.
 */
context(raise: Raise<AppError>)
fun DecodedJWT.sessionAndJtiClaim(): Pair<AuthSessionId, Uuid> {
    val sessionId = getClaim(JwtConfig.CLAIM_SESSION_ID).asString()?.let { Uuid.parseOrNull(it) }
    val jti = id?.let { Uuid.parseOrNull(it) }
    if (sessionId == null || jti == null) {
        raise(CommonDomainError("SESSION_REVOKED", "Session is revoked"))
    }
    return sessionId.toAuthSessionId() to jti
}

/**
 * Устанавливает HttpOnly cookies с JWT токенами [accessToken] и [refreshToken].
 * Cookies живут столько же, сколько токены ([JwtConfig.accessTokenTtl], [JwtConfig.refreshTokenTtl]):
 * без `Max-Age` они сессионные, и мобильные браузеры теряют их при выгрузке вкладки из памяти.
 */
context(jwtConfig: JwtConfig)
fun ResponseCookies.setJwtCookies(
    accessToken: String,
    refreshToken: String,
) {
    append(jwtCookie(JwtConfig.COOKIE_ACCESS_TOKEN, accessToken, jwtConfig.accessTokenTtl, "/"))
    append(jwtCookie(JwtConfig.COOKIE_REFRESH_TOKEN, refreshToken, jwtConfig.refreshTokenTtl, REFRESH_TOKEN_COOKIE_PATH))
}

/** Удаляет cookies с JWT токенами (используется при logout). */
fun ResponseCookies.clearJwtCookies() {
    append(jwtCookie(JwtConfig.COOKIE_ACCESS_TOKEN, "", Duration.ZERO, "/"))
    append(jwtCookie(JwtConfig.COOKIE_REFRESH_TOKEN, "", Duration.ZERO, REFRESH_TOKEN_COOKIE_PATH))
}

/**
 * HttpOnly cookie [name] со значением [value], временем жизни [maxAge] и путём [path].
 * Нулевой [maxAge] велит браузеру удалить cookie.
 */
private fun jwtCookie(
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
        path = path,
        extensions = mapOf("SameSite" to "Strict"),
    )

/** Путь, на который браузер отправляет cookie с refresh токеном. */
private const val REFRESH_TOKEN_COOKIE_PATH = "/api/auth/refresh-token"
