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
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.orThrow
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

            suspend fun postJson(path: String, body: String, token: String? = null) =
                client.post("/api/auth/$path") {
                    contentType(ContentType.Application.Json)
                    if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
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

            val updated = postJson("me/update", """{"name":"New Name","avatarId":null}""", token)
            assertEquals(HttpStatusCode.OK, updated.status)

            val changedPassword = postJson("me/change-password", """{"oldPassword":"$password","newPassword":"newPassword123"}""", token)
            assertEquals(HttpStatusCode.OK, changedPassword.status)

            val logout = client.post("/api/auth/logout") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.OK, logout.status)

            val actions =
                orThrow {
                    TestPostgres.db
                        .sql("SELECT action_type FROM audit_logs WHERE username = :login ORDER BY action_type")
                        .bind("login", login)
                        .list { it.asString("action_type") }
                }
            assertEquals(
                listOf(
                    AuditActionType.AUTH_CHANGE_PASSWORD,
                    AuditActionType.AUTH_LOGIN,
                    AuditActionType.AUTH_LOGOUT,
                    AuditActionType.AUTH_SIGNUP,
                ).map { it.code },
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

    @Test
    fun `HTTP требует имя и положительный срок а лимит занятий может быть null`() =
        testApplication {
            application { context(testDi) { configureServer() } }

            suspend fun postJson(path: String, body: String, token: String? = null) =
                client.post("/api/$path") {
                    contentType(ContentType.Application.Json)
                    if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                    setBody(body)
                }

            val signupBody = """{"companyName":"Acme","userName":"Owner","login":"owner@example.com","password":"password123","timezone":"UTC","currency":"RUB"}"""
            assertEquals(HttpStatusCode.BadRequest, postJson("auth/sign-up", signupBody.replace("Acme", "  ")).status)
            val signup = postJson("auth/sign-up", signupBody)
            assertEquals(HttpStatusCode.OK, signup.status)
            val token = serverJson.decodeFromString<LoginResponse>(signup.bodyAsText()).accessToken

            val group = GroupId.new()
            assertEquals(HttpStatusCode.BadRequest, postJson("groups/create", """{"id":"$group","name":"  "}""", token).status)
            val createdGroup = postJson("groups/create", """{"id":"$group","name":" Йога "}""", token)
            assertEquals(HttpStatusCode.OK, createdGroup.status)
            val clientId = ClientId.new()
            assertEquals(HttpStatusCode.OK, postJson("clients/create", """{"id":"$clientId","name":"Иван","gender":"MALE"}""", token).status)

            val tariff = TariffPlanId.new()
            val membership = MembershipId.new()
            val price = """{"minorUnits":10000,"currency":"RUB"}"""
            val tariffFields = """"id":"$tariff","name":"Тариф","durationUnit":"MONTHS","price":$price"""
            val membershipFields =
                """"id":"$membership","clientId":"$clientId","tariffPlanId":"$tariff","name":"Абонемент","groupIds":["$group"],"durationUnit":"MONTHS","price":$price,"activationMode":"IMMEDIATE","closeMode":"MANUAL""""
            val validTerms = ""","durationValue":1200,"sessions":null"""
            val invalidTerms =
                listOf(
                    ""","durationValue":0,"sessions":null""",
                    ""","durationValue":-1,"sessions":null""",
                    ""","durationValue":null,"sessions":null""",
                    ""","sessions":null""",
                    ""","durationValue":1,"sessions":0""",
                    ""","durationValue":1,"sessions":-1""",
                )
            for (terms in invalidTerms) {
                assertEquals(HttpStatusCode.BadRequest, postJson("tariffs/create", "{$tariffFields$terms}", token).status)
            }
            assertEquals(HttpStatusCode.OK, postJson("tariffs/create", "{$tariffFields$validTerms}", token).status)
            for (terms in invalidTerms) {
                assertEquals(HttpStatusCode.BadRequest, postJson("tariffs/update", "{$tariffFields$terms}", token).status)
                assertEquals(HttpStatusCode.BadRequest, postJson("memberships/issue", "{$membershipFields$terms}", token).status)
            }
            assertEquals(HttpStatusCode.OK, postJson("memberships/issue", "{$membershipFields$validTerms}", token).status)
        }
}
