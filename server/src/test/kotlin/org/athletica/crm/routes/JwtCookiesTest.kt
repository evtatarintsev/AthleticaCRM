package org.athletica.crm.routes

import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.server.response.ResponseCookies
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.athletica.crm.security.JwtConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты cookies с JWT токенами.
 *
 * Регрессия: cookies без `Max-Age` были сессионными, и мобильные браузеры теряли их
 * при выгрузке вкладки из памяти — пользователя разлогинивало.
 */
class JwtCookiesTest {
    private val jwtConfig = JwtConfig(secret = "test", accessTokenTtlMinutes = 15, refreshTokenTtlDays = 30)

    /** Заголовки `Set-Cookie`, которые сервер отдаёт на запрос маршрута, вызывающего [setCookies]. */
    private fun setCookieHeaders(setCookies: ResponseCookies.() -> Unit): List<String> {
        var headers = emptyList<String>()
        testApplication {
            routing {
                get("/cookies") {
                    call.response.cookies.setCookies()
                    call.respond("ok")
                }
            }
            headers = client.get("/cookies").headers.getAll(HttpHeaders.SetCookie).orEmpty()
        }
        return headers
    }

    @Test
    fun `cookies с токенами живут столько же, сколько токены`() {
        val headers = setCookieHeaders { context(jwtConfig) { setJwtCookies("access", "refresh") } }

        val access = headers.single { it.startsWith("${JwtConfig.COOKIE_ACCESS_TOKEN}=") }
        val refresh = headers.single { it.startsWith("${JwtConfig.COOKIE_REFRESH_TOKEN}=") }
        assertTrue("Max-Age=900" in access, access)
        assertTrue("Max-Age=2592000" in refresh, refresh)
    }

    @Test
    fun `logout удаляет обе cookies`() {
        val headers = setCookieHeaders { clearJwtCookies() }

        assertEquals(2, headers.size)
        headers.forEach { assertTrue("Max-Age=0" in it, it) }
    }
}
