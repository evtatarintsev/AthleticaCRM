package org.athletica.crm

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Тесты адреса webhook-уведомлений ЮKassa: маршрут живёт под `/api` и не требует сессии сотрудника.
 */
class CheckoutRouteTest {
    private lateinit var di: Di

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Запускает тестовый сервер с конфигурацией приложения и выполняет [block]. */
    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                context(di) {
                    configureServer()
                }
            }
            block()
        }

    @Test
    fun `уведомление без авторизации доходит до проверки подписи`() =
        withServer {
            val response = client.post("/api/checkout/yookassa") { setBody("{}") }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `старый адрес вне api не обслуживается`() =
        withServer {
            val response = client.post("/checkout/yookassa") { setBody("{}") }
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}
