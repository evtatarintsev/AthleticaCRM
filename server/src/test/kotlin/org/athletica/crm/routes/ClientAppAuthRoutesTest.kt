package org.athletica.crm.routes

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
import kotlinx.coroutines.test.runTest
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.clientapp.ClientAppFixture
import org.athletica.crm.configureServer
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.AuthSessionId
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.security.ClientAppJwtConfig
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Сквозные тесты кабинета: cookie, разделение с контуром CRM, ротация refresh, выход и доступ к профилю. */
class ClientAppAuthRoutesTest {
    private lateinit var di: Di

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Токены кабинета из cookie ответа. */
    private data class Tokens(val access: String, val refresh: String)

    /** Значение cookie [name] из ответа [response]. */
    private fun HttpResponse.cookie(name: String): String? = headers.getAll(HttpHeaders.SetCookie).orEmpty().map { parseServerSetCookieHeader(it) }.firstOrNull { it.name == name }?.value

    /** Токены из ответа [this]. */
    private fun HttpResponse.tokens(): Tokens =
        Tokens(
            assertNotNull(cookie(ClientAppJwtConfig.COOKIE_ACCESS_TOKEN)),
            assertNotNull(cookie(ClientAppJwtConfig.COOKIE_REFRESH_TOKEN)),
        )

    /** POST [body] на клиентский маршрут [path] с cookie [cookies]. */
    private suspend fun ApplicationTestBuilder.post(path: String, body: String = "", cookies: String? = null): HttpResponse =
        client.post("/api/client-app/$path") {
            contentType(ContentType.Application.Json)
            cookies?.let { header(HttpHeaders.Cookie, it) }
            setBody(body)
        }

