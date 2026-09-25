package org.athletica.crm.contracts

import io.ktor.http.HttpMethod
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.PathSegmentOptionalParameterRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.nonNullOriginal
import org.athletica.crm.Di
import org.athletica.crm.configureServer
import org.athletica.crm.routes.ApiContract
import org.athletica.crm.routes.ApiRequest
import org.athletica.crm.routes.apiContract

/** Префикс маршрутов API; ниже него контракт обязателен. */
const val API_PREFIX = "/api/"

/**
 * Маршрут с обработчиком, найденный в дереве Ktor.
 * [path] — полный путь с `{параметрами}`, [method] — HTTP-метод, [contract] — записанный контракт.
 */
data class RegisteredRoute(
    /** Полный путь маршрута, например `/api/clients/list` или `/api/sessions/{id}/cancel`. */
    val path: String,
    /** HTTP-метод маршрута; `null`, если маршрут принимает любой метод. */
    val method: HttpMethod?,
    /** Контракт маршрута; `null`, если он не записан. */
    val contract: ApiContract?,
)

/**
 * Эндпоинт API для веб-клиента.
 * [path] — путь без префикса `/api/`, [contract] — формат запроса и ответа.
 */
data class Endpoint(
    /** Путь без префикса `/api/`, например `clients/list`. */
    val path: String,
    /** Контракт эндпоинта. */
    val contract: ApiContract,
)

/** Нарушение инвариантов манифеста маршрутов. */
class ManifestException(message: String) : RuntimeException(message)

/**
 * Поднимает приложение с [di] в тестовом движке Ktor и возвращает все маршруты,
 * у которых есть обработчик. Запросы не выполняются.
 */
fun registeredRoutes(di: Di): List<RegisteredRoute> {
    var root: RoutingNode? = null
    testApplication {
        application {
            context(di) {
                configureServer()
            }
            root = routing { }
        }
        startApplication()
    }
    val tree = root ?: throw ManifestException("Дерево маршрутов не построено")
    return tree
        .descendants()
        .filterIsInstance<RoutingNode>()
        .filter { it.hasHandler() }
        .map { RegisteredRoute(it.fullPath(), it.method(), it.apiContract) }
        .toList()
}

/**
 * Проверяет инварианты манифеста и возвращает эндпоинты под `/api/`, упорядоченные по пути.
 *
 * - у каждого маршрута под `/api/` есть контракт, и метод контракта совпадает с методом маршрута;
 * - запрос GET — плоский объект из примитивов, перечислений и идентификаторов.
 *
 * При нарушении бросает [ManifestException] со списком всех найденных проблем.
 */
fun endpoints(routes: List<RegisteredRoute>): List<Endpoint> {
    val problems = mutableListOf<String>()
    val result =
        routes
            .filter { it.path.startsWith(API_PREFIX) }
            .mapNotNull { route ->
                val contract = route.contract
                when {
                    contract == null -> {
                        problems += "${route.method?.value ?: "*"} ${route.path}: у маршрута нет ApiContract"
                        null
                    }
                    contract.method != route.method -> {
                        problems += "${route.path}: метод контракта ${contract.method.value} не совпадает с методом маршрута ${route.method?.value}"
                        null
                    }
                    else -> {
                        flatQueryProblems(route.path, contract.request).forEach { problems += it }
                        Endpoint(route.path.removePrefix(API_PREFIX), contract)
                    }
                }
            }.sortedWith(compareBy({ it.path }, { it.contract.method.value }))
    val duplicates = result.groupBy { it.path }.filterValues { it.size > 1 }.keys
    duplicates.forEach { problems += "$it: несколько методов на одном пути не поддерживаются клиентом" }
    if (problems.isNotEmpty()) {
        throw ManifestException("Манифест маршрутов нарушает инварианты:\n" + problems.joinToString("\n") { "  - $it" })
    }
    return result
}

/** Проблемы плоскости query-запроса [request] маршрута [path]; пусто, если запрос не query. */
private fun flatQueryProblems(path: String, request: ApiRequest): List<String> {
    if (request !is ApiRequest.Query) {
        return emptyList()
    }
    val descriptor = request.serializer.descriptor
    if (descriptor.kind != StructureKind.CLASS) {
        return listOf("$path: query-запрос ${descriptor.serialName} должен быть классом")
    }
    return (0 until descriptor.elementsCount)
        .filterNot { isFlatQueryValue(descriptor.getElementDescriptor(it)) }
        .map { "$path: поле ${descriptor.getElementName(it)} query-запроса ${descriptor.serialName} не примитив, не перечисление и не идентификатор" }
}

/** Истина, если значение [descriptor] передаётся одним query-параметром. */
private fun isFlatQueryValue(descriptor: SerialDescriptor): Boolean {
    val plain = descriptor.nonNullOriginal
    val kind: SerialKind = plain.kind
    return when {
        plain.isInline -> plain.elementDescriptors.all { isFlatQueryValue(it) }
        kind is PrimitiveKind -> true
        kind == SerialKind.ENUM -> true
        else -> false
    }
}

/** Полный путь узла по цепочке селекторов: константы как есть, параметры — `{имя}`. */
private fun RoutingNode.fullPath(): String {
    val segments =
        generateSequence(this) { it.parent }
            .toList()
            .asReversed()
            .mapNotNull {
                when (val selector = it.selector) {
                    is PathSegmentConstantRouteSelector -> selector.value
                    is PathSegmentParameterRouteSelector -> "{${selector.name}}"
                    is PathSegmentOptionalParameterRouteSelector -> "{${selector.name}?}"
                    else -> null
                }
            }
    return "/" + segments.joinToString("/")
}

/** HTTP-метод узла, если он задан селектором метода. */
private fun RoutingNode.method(): HttpMethod? = (selector as? HttpMethodRouteSelector)?.method
