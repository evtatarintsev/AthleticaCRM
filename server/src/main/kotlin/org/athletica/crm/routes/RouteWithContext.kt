package org.athletica.crm.routes

import io.ktor.http.HttpMethod
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.route
import org.athletica.crm.core.Lang

/**
 * Регистрирует POST-маршрут с контекстным параметром [Lang].
 * Язык определяется из заголовков запроса (сейчас заглушка — всегда [Lang.EN]).
 */
fun Route.postWithContext(
    path: String,
    body: suspend context(Lang) RoutingContext.() -> Unit,
): Route =
    route(path, HttpMethod.Post) {
        handle {
            val lang = Lang.EN // в реальности возьмем их хидеров call.
            context(lang) {
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
    body: suspend context(Lang) RoutingContext.() -> Unit,
): Route =
    route(path, HttpMethod.Get) {
        handle {
            val lang = Lang.EN // в реальности возьмем их хидеров call.
            context(lang) {
                body()
            }
        }
    }
