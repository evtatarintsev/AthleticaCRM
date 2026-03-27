package org.athletica.crm.routes

import io.ktor.http.HttpMethod
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.route
import org.athletica.crm.core.Lang

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
