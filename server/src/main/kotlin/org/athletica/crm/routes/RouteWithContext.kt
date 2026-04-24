package org.athletica.crm.routes

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.either
import com.auth0.jwt.interfaces.Payload
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.route
import io.ktor.util.reflect.typeInfo
import org.athletica.crm.Di
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.EmployeePermissions
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.storage.Database
import kotlin.uuid.Uuid

class RouteWithContext(val di: Di, val router: Route) {
    fun route(path: String, build: RouteWithContext.() -> Unit): Route =
        router.route(path) {
            RouteWithContext(di, this).apply(build)
        }

    fun get(
        path: String,
        body: suspend context(RequestContext) RoutingContext.() -> Unit,
    ): Route =
        router.route(path, HttpMethod.Get) {
            handle {
                context(call.contextFromRequest(di.database, di.employeePermissions)) {
                    body()
                }
            }
        }

    inline fun <reified Req, reified Res> post(
        path: String,
        crossinline body: suspend context(RequestContext) Raise<DomainError>.(Req) -> Res,
    ): Route =
        postRoute(path) { call ->
            val response = body(call.body<Req>())
            call.respond(response, typeInfo<Res>())
        }

    @JvmName("postWithUnitResponse")
    inline fun <reified Req, reified Res : Unit> post(
        path: String,
        crossinline body: suspend context(RequestContext) Raise<DomainError>.(Req) -> Unit,
    ): Route =
        postRoute(path) { call ->
            body(call.body<Req>())
            call.respond(Unit)
        }

    inline fun <reified Req, reified Res> post(
        path: String,
        crossinline body: suspend context(RequestContext) Raise<DomainError>.(Req, RoutingCall) -> Res,
    ): Route =
        postRoute(path) { call ->
            val response = body(call.body<Req>(), call)
            call.respond(response, typeInfo<Res>())
        }

    @JvmName("postWithUnitRequestResponse")
    inline fun <reified Req : Unit, reified Res : Unit> post(
        path: String,
        crossinline body: suspend context(RequestContext) Raise<DomainError>.(Unit) -> Res,
    ): Route =
        postRoute(path) { call ->
            body(Unit)
            call.respond(Unit)
        }

    @JvmName("postWithUnitRequestResponseAndCall")
    inline fun <reified Req : Unit, reified Res : Unit> post(
        path: String,
        crossinline body: suspend context(RequestContext) Raise<DomainError>.(Unit, RoutingCall) -> Unit,
    ): Route =
        postRoute(path) { call ->
            body(Unit, call)
            call.respond(Unit)
        }

    @JvmName("postWithUnitRequestAndCall")
    inline fun <reified Req : Unit, reified Res> post(
        path: String,
        crossinline body: suspend context(RequestContext) Raise<DomainError>.(Unit, RoutingCall) -> Res,
    ): Route =
        postRoute(path) { call ->
            val response = body(Unit, call)
            call.respond(response, typeInfo<Res>())
        }

    inline fun postRoute(
        path: String,
        crossinline body: suspend context(RequestContext) Raise<DomainError>.(RoutingCall) -> Unit,
    ): Route =
        router.route(path, HttpMethod.Post) {
            handle {
                context(call.contextFromRequest(di.database, di.employeePermissions)) {
                    either {
                        body(call)
                    }.onLeft {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(code = it.code, message = it.message),
                        )
                    }
                }
            }
        }
}

context(raise: Raise<DomainError>)
suspend inline fun <reified T> RoutingCall.body() =
    try {
        receive<T>(typeInfo<T>())
    } catch (e: Exception) {
        raise(CommonDomainError("INVALID_REQUEST", "Invalid request body"))
    }

fun Route.routeWithContext(di: Di, block: RouteWithContext.() -> Unit) {
    RouteWithContext(di, this).apply(block)
}

/**
 * Определяет язык из заголовка `Accept-Language` (RFC 7231).
 * Берёт первый тег с наивысшим приоритетом и сопоставляет с поддерживаемыми [Lang].
 * Если заголовок отсутствует или язык не поддерживается — возвращает [Lang.default()].
 */
fun RoutingCall.langFromRequest(): Lang {
    val header = request.header("Accept-Language") ?: return Lang.default()
    val primaryTag =
        header
            .splitToSequence(",")
            .map { it.trim().substringBefore(";").substringBefore("-").lowercase() }
            .firstOrNull() ?: return Lang.default()
    return Lang.entries.firstOrNull { it.code == primaryTag } ?: Lang.default()
}

/**
 * Собирает [RequestContext] из JWT-токена и заголовков запроса.
 * Извлекает [UserId], [EmployeeId] и [OrgId] из claims токена, язык — из `Accept-Language`,
 * IP-адрес клиента — из `X-Forwarded-For` или прямого подключения.
 */
suspend fun RoutingCall.contextFromRequest(db: Database, permissions: EmployeePermissions): RequestContext {
    val principal = principal<JWTPrincipal>()!!
    val userId = principal.payload.claimAsUuid(JwtConfig.CLAIM_USER_ID).toUserId()
    val orgId = principal.payload.claimAsUuid(JwtConfig.CLAIM_ORG_ID).toOrgId()
    val employeeId = principal.payload.claimAsUuid(JwtConfig.CLAIM_EMPLOYEE_ID).toEmployeeId()
    return RequestContext(
        userId = userId,
        orgId = orgId,
        employeeId = employeeId,
        lang = langFromRequest(),
        username = principal.payload.getClaim(JwtConfig.CLAIM_USERNAME).asString(),
        clientIp = clientIp(),
        permission = db.transaction { permissions.byId(employeeId) },
    )
}

fun Payload.claimAsUuid(claim: String) = Uuid.parse(getClaim(claim).asString())

/**
 * Выполняет [block] в контексте [Raise], оборачивает результат в [either] и отвечает:
 * - успех: [respond] с телом типа [A]
 * - ошибка: HTTP 400 с [ErrorResponse]
 */
suspend inline fun <reified A : Any> RoutingCall.eitherToResponse(block: Raise<DomainError>.() -> A) {
    either {
        val result = block()
        respond(result)
    }.onLeft {
        respond(
            HttpStatusCode.BadRequest,
            ErrorResponse(code = it.code, message = it.message),
        )
    }
}

/**
 * Возвращает IP-адрес клиента из заголовка X-Forwarded-For (для запросов через прокси)
 * или из локального адреса прямого подключения.
 */
fun RoutingCall.clientIp(): String? =
    request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
        ?: request.local.remoteHost
