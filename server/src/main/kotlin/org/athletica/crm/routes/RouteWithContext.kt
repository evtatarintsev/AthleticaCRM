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

    /**
     * GET с автоматическим error handling и response (без параметров).
     * Использование:
     *   get<OrgSettingsResponse>("/settings") {
     *       db.transaction { ... }
     *   }
     */
    inline fun <reified Res> get(
        path: String,
        crossinline body: suspend context(RequestContext) () -> Either<DomainError, Res>,
    ): Route =
        router.route(path, HttpMethod.Get) {
            handle {
                context(call.contextFromRequest(di.database, di.employeePermissions)) {
                    body()
                        .fold(
                            { error ->
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(code = error.code, message = error.message),
                                )
                            },
                            { result -> call.respond(result) }
                        )
                }
            }
        }

    /**
     * GET с доступом к call и параметрам запроса.
     * Для случаев когда нужны параметры из query, headers, etc.
     * Использование:
     *   getWithCall<AuditLogListResponse>("/log") { call ->
     *       val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
     *       Either.Right(AuditLogListResponse(...))
     *   }
     */
    inline fun <reified Res> getWithCall(
        path: String,
        crossinline body: suspend context(RequestContext) (RoutingCall) -> Either<DomainError, Res>,
    ): Route =
        router.route(path, HttpMethod.Get) {
            handle {
                context(call.contextFromRequest(di.database, di.employeePermissions)) {
                    body(call)
                        .fold(
                            { error ->
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(code = error.code, message = error.message),
                                )
                            },
                            { result -> call.respond(result) }
                        )
                }
            }
        }

    /**
     * POST с автоматическим разбором request, error handling и response.
     * Использование:
     *   post<UpdateOrgSettingsRequest, OrgSettingsResponse>("/settings/update") { request ->
     *       db.transaction { ... }
     *   }
     */
    inline fun <reified Req, reified Res> post(
        path: String,
        crossinline body: suspend context(RequestContext) (Req) -> Either<DomainError, Res>,
    ): Route =
        router.route(path, HttpMethod.Post) {
            handle {
                context(call.contextFromRequest(di.database, di.employeePermissions)) {
                    try {
                        val request = call.receive<Req>()
                        body(request)
                            .fold(
                                { error ->
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        ErrorResponse(code = error.code, message = error.message),
                                    )
                                },
                                { result -> call.respond(result) }
                            )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(code = "INVALID_REQUEST", message = "Invalid request body"),
                        )
                    }
                }
            }
        }

    /**
     * POST с доступом к call для сложных операций (multipart, custom parsing, etc).
     * Использование:
     *   postWithCall<UploadResponse>("/upload") { call ->
     *       call.receiveMultipart() { ... }
     *       ...
     *   }
     */
    inline fun <reified Res> postWithCall(
        path: String,
        crossinline body: suspend context(RequestContext) (RoutingCall) -> Either<DomainError, Res>,
    ): Route =
        router.route(path, HttpMethod.Post) {
            handle {
                context(call.contextFromRequest(di.database, di.employeePermissions)) {
                    body(call)
                        .fold(
                            { error ->
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ErrorResponse(code = error.code, message = error.message),
                                )
                            },
                            { result -> call.respond(result) }
                        )
                }
            }
        }

    /**
     * GET без параметров типа (для сложных случаев с eitherToResponse).
     * Используй это если нужен доступ к call и Raise контекст.
     * Использование:
     *   get("/upload/info") {
     *       call.eitherToResponse { ... }
     *   }
     */
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

    /**
     * POST без параметров типа (для сложных случаев с eitherToResponse).
     * Используй это если нужен доступ к call и Raise контекст.
     * Использование:
     *   post("/upload") {
     *       call.eitherToResponse { ... }
     *   }
     */
    fun post(
        path: String,
        body: suspend context(RequestContext) RoutingContext.() -> Unit,
    ): Route =
        router.route(path, HttpMethod.Post) {
            handle {
                context(call.contextFromRequest(di.database, di.employeePermissions)) {
                    body()
                }
            }
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
