package org.athletica.crm

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Интеграционные тесты сервера с изолированной БД в Docker-контейнере. */
class ApplicationTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    private val testJwtConfig =
        JwtConfig(
            secret = "test-secret-key-for-unit-tests",
            accessTokenTtlMinutes = 15L,
            refreshTokenTtlDays = 30L,
        )

    @Test
    fun testLoginWithInvalidCredentials() =
        testApplication {
            application {
                context(TestPostgres.db, PasswordHasher()) {
                    configureServer(testJwtConfig)
                }
            }
            val response =
                client.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"wrong","password":"wrong"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun testMeWithoutToken() =
        testApplication {
            application {
                context(TestPostgres.db, PasswordHasher()) {
                    configureServer(testJwtConfig)
                }
            }
            val response = client.get("/api/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertContains(response.bodyAsText(), "Token expired or invalid")
        }
}
