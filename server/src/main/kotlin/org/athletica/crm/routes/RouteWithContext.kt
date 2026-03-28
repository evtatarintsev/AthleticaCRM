package org.athletica.crm.routes

import io.ktor.http.HttpMethod
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.route
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.security.JwtConfig
import kotlin.uuid.Uuid

/**
 * Регистрирует POST-маршрут с контекстным параметром [Lang].
 * Язык определяется из заголовков запроса (сейчас заглушка — всегда [Lang.EN]).
 */
fun Route.postWithContext(
    path: String,
    body: suspend context(RequestContext) RoutingContext.() -> Unit,
): Route =
    route(path, HttpMethod.Post) {
        handle {
            context(call.contextFromRequest()) {
                body()
            }
        }
    }

/**
 * Регистрирует GET-маршрут с контекстным параметром [Lang].
 * Язык определяется из заголовков запроса (сейчас заглушка — всегда [Lang.EN]).
 */
fun Route.getWithContext(
    path: String,
    body: suspend context(RequestContext) RoutingContext.() -> Unit,
): Route =
    route(path, HttpMethod.Get) {
        handle {
            context(call.contextFromRequest()) {
                body()
            }
        }
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
 * Извлекает [UserId] и [OrgId] из claims токена, язык — из `Accept-Language`.
 */
fun RoutingCall.contextFromRequest(): RequestContext {
    val principal = principal<JWTPrincipal>()!!
    val userId = UserId(Uuid.parse(principal.payload.getClaim(JwtConfig.CLAIM_USER_ID).asString()))
    val orgId = OrgId(Uuid.parse(principal.payload.getClaim(JwtConfig.CLAIM_ORG_ID).asString()))
    return RequestContext(
        userId = userId,
        orgId = orgId,
        lang = langFromRequest(),
        username = principal.payload.getClaim(JwtConfig.CLAIM_USERNAME).asString(),
    )
}
