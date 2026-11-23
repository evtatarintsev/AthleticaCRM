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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.auth.SignUp
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailConfirmationRoutesTest {
    private lateinit var di: Di

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    private suspend fun signup(email: String = "owner@example.com"): AuthenticatedUser =
        orThrow {
            TestPostgres.db.transaction {
                di.auth.signup(SignUp(testName("Club"), testName("Owner"), email, "password123", "UTC", Currency.RUB), Lang.EN, null)
            }
        }

    private suspend fun token(user: AuthenticatedUser): String =
        orThrow {
            TestPostgres.db.sql("SELECT text_body FROM org_emails WHERE org_id = :orgId").bind("orgId", user.orgId)
                .firstOrNull { it.asString("text_body") }!!.substringAfter("#token=").substringBefore('\n').trim()
        }

    @Test
    fun `unconfirmed account keeps login refresh and normal API access`() =
        testApplication {
            application { context(di) { configureServer() } }
            val user = signup()
            val access = di.jwtConfig.sessionAccessToken(user)
            val profile = client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer $access") }
            assertEquals(HttpStatusCode.OK, profile.status)
            assertTrue(Json.parseToJsonElement(profile.bodyAsText()).jsonObject.getValue("emailVerificationPending").jsonPrimitive.boolean)
            val login =
                client.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"${user.username}","password":"password123","branchId":"${user.branchId}"}""")
                }
            assertEquals(HttpStatusCode.OK, login.status)
            val refresh = client.post("/api/auth/refresh-token") { header("X-Refresh-Token", di.jwtConfig.makeRefreshToken(user.id, user.branchId, openTestSession(user))) }
            assertEquals(HttpStatusCode.OK, refresh.status)
            val resend = client.post("/api/auth/me/resend-email-confirmation") { header(HttpHeaders.Authorization, "Bearer $access") }
            assertEquals(HttpStatusCode.OK, resend.status)
            assertContains(resend.bodyAsText(), "rate_limited")
            assertContains(resend.bodyAsText(), "retryAfterSeconds")
        }

    @Test
    fun `public confirmation leaves the browser session intact and localizes isolated effects`() =
        testApplication {
            application { context(di) { configureServer() } }
            val user = signup()
            val token = token(user)
            val other = signup("other@example.com")
            val otherAccess = di.jwtConfig.sessionAccessToken(other)
            val response =
                client.post("/api/auth/confirm-email") {
                    header(HttpHeaders.Authorization, "Bearer $otherAccess")
                    header(HttpHeaders.AcceptLanguage, "en")
                    contentType(ContentType.Application.Json)
                    setBody("""{"token":"$token"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "confirmed")
            assertNull(response.headers[HttpHeaders.SetCookie])
            val repeated =
                client.post("/api/auth/confirm-email") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"token":"$token"}""")
                }
            assertContains(repeated.bodyAsText(), "already_confirmed")
            assertNull(repeated.headers[HttpHeaders.SetCookie])
            for ((account, pending) in listOf(user to false, other to true)) {
                val access = di.jwtConfig.sessionAccessToken(account)
                val profile = client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer $access") }
                assertEquals(pending, Json.parseToJsonElement(profile.bodyAsText()).jsonObject.getValue("emailVerificationPending").jsonPrimitive.boolean)
                for ((lang, description) in listOf("en" to "confirmed email", "ru" to "подтверждение email")) {
                    val audit =
                        client.get("/api/audit/log?actionType=auth_verify_email") {
                            header(HttpHeaders.Authorization, "Bearer $access")
                            header(HttpHeaders.AcceptLanguage, lang)
                        }
                    assertEquals(HttpStatusCode.OK, audit.status)
                    val body = audit.bodyAsText()
                    assertFalse(body.contains(token))
                    val items = Json.parseToJsonElement(body).jsonObject.getValue("items").jsonArray
                    assertEquals(if (pending) 0 else 1, items.size)
                    if (!pending) {
                        assertContains(items.single().jsonObject.getValue("description").jsonPrimitive.content, description)
                        assertContains(body, user.username)
                    }
                }
                val notifications =
                    client.get("/api/notifications") {
                        header(HttpHeaders.Authorization, "Bearer $access")
                        header(HttpHeaders.AcceptLanguage, "en")
                    }
                assertEquals(HttpStatusCode.OK, notifications.status)
                val items = Json.parseToJsonElement(notifications.bodyAsText()).jsonObject.getValue("notifications").jsonArray
                assertEquals(if (pending) 0 else 1, items.size)
                if (!pending) assertContains(notifications.bodyAsText(), "Email confirmed")
            }
        }

    @Test
    fun `guest can confirm but cannot resend and invalid input does not expose tokens`() =
        testApplication {
            application { context(di) { configureServer() } }
            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/auth/me/resend-email-confirmation").status)
            val invalid =
                client.post("/api/auth/confirm-email") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"token":"unknown-secret-token"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            assertFalse(invalid.bodyAsText().contains("unknown-secret-token"))
            val invalidSignup =
                client.post("/api/auth/sign-up") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"companyName":"Club","userName":"Owner","login":"not-an-email","password":"password123","timezone":"UTC","currency":"RUB"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, invalidSignup.status)
            assertContains(invalidSignup.bodyAsText(), "INVALID_EMAIL")
        }

    @Test
    fun `legacy profile has no reminder and unsupported language uses Russian welcome`() =
        testApplication {
            application { context(di) { configureServer() } }
            val response =
                client.post("/api/auth/sign-up") {
                    header(HttpHeaders.AcceptLanguage, "de-DE")
                    header(HttpHeaders.Host, "evil.example.com")
                    contentType(ContentType.Application.Json)
                    setBody("""{"companyName":"Club","userName":"Owner","login":"owner@example.com","password":"password123","timezone":"UTC","currency":"RUB"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val access = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("accessToken").jsonPrimitive.content
            val body = orThrow { TestPostgres.db.sql("SELECT text_body FROM org_emails").firstOrNull { it.asString("text_body") }!! }
            assertContains(body, "Добро пожаловать")
            assertFalse(body.contains("evil.example.com"))
            assertContains(body, "http://localhost:5173/confirm-email#token=")
            orThrow { TestPostgres.db.sql("DELETE FROM user_email_verifications").execute() }
            val profile = client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer $access") }
            assertFalse(Json.parseToJsonElement(profile.bodyAsText()).jsonObject.getValue("emailVerificationPending").jsonPrimitive.boolean)
            val resend = client.post("/api/auth/me/resend-email-confirmation") { header(HttpHeaders.Authorization, "Bearer $access") }
            assertEquals(HttpStatusCode.BadRequest, resend.status)
            assertContains(resend.bodyAsText(), "EMAIL_VERIFICATION_UNAVAILABLE")
        }
}
