package io.ktor.typedroutes

import io.ktor.server.routing.Route


/** Обёртка над [Route], несущая типовую информацию о маршруте. */
class TypedRoute(
    val route: Route,
)

fun TypedRoute.post() {

}
