package org.athletica.crm

import arrow.core.Either
import arrow.core.raise.context.either
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.auth.DevicePlatform
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SessionListResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.security.JwtConfig
import org.junit.Before
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Сквозные тесты сессий входа: список, завершение, выход, смена филиала и пароля, блокировка. */
class AuthSessionsRoutesTest {
    private lateinit var di: Di
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию с владельцем `owner@example.com` и возвращает владельца. */
    private suspend fun signUpOwner(): AuthenticatedUser {
        val result =
            either {
                di.database.transaction {
                    di.auth.signup(
                        SignUpRequest(
                            companyName = testName("Acme"),
                            userName = testName("John"),
                            login = OWNER_LOGIN,
                            password = PASSWORD,
                            timezone = "UTC",
                            currency = Currency.RUB,
                        ),
                        lang = Lang.RU,
                        clientIp = null,
                    )
                }
            }
        return assertIs<Either.Right<AuthenticatedUser>>(result).value
    }

    /** Входит логином [login] в филиал владельца [owner] с User-Agent [userAgent]. */
    private suspend fun ApplicationTestBuilder.login(owner: AuthenticatedUser, login: String = OWNER_LOGIN, userAgent: String = CHROME_MAC): LoginResponse {
        val response =
            client.post("/api/auth/login") {
                header(HttpHeaders.UserAgent, userAgent)
                contentType(ContentType.Application.Json)
                setBody("""{"username":"$login","password":"$PASSWORD","branchId":"${owner.branchId}"}""")
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    /** POST [body] на [path] с токеном [token]. */
    private suspend fun ApplicationTestBuilder.post(token: String, path: String, body: String = ""): HttpResponse =
        client.post("/api/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** GET [path] с токеном [token]. */
    private suspend fun ApplicationTestBuilder.get(token: String, path: String): HttpResponse = client.get("/api/$path") { header(HttpHeaders.Authorization, "Bearer $token") }

    /** Сеансы, видимые с токеном [token] по маршруту [path]. */
    private suspend fun ApplicationTestBuilder.sessions(token: String, path: String = "auth/sessions"): SessionListResponse {
        val response = get(token, path)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    /** Обновляет токены по refresh токену [refreshToken]. */
    private suspend fun ApplicationTestBuilder.refresh(refreshToken: String): HttpResponse = client.post("/api/auth/refresh-token") { header("X-Refresh-Token", refreshToken) }

    /** Создаёт сотрудника [id] с логином [login] от имени владельца с токеном [ownerToken]. */
    private suspend fun ApplicationTestBuilder.invite(ownerToken: String, id: EmployeeId, login: String) {
        assertEquals(HttpStatusCode.OK, post(ownerToken, "employees/create", """{"id":"$id","name":"Анна"}""").status)
        assertEquals(HttpStatusCode.OK, post(ownerToken, "employees/send-access", """{"employeeId":"$id","email":"$login","password":"$PASSWORD"}""").status)
    }

    @Test
    fun `список сеансов отмечает текущий и распознаёт устройство`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val laptop = login(owner)
                login(owner, userAgent = CHROME_ANDROID)

                val items = sessions(laptop.accessToken).items
                assertEquals(2, items.size)
                val current = items.single { it.current }
                assertEquals("Chrome 142", current.browser)
                assertEquals("macOS", current.os)
                assertEquals(DevicePlatform.ANDROID, items.single { !it.current }.platform)
            }
        }

    @Test
    fun `завершённый сеанс сразу теряет доступ и обновление`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val laptop = login(owner)
                val phone = login(owner, userAgent = CHROME_ANDROID)
                val phoneId = sessions(laptop.accessToken).items.single { !it.current }.id

                assertEquals(HttpStatusCode.OK, post(laptop.accessToken, "auth/sessions/terminate", """{"sessionId":"$phoneId"}""").status)

                assertEquals(HttpStatusCode.Unauthorized, get(phone.accessToken, "auth/me").status)
                assertNotEquals(HttpStatusCode.OK, refresh(phone.refreshToken).status)
                assertEquals(1, sessions(laptop.accessToken).items.size)
            }
        }

    @Test
    fun `текущий сеанс завершается только выходом`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val laptop = login(owner)
                val currentId = sessions(laptop.accessToken).items.single().id

