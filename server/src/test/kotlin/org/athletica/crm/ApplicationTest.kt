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
import org.athletica.crm.security.UserService
import org.junit.AfterClass
import org.junit.BeforeClass
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Интеграционные тесты сервера с изолированной БД в Docker-контейнере. */
class ApplicationTest {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:18")

        private lateinit var userService: UserService

        @BeforeClass
        @JvmStatic
        fun setup() {
            postgres.start()
            runMigrations(
                url = postgres.jdbcUrl,
                user = postgres.username,
                password = postgres.password,
            )
            userService = UserService(
                createDatabase(
                    jdbcUrl = postgres.jdbcUrl,
                    user = postgres.username,
                    password = postgres.password,
                )
            )
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            postgres.stop()
        }
    }

    private val testJwtConfig = JwtConfig(
        secret = "test-secret-key-for-unit-tests",
        accessTokenTtlMinutes = 15L,
        refreshTokenTtlDays = 30L,
    )

    @Test
    fun testLoginWithInvalidCredentials() =
        testApplication {
            application { configureServer(testJwtConfig, userService) }
            val response = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"wrong","password":"wrong"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun testMeWithoutToken() =
        testApplication {
            application { configureServer(testJwtConfig, userService) }
            val response = client.get("/api/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertContains(response.bodyAsText(), "Token expired or invalid")
        }
}
