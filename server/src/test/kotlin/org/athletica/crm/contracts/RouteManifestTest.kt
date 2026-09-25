package org.athletica.crm.contracts

import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import org.athletica.crm.routes.ApiRequest
import org.athletica.crm.routes.ApiResponse
import org.athletica.crm.routes.getContract
import org.athletica.crm.routes.postContract
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Запрос GET с вложенным списком — нарушает требование плоских query-параметров. */
@Serializable
private data class NestedQuery(val ids: List<String>)

/** Плоский запрос GET: примитивы и nullable. */
@Serializable
private data class FlatQuery(val search: String, val limit: Int? = null)

/** Тесты манифеста маршрутов: контракт у каждого маршрута `/api` и инварианты. */
class RouteManifestTest {
    @Test
    fun `у каждого маршрута под api есть контракт`() {
        val routes = registeredRoutes(offlineDi())
        val apiRoutes = routes.filter { it.path.startsWith(API_PREFIX) }

        val endpoints = endpoints(routes)

        assertTrue(apiRoutes.size > 90, "найдено маршрутов: ${apiRoutes.size}")
        assertEquals(apiRoutes.size, endpoints.size)
        assertTrue(apiRoutes.all { it.contract != null })
    }

    @Test
    fun `явно размеченные маршруты попадают в манифест`() {
        val byPath = endpoints(registeredRoutes(offlineDi())).associateBy { it.path }

        assertEquals(ApiRequest.Multipart, byPath.getValue("upload").contract.request)
        assertEquals(ApiResponse.File, byPath.getValue("clients/export").contract.response)
        listOf("auth/branches", "auth/login", "auth/sign-up", "auth/refresh-token").forEach {
            assertEquals(HttpMethod.Post, byPath.getValue(it).contract.method, it)
        }
    }

    @Test
    fun `маршрут без контракта роняет генерацию с путём маршрута`() {
        val routes =
            listOf(
                RegisteredRoute("/api/halls/list", HttpMethod.Get, getContract<Unit, String>()),
                RegisteredRoute("/api/halls/secret", HttpMethod.Post, contract = null),
            )

        val error = assertFailsWith<ManifestException> { endpoints(routes) }

        assertContains(error.message.orEmpty(), "POST /api/halls/secret")
    }

    @Test
    fun `маршруты вне api не требуют контракта`() {
        val routes = listOf(RegisteredRoute("/health", HttpMethod.Get, contract = null))

        assertEquals(emptyList(), endpoints(routes))
    }

    @Test
    fun `метод контракта должен совпадать с методом маршрута`() {
        val routes = listOf(RegisteredRoute("/api/halls/list", HttpMethod.Get, postContract<Unit, String>()))

        val error = assertFailsWith<ManifestException> { endpoints(routes) }

        assertContains(error.message.orEmpty(), "/api/halls/list")
    }

    @Test
    fun `запрос GET должен быть плоским`() {
        val nested = listOf(RegisteredRoute("/api/x", HttpMethod.Get, getContract<NestedQuery, String>()))
        val flat = listOf(RegisteredRoute("/api/y", HttpMethod.Get, getContract<FlatQuery, String>()))

        val error = assertFailsWith<ManifestException> { endpoints(nested) }

        assertContains(error.message.orEmpty(), "ids")
        assertEquals(listOf("y"), endpoints(flat).map { it.path })
    }
}
