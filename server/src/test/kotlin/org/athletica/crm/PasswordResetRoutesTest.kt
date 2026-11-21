package org.athletica.crm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PasswordResetRoutesTest {
    private lateinit var di: Di

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    private suspend fun signup(): AuthenticatedUser =
        orThrow {
            TestPostgres.db.transaction {
                di.auth.signup(SignUpRequest(testName("Club"), testName("Owner"), "owner@example.com", "old-password", "UTC", Currency.RUB), Lang.EN, null)
            }
        }

    private suspend fun token(): String =
        orThrow {
            TestPostgres.db.sql("SELECT text_body FROM org_emails WHERE text_body LIKE '%reset-password#token=%'")
                .firstOrNull { it.asString("text_body") }!!.substringAfter("#token=").substringBefore('\n').trim()
        }

    @Test
    fun `forgot password answers the same for known and unknown addresses`() =
        testApplication {
            application { context(di) { configureServer() } }
            signup()
            val responses =
                listOf("owner@example.com", "nobody@example.com").map {
                    client.post("/api/auth/forgot-password") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"$it"}""")
                    }
                }
            responses.forEach { assertEquals(HttpStatusCode.OK, it.status) }
            assertEquals(responses[0].bodyAsText(), responses[1].bodyAsText())
        }

    @Test
    fun `reset ends the session and only the new password logs in`() =
        testApplication {
            application { context(di) { configureServer() } }
            val user = signup()
            val access = di.jwtConfig.sessionAccessToken(user)
            client.post("/api/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"owner@example.com"}""")
            }
            val token = token()
            val reset =
                client.post("/api/auth/reset-password") {
                    header(HttpHeaders.AcceptLanguage, "en")
                    contentType(ContentType.Application.Json)
                    setBody("""{"token":"$token","newPassword":"new-password"}""")
                }
            assertEquals(HttpStatusCode.OK, reset.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer $access") }.status)
            listOf("old-password" to HttpStatusCode.BadRequest, "new-password" to HttpStatusCode.OK).forEach { (password, status) ->
                val login =
                    client.post("/api/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"username":"${user.username}","password":"$password","branchId":"${user.branchId}"}""")
                    }
                assertEquals(status, login.status, password)
            }
            val again =
                client.post("/api/auth/reset-password") {
                    header(HttpHeaders.AcceptLanguage, "en")
                    contentType(ContentType.Application.Json)
                    setBody("""{"token":"$token","newPassword":"other"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, again.status)
            assertContains(again.bodyAsText(), "INVALID_PASSWORD_RESET")
            assertFalse(again.bodyAsText().contains(token))
        }
}
