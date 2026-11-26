package org.athletica.crm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.athletica.crm.api.schemas.auth.AuthBranchesResponse
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppHomeResponse
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationsResponse
import org.athletica.crm.api.schemas.clientappadmin.ClientAppClientAccessResponse
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.clientapp.ClientAppFixture
import org.athletica.crm.core.audit.AuditActionType
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.messaging.toJson
import org.athletica.crm.domain.mail.EmailDispatcher
import org.athletica.crm.security.ClientAppJwtConfig
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

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
    fun `HTTP принимает контакты в старом формате и отклоняет два основных телефона`() =
        testApplication {
            application { context(testDi) { configureServer() } }

            suspend fun postJson(path: String, body: String, token: String? = null) =
                client.post("/api/$path") {
                    contentType(ContentType.Application.Json)
                    if (token != null) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(body)
                }

            val signupBody = """{"companyName":"Acme","userName":"Owner","login":"owner@example.com","password":"password123","timezone":"UTC","currency":"RUB"}"""
            val token = serverJson.decodeFromString<LoginResponse>(postJson("auth/sign-up", signupBody).bodyAsText()).accessToken

            val legacy = postJson("clients/create", """{"id":"${ClientId.new()}","name":"Иван","gender":"MALE","contacts":[{"type":"PHONE","value":"+79990001122"}]}""", token)
            assertEquals(HttpStatusCode.OK, legacy.status)
            val contact = serverJson.decodeFromString<ClientDetailResponse>(legacy.bodyAsText()).contacts.single()
            assertEquals(true, contact.isPrimary)
            assertEquals(null, contact.label)

            val duplicatePrimary =
                postJson(
                    "clients/create",
                    """{"id":"${ClientId.new()}","name":"Пётр","gender":"MALE","contacts":[{"type":"PHONE","value":"1","isPrimary":true},{"type":"PHONE","value":"2","isPrimary":true}]}""",
                    token,
                )
            assertEquals(HttpStatusCode.BadRequest, duplicatePrimary.status)
            assertContains(duplicatePrimary.bodyAsText(), "CONTACT_PRIMARY_DUPLICATE")
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

    @Test
    fun `кабинет клиента — включить, пригласить, войти, подключиться, главная`() =
        testApplication {
            application { context(testDi) { configureServer() } }
            val cabinet = CabinetHttp(this)
            val clientId = cabinet.signUpWithSmsChannel()

            assertEquals(HttpStatusCode.OK, cabinet.crm("client-app-admin/set-enabled", """{"enabled":true}""").status)
            assertEquals(HttpStatusCode.OK, cabinet.crm("clients/create", cabinet.client(clientId, MOM)).status)
            val invited = cabinet.crm("client-app-admin/invite", """{"clientId":"$clientId"}""")
            assertEquals(HttpStatusCode.OK, invited.status, invited.bodyAsText())
            assertContains(invited.bodyAsText(), "\"notified\":true")

            val cookies = cabinet.signIn(MOM)
            val orgId = serverJson.decodeFromString<ClientAppInvitationsResponse>(cabinet.cabinetGet("invitations", cookies).bodyAsText()).invitations.single().orgId
            assertEquals(HttpStatusCode.OK, cabinet.cabinetPost("connect", """{"orgId":"$orgId"}""", cookies).status)
            val home = cabinet.cabinetGet("home?orgId=$orgId&clientId=$clientId", cookies)

            assertEquals(HttpStatusCode.OK, home.status, home.bodyAsText())
            assertEquals("Иван", serverJson.decodeFromString<ClientAppHomeResponse>(home.bodyAsText()).clientName)
        }

    @Test
    fun `кабинет клиента — чужой номер, «Это не я», задача, исправление номера отключает старый доступ`() =
        testApplication {
            application { context(testDi) { configureServer() } }
            val cabinet = CabinetHttp(this)
            val clientId = cabinet.signUpWithSmsChannel()
            cabinet.crm("client-app-admin/set-enabled", """{"enabled":true}""")
            cabinet.crm("clients/create", cabinet.client(clientId, STRANGER))
            cabinet.crm("client-app-admin/invite", """{"clientId":"$clientId"}""")

            val cookies = cabinet.signIn(STRANGER)
            val orgId = serverJson.decodeFromString<ClientAppInvitationsResponse>(cabinet.cabinetGet("invitations", cookies).bodyAsText()).invitations.single().orgId
            assertEquals(HttpStatusCode.OK, cabinet.cabinetPost("not-me", """{"orgId":"$orgId"}""", cookies).status)
            val tasks = orThrow { TestPostgres.db.sql("SELECT title FROM tasks WHERE client_id = :id").bind("id", clientId).list { it.asString("title") } }
            assertEquals(listOf("Проверьте номер телефона"), tasks)

            assertEquals(HttpStatusCode.OK, cabinet.crm("clients/edit", cabinet.client(clientId, MOM)).status)
            val card = cabinet.crmGet("client-app-admin/client?clientId=$clientId").bodyAsText()
            val phones = serverJson.decodeFromString<ClientAppClientAccessResponse>(card).phones
            assertEquals(listOf("79031234567" to ClientAppAccessStatus.NOT_INVITED), phones.map { it.phone to it.status })
            val old = orThrow { TestPostgres.db.sql("SELECT status FROM client_app_access WHERE phone_digits = '79990000000'").firstOrNull { it.asString("status") } }
            assertEquals("DISABLED", old)
        }

    /** HTTP-шаги сквозных тестов кабинета клиента: CRM владельца по Bearer-токену и кабинет по cookie. */
    private class CabinetHttp(private val app: ApplicationTestBuilder) {
        private lateinit var token: String

        /** Регистрирует организацию с SMS-каналом и возвращает идентификатор будущего клиента. */
        suspend fun signUpWithSmsChannel(): ClientId {
            val signup =
                app.client.post("/api/auth/sign-up") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"companyName":"Динамо","userName":"Owner","login":"owner@example.com","password":"password123","timezone":"UTC","currency":"RUB"}""")
                }
            token = serverJson.decodeFromString<LoginResponse>(signup.bodyAsText()).accessToken
            orThrow {
                TestPostgres.db.sql("INSERT INTO channel_integrations (id, org_id, name, config, enabled) SELECT :id, id, 'SMS', :config::jsonb, true FROM organizations")
                    .bind("id", Uuid.generateV7()).bind("config", ClientAppFixture.SMS_CONFIG.toJson()).execute()
            }
            return ClientId.new()
        }

        /** Тело создания или изменения клиента [id] «Иван» с телефоном [phone]. */
        fun client(id: ClientId, phone: String) = """{"id":"$id","name":"Иван","gender":"MALE","contacts":[{"type":"PHONE","value":"$phone"}]}"""

        /** POST [body] на маршрут CRM [path]. */
        suspend fun crm(path: String, body: String): HttpResponse =
            app.client.post("/api/$path") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(body)
            }

        /** GET маршрута CRM [path]. */
        suspend fun crmGet(path: String): HttpResponse = app.client.get("/api/$path") { header(HttpHeaders.Authorization, "Bearer $token") }

        /** Вход в кабинет номером [phone] с подменой кода на известный; возвращает cookie access-токена. */
        suspend fun signIn(phone: String): String {
            cabinetPost("auth/request-code", """{"phone":"$phone"}""", null)
            orThrow { TestPostgres.db.sql("UPDATE client_app_codes SET code_hash = encode(sha256(convert_to(id::text || '123456', 'UTF8')), 'hex')").execute() }
            val login = cabinetPost("auth/login", """{"phone":"$phone","code":"123456","acceptedTermsVersion":"dev"}""", null)
            assertEquals(HttpStatusCode.OK, login.status, login.bodyAsText())
            val access = login.headers.getAll(HttpHeaders.SetCookie).orEmpty().map { parseServerSetCookieHeader(it) }.single { it.name == ClientAppJwtConfig.COOKIE_ACCESS_TOKEN }
            return "${access.name}=${access.value}"
        }

        /** POST [body] на маршрут кабинета [path] с cookie [cookies]. */
        suspend fun cabinetPost(path: String, body: String, cookies: String?): HttpResponse =
            app.client.post("/api/client-app/$path") {
                contentType(ContentType.Application.Json)
                cookies?.let { header(HttpHeaders.Cookie, it) }
                setBody(body)
            }

        /** GET маршрута кабинета [path] с cookie [cookies]. */
        suspend fun cabinetGet(path: String, cookies: String): HttpResponse = app.client.get("/api/client-app/$path") { header(HttpHeaders.Cookie, cookies) }
    }

    private companion object {
        /** Номер мамы. */
        const val MOM = "+7 903 123-45-67"

        /** Номер, записанный по ошибке. */
        const val STRANGER = "+7 999 000-00-00"
    }
}
