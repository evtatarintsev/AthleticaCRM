package org.athletica.crm.routes

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.either
import com.auth0.jwt.interfaces.Payload
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.route
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.athletica.crm.Di
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.systemContext
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.asString
import kotlin.uuid.Uuid

class RouteWithContext(val di: Di, val router: Route) {
    fun route(path: String, build: RouteWithContext.() -> Unit): Route =
        router.route(path) {
            RouteWithContext(di, this).apply(build)
        }

    @Deprecated(
        "Use typed route instead",
        ReplaceWith("get<Request, Response>(path)"),
    )
    inline fun <reified Res> get(
        path: String,
        crossinline body: suspend context(EmployeeRequestContext) Raise<DomainError>.(RoutingCall) -> Res,
    ): Route =
        route(path, HttpMethod.Get) { call ->
            val response = body(call)
            call.respond(response, typeInfo<Res>())
        }

    @JvmName("getWithRequest")
    inline fun <reified Req, reified Res> get(
        path: String,
        crossinline body: suspend context(EmployeeRequestContext) Raise<DomainError>.(Req) -> Res,
    ): Route =
        route(path, HttpMethod.Get) { call ->
            val request = call.request.queryParameters.decode<Req>()
            val response = body(request)
            call.respond(response, typeInfo<Res>())
        }

    @JvmName("getWithRequestAndCall")
    inline fun <reified Req, reified Res> get(
        path: String,
        crossinline body: suspend context(EmployeeRequestContext) Raise<DomainError>.(Req, RoutingCall) -> Res,
    ): Route =
        route(path, HttpMethod.Get) { call ->
            val request = call.request.queryParameters.decode<Req>()
            val response = body(request, call)
            call.respond(response, typeInfo<Res>())
        }

    inline fun <reified Req, reified Res> post(
        path: String,
        crossinline body: suspend context(EmployeeRequestContext) Raise<DomainError>.(Req) -> Res,
    ): Route =
        route(path, HttpMethod.Post) { call ->
            @Suppress("UNCHECKED_CAST")
            val req: Req = if (Req::class == Unit::class) Unit as Req else call.body<Req>()
            val response = body(req)
            if (Res::class == Unit::class) call.respond(Unit) else call.respond(response, typeInfo<Res>())
        }

    @JvmName("postWithCall")
    inline fun <reified Req, reified Res> post(
        path: String,
        crossinline body: suspend context(EmployeeRequestContext) Raise<DomainError>.(Req, RoutingCall) -> Res,
    ): Route =
        route(path, HttpMethod.Post) { call ->
            @Suppress("UNCHECKED_CAST")
            val req: Req = if (Req::class == Unit::class) Unit as Req else call.body<Req>()
            val response = body(req, call)
            if (Res::class == Unit::class) call.respond(Unit) else call.respond(response, typeInfo<Res>())
        }

    inline fun route(
        path: String,
        method: HttpMethod,
        crossinline body: suspend context(EmployeeRequestContext) Raise<DomainError>.(RoutingCall) -> Unit,
    ): Route =
        router.route(path, method) {
            handle {
                context(call.contextFromRequest(di.database, di.employees, di.organizations)) {
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

context(raise: Raise<DomainError>)
@PublishedApi
internal inline fun <reified T> Parameters.decode(): T {
    val jsonObject =
        JsonObject(
            entries()
                .filter { (_, values) -> values.isNotEmpty() }
                .associate { (key, values) -> key to JsonPrimitive(values.first()) },
        )
    return try {
        queryParametersJson.decodeFromJsonElement(jsonObject)
    } catch (e: Exception) {
        raise(CommonDomainError("INVALID_REQUEST", "Invalid query parameters"))
    }
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
 * Собирает [EmployeeRequestContext] из JWT-токена и заголовков запроса.
 * Извлекает [UserId], [EmployeeId] и [OrgId] из claims токена, язык — из `Accept-Language`,
 * IP-адрес клиента — из `X-Forwarded-For` или прямого подключения.
 */
suspend fun RoutingCall.contextFromRequest(
    db: Database,
    employees: Employees,
    organizations: Organizations,
): EmployeeRequestContext =
    either {
        val principal = principal<JWTPrincipal>()!!
        val userId = principal.payload.claimAsUuid(JwtConfig.CLAIM_USER_ID).toUserId()
        val orgId = principal.payload.claimAsUuid(JwtConfig.CLAIM_ORG_ID).toOrgId()
        val branchId = principal.payload.claimAsUuid(JwtConfig.CLAIM_BRANCH_ID).toBranchId()
        val employeeId = principal.payload.claimAsUuid(JwtConfig.CLAIM_EMPLOYEE_ID).toEmployeeId()
        val (currency, employee) =
            db.transaction {
                context(systemContext(orgId)) {
                    organizations.current().currency to employees.byId(employeeId)
                }
            }
        EmployeeRequestContext(
            userId = userId,
            orgId = orgId,
            branchId = branchId,
            employeeId = employeeId,
            lang = langFromRequest(),
            username = principal.payload.getClaim(JwtConfig.CLAIM_USERNAME).asString(),
            clientIp = clientIp(),
            currency = currency,
            permission = employee.permissions,
            availableBranches = employee.availableBranches,
        )
    }.getOrElse { throw RuntimeException("Error getting context from request: $it") }

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

@PublishedApi
internal val queryParametersJson = Json { ignoreUnknownKeys = true }