                val response = post(laptop.accessToken, "auth/sessions/terminate", """{"sessionId":"$currentId"}""")
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue("CANNOT_TERMINATE_CURRENT_SESSION" in response.bodyAsText())
            }
        }

    @Test
    fun `завершить все другие оставляет текущий`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val laptop = login(owner)
                val phone = login(owner, userAgent = CHROME_ANDROID)
                login(owner)

                assertEquals(HttpStatusCode.OK, post(laptop.accessToken, "auth/sessions/terminate-others").status)

                assertEquals(1, sessions(laptop.accessToken).items.size)
                assertEquals(HttpStatusCode.Unauthorized, get(phone.accessToken, "auth/me").status)
            }
        }

    @Test
    fun `выход завершает только свой сеанс`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val laptop = login(owner)
                val phone = login(owner, userAgent = CHROME_ANDROID)

                assertEquals(HttpStatusCode.OK, post(laptop.accessToken, "auth/logout").status)

                assertNotEquals(HttpStatusCode.OK, refresh(laptop.refreshToken).status)
                assertEquals(HttpStatusCode.OK, get(phone.accessToken, "auth/me").status)
            }
        }

    @Test
    fun `токен без сеанса отклоняется`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val legacy =
                    JWT
                        .create()
                        .withIssuer(JwtConfig.ISSUER)
                        .withAudience(JwtConfig.AUDIENCE)
                        .withClaim(JwtConfig.CLAIM_USER_ID, owner.id.toString())
                        .withClaim(JwtConfig.CLAIM_ORG_ID, owner.orgId.toString())
                        .withClaim(JwtConfig.CLAIM_BRANCH_ID, owner.branchId.toString())
                        .withClaim(JwtConfig.CLAIM_EMPLOYEE_ID, owner.employeeId.toString())
                        .withClaim(JwtConfig.CLAIM_USERNAME, owner.username)
                        .withClaim(JwtConfig.CLAIM_TYPE, JwtConfig.TYPE_ACCESS)
                        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
                        .sign(Algorithm.HMAC256(TEST_SECRET))

                assertEquals(HttpStatusCode.Unauthorized, get(legacy, "auth/me").status)
            }
        }

    @Test
    fun `обновление и смена филиала остаются в том же сеансе`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val tokens = login(owner)
                val refreshed = refresh(tokens.refreshToken)
                assertEquals(HttpStatusCode.OK, refreshed.status, refreshed.bodyAsText())
                val next = json.decodeFromString<LoginResponse>(refreshed.bodyAsText())

                val switched = post(next.accessToken, "auth/switch-branch", """{"branchId":"${owner.branchId}"}""")
                assertEquals(HttpStatusCode.OK, switched.status, switched.bodyAsText())
                val afterSwitch = json.decodeFromString<LoginResponse>(switched.bodyAsText())

                assertEquals(1, sessions(afterSwitch.accessToken).items.size)
                assertEquals(HttpStatusCode.OK, refresh(afterSwitch.refreshToken).status)
            }
        }

    @Test
    fun `смена пароля завершает другие сеансы`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val laptop = login(owner)
                val phone = login(owner, userAgent = CHROME_ANDROID)

                val changed = post(laptop.accessToken, "auth/me/change-password", """{"oldPassword":"$PASSWORD","newPassword":"newPassword123"}""")
                assertEquals(HttpStatusCode.OK, changed.status, changed.bodyAsText())

                assertEquals(HttpStatusCode.OK, get(laptop.accessToken, "auth/me").status)
                assertEquals(HttpStatusCode.Unauthorized, get(phone.accessToken, "auth/me").status)
            }
        }

    @Test
    fun `блокировка завершает сеансы, после разблокировки старые токены не действуют`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val ownerToken = login(owner).accessToken
                val id = EmployeeId.new()
                invite(ownerToken, id, "anna@example.com")
                val anna = login(owner, "anna@example.com")
                assertEquals(1, sessions(ownerToken, "employees/sessions?employeeId=$id").items.size)

                assertEquals(HttpStatusCode.OK, post(ownerToken, "employees/block", """{"employeeId":"$id"}""").status)
                assertEquals(0, sessions(ownerToken, "employees/sessions?employeeId=$id").items.size)

                assertEquals(HttpStatusCode.OK, post(ownerToken, "employees/unblock", """{"employeeId":"$id"}""").status)
                assertEquals(HttpStatusCode.Unauthorized, get(anna.accessToken, "auth/me").status)
                assertNotEquals(HttpStatusCode.OK, refresh(anna.refreshToken).status)
            }
        }

    @Test
    fun `сеанс сотрудника завершается из карточки, сеанс владельца — нет`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val ownerToken = login(owner).accessToken
                val id = EmployeeId.new()
                invite(ownerToken, id, "anna@example.com")
                val anna = login(owner, "anna@example.com")
                val annaSession = sessions(ownerToken, "employees/sessions?employeeId=$id").items.single().id

                val terminated = post(ownerToken, "employees/sessions/terminate", """{"employeeId":"$id","sessionId":"$annaSession"}""")
                assertEquals(HttpStatusCode.OK, terminated.status, terminated.bodyAsText())
                assertEquals(HttpStatusCode.Unauthorized, get(anna.accessToken, "auth/me").status)

                val annaAgain = login(owner, "anna@example.com")
                val ownerSession = sessions(annaAgain.accessToken, "employees/sessions?employeeId=${owner.employeeId}").items.first().id
                val rejected = post(annaAgain.accessToken, "employees/sessions/terminate", """{"employeeId":"${owner.employeeId}","sessionId":"$ownerSession"}""")
                assertEquals(HttpStatusCode.BadRequest, rejected.status)
                assertTrue("EMPLOYEE_OWNER_CANNOT_BE_BLOCKED" in rejected.bodyAsText())
            }
        }

    private companion object {
        /** Логин владельца организации. */
        const val OWNER_LOGIN = "owner@example.com"

        /** Пароль всех пользователей теста. */
        const val PASSWORD = "password123"

        /** Секрет подписи [testJwtConfig]. */
        const val TEST_SECRET = "test-secret-key-for-unit-tests"

        /** User-Agent Chrome на macOS. */
        const val CHROME_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"

        /** User-Agent Chrome на Android. */
        const val CHROME_ANDROID = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"
    }
}
