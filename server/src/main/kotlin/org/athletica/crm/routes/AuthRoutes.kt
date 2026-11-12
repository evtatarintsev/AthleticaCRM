package org.athletica.crm.routes

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.either
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.Cookie
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
import org.athletica.crm.api.schemas.auth.LoginRequest
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.auth.SwitchBranchRequest
import org.athletica.crm.api.schemas.branches.BranchDetailResponse
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.auth.Auth
import org.athletica.crm.domain.branch.Branches
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.storage.Database
import kotlin.time.Duration
import kotlin.uuid.Uuid

context(db: Database, jwtConfig: JwtConfig)
fun Route.authRoutes(auth: Auth) {
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
                auth.signup(request, call.langFromRequest(), call.clientIp())
            }
        }
    }.contract(postContract<SignUpRequest, LoginResponse>())

    post("/auth/login") {
        call.eitherToAuthResponse {
            val request = call.receive<LoginRequest>()
            db.transaction {
                auth.login(request.username, request.password, request.branchId, call.langFromRequest(), call.clientIp())
            }
        }
    }.contract(postContract<LoginRequest, LoginResponse>())

    post("/auth/refresh-token") {
        call.eitherToAuthResponse {
            db.transaction {
                call.request
                    .refreshToken()
                    .verifiedJwtToken(jwtConfig)
                    .userIdAndBranchClaim()
                    .let { (userId, branchId) -> auth.userById(userId, branchId) }
            }
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
 * и выпускает новые JWT-токены с обновлённым [branchId].
 */
context(db: Database, jwtConfig: JwtConfig)
fun RouteWithContext.switchBranchRoute(auth: Auth) {
    post<SwitchBranchRequest, LoginResponse>("/auth/switch-branch") { request, call ->
        val user =
            db.transaction {
                auth.switchBranch(request.branchId)
            }
        val newAccessToken = jwtConfig.makeAccessToken(user)
        val newRefreshToken = jwtConfig.makeRefreshToken(user.id, user.branchId)
        call.response.cookies.setJwtCookies(newAccessToken, newRefreshToken)
        LoginResponse(accessToken = newAccessToken, refreshToken = newRefreshToken)
    }
}

/**
 * Выполняет [block] в контексте [Raise], при успехе выпускает новые JWT-токены и отвечает [LoginResponse].
 * Ошибку передаёт в [respondError].
 */
context(jwtConfig: JwtConfig)
suspend inline fun RoutingCall.eitherToAuthResponse(block: Raise<AppError>.() -> AuthenticatedUser) {
    either {
        val user = block()
        val newAccessToken = jwtConfig.makeAccessToken(user)
        val newRefreshToken = jwtConfig.makeRefreshToken(user.id, user.branchId)

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
