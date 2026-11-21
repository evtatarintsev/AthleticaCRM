package org.athletica.crm

import arrow.core.Either
import arrow.core.raise.context.either
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
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.employees.EmployeeAccessStatus
import org.athletica.crm.api.schemas.employees.EmployeeDetailResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.money.Currency
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Сквозные тесты доступа сотрудника: статусы, блокировка и разблокировка, отметка активности. */
class EmployeeAccessRoutesTest {
    private lateinit var di: Di
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию и возвращает её владельца. */
    private suspend fun signUpOwner(): AuthenticatedUser {
        val result =
            either {
                di.database.transaction {
                    di.auth.signup(
                        SignUpRequest(
                            companyName = testName("Acme"),
                            userName = testName("John"),
                            login = "owner@example.com",
                            password = "password123",
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

    /** Отправляет POST-запрос [body] на [path] с токеном [token]. */
    private suspend fun ApplicationTestBuilder.post(token: String, path: String, body: String): HttpResponse =
        client.post("/api/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** Входит логином [login] в филиал владельца [owner]. */
    private suspend fun ApplicationTestBuilder.login(owner: AuthenticatedUser, login: String): HttpResponse =
        client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$login","password":"password123","branchId":"${owner.branchId}"}""")
        }

    /** Карточка сотрудника [id], запрошенная с токеном [token]. */
    private suspend fun ApplicationTestBuilder.detail(token: String, id: EmployeeId): EmployeeDetailResponse {
        val response = client.get("/api/employees/detail?id=$id") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    /** Создаёт сотрудника [id] и выдаёт ему логин [login] от имени владельца с токеном [ownerToken]. */
    private suspend fun ApplicationTestBuilder.invite(ownerToken: String, id: EmployeeId, login: String) {
        val created = post(ownerToken, "employees/create", """{"id":"$id","name":"Анна"}""")
        assertEquals(HttpStatusCode.OK, created.status, created.bodyAsText())
        val invited = post(ownerToken, "employees/send-access", """{"employeeId":"$id","email":"$login","password":"password123"}""")
        assertEquals(HttpStatusCode.OK, invited.status, invited.bodyAsText())
    }

    @Test
    fun `статус меняется от без доступа до активного, активность фиксируется`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val ownerToken = testJwtConfig.sessionAccessToken(owner)
                val id = EmployeeId.new()

                val created = post(ownerToken, "employees/create", """{"id":"$id","name":"Анна"}""")
                assertEquals(HttpStatusCode.OK, created.status, created.bodyAsText())
                assertEquals(EmployeeAccessStatus.NO_ACCESS, detail(ownerToken, id).status)

                val invited = post(ownerToken, "employees/send-access", """{"employeeId":"$id","email":"anna@example.com","password":"password123"}""")
                assertEquals(HttpStatusCode.OK, invited.status, invited.bodyAsText())
                assertEquals(EmployeeAccessStatus.INVITED, detail(ownerToken, id).status)

                val employeeToken = json.decodeFromString<LoginResponse>(login(owner, "anna@example.com").bodyAsText()).accessToken
                client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer $employeeToken") }

                val active = detail(ownerToken, id)
                assertEquals(EmployeeAccessStatus.ACTIVE, active.status)
                assertNotNull(active.lastSeenAt)
                assertTrue(active.online)
            }
        }

    @Test
    fun `заблокированный теряет доступ, после разблокировки входит с прежним паролем`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val ownerToken = testJwtConfig.sessionAccessToken(owner)
                val id = EmployeeId.new()
                invite(ownerToken, id, "anna@example.com")
                val tokens = json.decodeFromString<LoginResponse>(login(owner, "anna@example.com").bodyAsText())
                client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}") }

                val blocked = post(ownerToken, "employees/block", """{"employeeId":"$id"}""")
                assertEquals(HttpStatusCode.OK, blocked.status, blocked.bodyAsText())
                assertEquals(EmployeeAccessStatus.BLOCKED, detail(ownerToken, id).status)

                val me = client.get("/api/auth/me") { header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}") }
                assertEquals(HttpStatusCode.Unauthorized, me.status)
                val refresh = client.post("/api/auth/refresh-token") { header("X-Refresh-Token", tokens.refreshToken) }
                assertNotEquals(HttpStatusCode.OK, refresh.status)
                assertNotEquals(HttpStatusCode.OK, login(owner, "anna@example.com").status)

                val unblocked = post(ownerToken, "employees/unblock", """{"employeeId":"$id"}""")
                assertEquals(HttpStatusCode.OK, unblocked.status, unblocked.bodyAsText())
                assertEquals(EmployeeAccessStatus.ACTIVE, detail(ownerToken, id).status)
                assertEquals(HttpStatusCode.OK, login(owner, "anna@example.com").status)
            }
        }

    @Test
    fun `владельца и себя заблокировать нельзя`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val ownerToken = testJwtConfig.sessionAccessToken(owner)
                val id = EmployeeId.new()
                invite(ownerToken, id, "anna@example.com")
                val employeeToken = json.decodeFromString<LoginResponse>(login(owner, "anna@example.com").bodyAsText()).accessToken

                val ownerBlock = post(employeeToken, "employees/block", """{"employeeId":"${owner.employeeId}"}""")
                assertEquals(HttpStatusCode.BadRequest, ownerBlock.status)
                assertTrue("EMPLOYEE_OWNER_CANNOT_BE_BLOCKED" in ownerBlock.bodyAsText())

                val selfBlock = post(employeeToken, "employees/block", """{"employeeId":"$id"}""")
                assertEquals(HttpStatusCode.BadRequest, selfBlock.status)
                assertTrue("EMPLOYEE_CANNOT_BLOCK_SELF" in selfBlock.bodyAsText())
            }
        }
}
