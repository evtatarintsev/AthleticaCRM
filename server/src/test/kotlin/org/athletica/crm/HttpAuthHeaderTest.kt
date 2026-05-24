package org.athletica.crm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.athletica.crm.security.JwtConfig
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Тесты разбора токена авторизации из заголовков и cookie.
 *
 * Регрессия: невалидные/пустые значения должны давать 401, а не 500
 * (io.ktor.http.parsing.ParseException пробрасывался наружу из authHeader-лямбды).
 */
class HttpAuthHeaderTest {
    private lateinit var di: Di

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    private fun withServer(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                context(di) {
                    configureServer()
                }
            }
            block()
        }

    @Test
    fun `пустой cookie access_token возвращает 401`() =
        withServer {
            val response =
                client.get("/api/auth/me") {
                    header("Cookie", "${JwtConfig.COOKIE_ACCESS_TOKEN}=")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `cookie с пробелом в значении возвращает 401`() =
        withServer {
            val response =
                client.get("/api/auth/me") {
                    header("Cookie", "${JwtConfig.COOKIE_ACCESS_TOKEN}=invalid token")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `cookie с urlencoded символами возвращает 401`() =
        withServer {
            val response =
                client.get("/api/auth/me") {
                    header("Cookie", "${JwtConfig.COOKIE_ACCESS_TOKEN}=bad%20value%3D")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `заголовок Authorization с невалидными символами возвращает 401`() =
        withServer {
            val response =
                client.get("/api/auth/me") {
                    header("Authorization", "Bearer !!invalid!!")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `заголовок Authorization без схемы Bearer возвращает 401`() =
        withServer {
            val response =
                client.get("/api/auth/me") {
                    header("Authorization", "not-a-valid-auth-header")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `запрос без токена возвращает 401`() =
        withServer {
            val response = client.get("/api/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
