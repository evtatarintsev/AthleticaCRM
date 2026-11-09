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
import org.athletica.crm.api.schemas.auth.AuthBranchesResponse
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.mail.EmailDispatcher
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FakeEmailDispatcher : EmailDispatcher {
    override suspend fun dispatchPending() {}
}

/** Интеграционные тесты сервера с изолированной БД в Docker-контейнере. */
class ApplicationTest {
    private lateinit var testDi: Di

    @Before
    fun setUp() {
        TestPostgres.truncate()
        testDi = testDi()
    }

    @Test
    fun testLoginWithInvalidCredentials() =
        testApplication {
            application {
                context(testDi) {
                    configureServer()
                }
            }
            val response =
                client.post("/api/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"wrong","password":"wrong","branchId":"00000000-0000-0000-0000-000000000000"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun testAuditRecordsSuccessfulAuthOperations() =
        testApplication {
            application { context(testDi) { configureServer() } }

            val login = "owner@example.com"
            val password = "password123"

            suspend fun postJson(path: String, body: String) =
                client.post("/api/auth/$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

            val signup =
                postJson(
                    "sign-up",
                    """{"companyName":"Acme","userName":"Owner","login":"$login","password":"$password","timezone":"UTC","currency":"RUB"}""",
                )
            assertEquals(HttpStatusCode.OK, signup.status)

            val credentials = """"username":"$login","password":"$password""""
            val branches = postJson("branches", "{$credentials}")
            assertEquals(HttpStatusCode.OK, branches.status)
            val branchId = serverJson.decodeFromString<AuthBranchesResponse>(branches.bodyAsText()).branches.single().id

            val denied = postJson("login", """{$credentials,"branchId":"${BranchId.new().value}"}""")
            assertEquals(HttpStatusCode.BadRequest, denied.status)
            assertContains(denied.bodyAsText(), "BRANCH_ACCESS_DENIED")

            val loggedIn = postJson("login", """{$credentials,"branchId":"${branchId.value}"}""")
            assertEquals(HttpStatusCode.OK, loggedIn.status)
            val token = serverJson.decodeFromString<LoginResponse>(loggedIn.bodyAsText()).accessToken

            val logout = client.post("/api/auth/logout") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.OK, logout.status)

            val actions =
                TestPostgres.db
                    .sql("SELECT action_type FROM audit_logs WHERE username = :login ORDER BY action_type")
                    .bind("login", login)
                    .list { it.asString("action_type") }
            assertEquals(
                listOf(AuditActionType.AUTH_LOGIN, AuditActionType.AUTH_LOGOUT, AuditActionType.AUTH_SIGNUP).map { it.code },
                actions,
            )
        }

    @Test
    fun testMeWithoutToken() =
        testApplication {
            application {
                context(testDi) {
                    configureServer()
                }
            }
            val response = client.get("/api/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertContains(response.bodyAsText(), "Token expired or invalid")
        }
}