    /** Вход номером [PHONE]: запрос кода, подмена хэша на известный код, вход с принятием условий. */
    private suspend fun ApplicationTestBuilder.login(): Tokens {
        assertEquals(HttpStatusCode.OK, post("auth/request-code", """{"phone":"$PHONE"}""").status)
        orThrow {
            TestPostgres.db.sql("UPDATE client_app_codes SET code_hash = encode(sha256(convert_to(id::text || '$CODE', 'UTF8')), 'hex')").execute()
        }
        val response =
            client.post("/api/client-app/auth/login") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, SAFARI_IOS)
                setBody("""{"phone":"$PHONE","code":"$CODE","acceptedTermsVersion":"${di.clientAppConfig.termsVersion}"}""")
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.tokens()
    }

    /** Обновление по refresh токену [refresh]. */
    private suspend fun ApplicationTestBuilder.refresh(refresh: String): HttpResponse = post("auth/refresh", cookies = "${ClientAppJwtConfig.COOKIE_REFRESH_TOKEN}=$refresh")

    /** Выход с access токеном [access]. */
    private suspend fun ApplicationTestBuilder.logout(access: String): HttpResponse = post("auth/logout", cookies = "${ClientAppJwtConfig.COOKIE_ACCESS_TOKEN}=$access")

    /** Подписанный CRM access токен сотрудника. */
    private fun crmToken(): String =
        testJwtConfig.makeAccessToken(
            object : AuthenticatedUser {
                override val id = UserId.new()
                override val orgId = OrgId.new()
                override val branchId = BranchId.new()
                override val employeeId = EmployeeId.new()
                override val username = "owner@example.com"
            },
            AuthSessionId.new(),
        )

    @Test
    fun `вход ставит HttpOnly cookie кабинета и сеанс с названием браузера и системы`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                assertEquals(HttpStatusCode.OK, post("auth/request-code", """{"phone":"$PHONE"}""").status)
                orThrow { TestPostgres.db.sql("UPDATE client_app_codes SET code_hash = encode(sha256(convert_to(id::text || '$CODE', 'UTF8')), 'hex')").execute() }
                val response =
                    client.post("/api/client-app/auth/login") {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.UserAgent, SAFARI_IOS)
                        setBody("""{"phone":"$PHONE","code":"$CODE","acceptedTermsVersion":"${di.clientAppConfig.termsVersion}"}""")
                    }

                val cookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty().map { parseServerSetCookieHeader(it) }
                val access = cookies.single { it.name == ClientAppJwtConfig.COOKIE_ACCESS_TOKEN }
                val refresh = cookies.single { it.name == ClientAppJwtConfig.COOKIE_REFRESH_TOKEN }
                assertTrue(access.httpOnly && refresh.httpOnly)
                assertEquals("/api/client-app", access.path)
                assertEquals("/api/client-app/auth/refresh", refresh.path)
                assertEquals(null, access.domain)
                val name = orThrow { TestPostgres.db.sql("SELECT name FROM client_app_sessions").firstOrNull { it.get("name", String::class.java) } }
                assertEquals("Safari, iOS", name)
            }
        }

    @Test
    fun `клиентский токен не проходит на маршруты CRM`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val tokens = login()

                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer ${tokens.access}") }.status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/auth/me") { header(HttpHeaders.Cookie, "${JwtConfig.COOKIE_ACCESS_TOKEN}=${tokens.access}") }.status)
            }
        }

    @Test
    fun `токен и cookie CRM не принимаются клиентским API`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val crm = crmToken()

                assertEquals(HttpStatusCode.Unauthorized, logout(crm).status)
                assertEquals(HttpStatusCode.Unauthorized, post("auth/logout", cookies = "${JwtConfig.COOKIE_ACCESS_TOKEN}=$crm").status)
                assertEquals(
                    HttpStatusCode.Unauthorized,
                    client.post("/api/client-app/auth/logout") { header(HttpHeaders.Authorization, "Bearer $crm") }.status,
                )
                assertEquals(HttpStatusCode.Unauthorized, refresh(crm).status)
            }
        }

    @Test
    fun `две вкладки в окне 60 секунд обе получают новые токены`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val tokens = login()

                assertEquals(HttpStatusCode.OK, refresh(tokens.refresh).status)
                val second = refresh(tokens.refresh)
                assertEquals(HttpStatusCode.OK, second.status)
                assertEquals(HttpStatusCode.OK, logout(second.tokens().access).status)
            }
        }

    @Test
    fun `несколько вкладок в окне гонки получают один текущий refresh и сохраняют сеанс`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val original = login()
                val current = refresh(original.refresh).tokens()
                repeat(3) {
                    val response = refresh(original.refresh)
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(
                        di.clientAppJwtConfig.verifier.verify(current.refresh).id,
                        di.clientAppJwtConfig.verifier.verify(response.tokens().refresh).id,
                    )
                }
                val next = refresh(current.refresh)
                assertEquals(HttpStatusCode.OK, next.status)
                assertEquals(HttpStatusCode.OK, logout(next.tokens().access).status)
            }
        }

    @Test
    fun `украденный refresh завершает сеанс вместе с его текущими токенами`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val stolen = login()
                val current = refresh(stolen.refresh).tokens()
                orThrow { TestPostgres.db.sql("UPDATE client_app_sessions SET rotated_at = rotated_at - interval '1 hour'").execute() }

                assertEquals(HttpStatusCode.Unauthorized, refresh(stolen.refresh).status)
                assertEquals(HttpStatusCode.Unauthorized, refresh(current.refresh).status)
                assertEquals(HttpStatusCode.Unauthorized, logout(current.access).status)
            }
        }

    @Test
    fun `после выхода refresh отклоняется`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val tokens = login()

                val response = logout(tokens.access)
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("", response.cookie(ClientAppJwtConfig.COOKIE_ACCESS_TOKEN))
                assertEquals(HttpStatusCode.Unauthorized, refresh(tokens.refresh).status)
            }
        }

    @Test
    fun `сверх лимита запрос кода сообщает время до повтора`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                assertEquals(HttpStatusCode.OK, post("auth/request-code", """{"phone":"$PHONE"}""").status)

                val again = post("auth/request-code", """{"phone":"$PHONE"}""")
                assertEquals(HttpStatusCode.OK, again.status)
                assertTrue(again.bodyAsText().contains("\"sent\":false"), again.bodyAsText())
            }
        }

    @Test
    fun `данные профиля без подключения — CLIENT_NOT_FOUND, после подключения — главная`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val (orgId, clientId) = orThrow { ClientAppFixture.org("Динамо").let { it to ClientAppFixture.client(it, PHONE, name = "Иван Петров") } }
                val cookies = "${ClientAppJwtConfig.COOKIE_ACCESS_TOKEN}=${login().access}"

                suspend fun home() = client.get("/api/client-app/home?orgId=${orgId.value}&clientId=${clientId.value}") { header(HttpHeaders.Cookie, cookies) }

                val before = home()
                assertEquals(HttpStatusCode.BadRequest, before.status)
                assertTrue(before.bodyAsText().contains("CLIENT_NOT_FOUND"), before.bodyAsText())
                assertFalse(client.get("/api/client-app/invitations") { header(HttpHeaders.Cookie, cookies) }.bodyAsText().contains("Иван"))

                assertEquals(HttpStatusCode.OK, post("connect", """{"orgId":"${orgId.value}"}""", cookies).status)
                val after = home()
                assertEquals(HttpStatusCode.OK, after.status)
                assertTrue(after.bodyAsText().contains("Иван Петров"), after.bodyAsText())
            }
        }

    /** GET клиентского маршрута [path] с access токеном [access]. */
    private suspend fun ApplicationTestBuilder.get(path: String, access: String): HttpResponse = client.get("/api/client-app/$path") { header(HttpHeaders.Cookie, "${ClientAppJwtConfig.COOKIE_ACCESS_TOKEN}=$access") }

    /** Вход вторым браузером: прежние коды удаляются, чтобы не упереться в лимит частоты. */
    private suspend fun ApplicationTestBuilder.loginAgain(): Tokens {
        orThrow { TestPostgres.db.sql("DELETE FROM client_app_codes").execute() }
        return login()
    }

    /** Идентификатор сеанса из access токена [access]. */
    private fun sessionId(access: String): String = di.clientAppJwtConfig.verifier.verify(access).getClaim(JwtConfig.CLAIM_SESSION_ID).asString()

    @Test
    fun `после завершения сеанса другого устройства его access и refresh — 401`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val old = login()
                val current = loginAgain()

                val list = get("sessions", current.access)
                assertEquals(HttpStatusCode.OK, list.status)
                val body = list.bodyAsText()
                assertTrue(body.contains(sessionId(old.access)), body)
                assertTrue(body.contains(""""id":"${sessionId(current.access)}","name":"Safari, iOS""""), body)

                val cookies = "${ClientAppJwtConfig.COOKIE_ACCESS_TOKEN}=${current.access}"
                assertEquals(HttpStatusCode.OK, post("sessions/terminate", """{"sessionId":"${sessionId(old.access)}"}""", cookies).status)

                assertEquals(HttpStatusCode.Unauthorized, get("sessions", old.access).status)
                assertEquals(HttpStatusCode.Unauthorized, refresh(old.refresh).status)
                val after = get("sessions", current.access)
                assertEquals(HttpStatusCode.OK, after.status)
                assertFalse(after.bodyAsText().contains(sessionId(old.access)))
            }
        }

    @Test
    fun `текущий сеанс завершается выходом, а не из списка`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val tokens = login()

                val response = post("sessions/terminate", """{"sessionId":"${sessionId(tokens.access)}"}""", "${ClientAppJwtConfig.COOKIE_ACCESS_TOKEN}=${tokens.access}")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("CANNOT_TERMINATE_CURRENT_SESSION"), response.bodyAsText())
                assertEquals(HttpStatusCode.OK, get("sessions", tokens.access).status)
            }
        }

    @Test
    fun `удаление аккаунта завершает все сеансы, а повторный вход создаёт новый аккаунт без приглашений`() =
        runTest {
            testApplication {
                application { context(di) { configureServer() } }
                val volna =
                    orThrow {
                        val dynamo = ClientAppFixture.org("Динамо")
                        val volna = ClientAppFixture.org("Волна", cabinet = false)
                        ClientAppFixture.client(dynamo, PHONE, ClientAppAccessStatus.CONNECTED)
                        ClientAppFixture.client(dynamo, PHONE, ClientAppAccessStatus.INVITED, name = "Мария")
                        ClientAppFixture.client(volna, PHONE, ClientAppAccessStatus.CONNECTED)
                        ClientAppFixture.client(volna, PHONE, ClientAppAccessStatus.NOT_ME, name = "Пётр")
                        volna
                    }
                val other = login()
                val tokens = loginAgain()

                val response = post("account/delete", cookies = "${ClientAppJwtConfig.COOKIE_ACCESS_TOKEN}=${tokens.access}")
                assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
                assertEquals("", response.cookie(ClientAppJwtConfig.COOKIE_ACCESS_TOKEN))
                assertEquals(HttpStatusCode.Unauthorized, get("sessions", tokens.access).status)
                assertEquals(HttpStatusCode.Unauthorized, get("sessions", other.access).status)
                assertEquals(HttpStatusCode.Unauthorized, refresh(other.refresh).status)

                val statuses = orThrow { TestPostgres.db.sql("SELECT status FROM client_app_access ORDER BY status").list { it.get("status", String::class.java) } }
                assertEquals(listOf("DISABLED", "DISABLED", "DISABLED", "NOT_ME"), statuses)
                val anonymized = orThrow { TestPostgres.db.sql("SELECT count(*)::int AS n FROM client_app_accounts WHERE deleted_at IS NOT NULL AND phone_digits IS NULL").firstOrNull { it.get("n", Integer::class.java) } }
                assertEquals(1, anonymized?.toInt())

                val again = loginAgain()
                orThrow { TestPostgres.db.sql("UPDATE organizations SET client_app_enabled = true WHERE id = :id").bind("id", volna).execute() }
                val invitations = get("invitations", again.access).bodyAsText()
                assertTrue(invitations.contains(""""invitations":[]"""), invitations)
                val profiles = get("profiles", again.access).bodyAsText()
                assertTrue(profiles.contains(""""profiles":[]"""), profiles)
            }
        }

    private companion object {
        const val PHONE = "+7 903 123-45-67"
        const val CODE = "123456"
        const val SAFARI_IOS =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"
    }
}
