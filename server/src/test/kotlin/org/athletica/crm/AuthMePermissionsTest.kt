package org.athletica.crm

import arrow.core.Either
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
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.usecases.auth.User
import org.athletica.crm.usecases.auth.signUp
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** Тесты списка прав в `GET /auth/me`: владелец получает все права, остальные — права своих ролей. */
class AuthMePermissionsTest {
    private lateinit var di: Di
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию и возвращает её владельца. */
    private suspend fun signUpOwner(): User {
        val result =
            TestPostgres.db.transaction {
                context(this, PasswordHasher(), DbUserDisplaySettings()) {
                    signUp(
                        SignUpRequest(
                            companyName = "Acme",
                            userName = "John",
                            login = "owner@example.com",
                            password = "password123",
                            timezone = "UTC",
                            currency = Currency.RUB,
                        ),
                    )
                }
            }
        return assertIs<Either.Right<User>>(result).value
    }

    /** Отправляет POST-запрос [body] на [path] от имени владельца токена [token]. */
    private suspend fun ApplicationTestBuilder.post(token: String, path: String, body: String): HttpResponse =
        client.post("/api/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** Права из ответа `GET /auth/me` для владельца токена [token]. */
    private suspend fun ApplicationTestBuilder.mePermissions(token: String): Set<UserPermission> {
        val response =
            client.get("/api/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return json.decodeFromString<AuthMeResponse>(response.bodyAsText()).permissions
    }

    @Test
    fun `владелец после регистрации получает все права`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }

                assertEquals(UserPermission.entries.toSet(), mePermissions(testJwtConfig.makeAccessToken(owner)))
            }
        }

    @Test
    fun `приглашённый сотрудник с одной ролью получает ровно права роли`() =
        runTest {
            val owner = signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                val ownerToken = testJwtConfig.makeAccessToken(owner)
                val roleId = Uuid.random()
                val employeeId = EmployeeId.new()

                val role = post(ownerToken, "employees/roles/create", """{"id":"$roleId","name":"Кассир","permissions":["CAN_VIEW_CLIENT_BALANCE"]}""")
                assertEquals(HttpStatusCode.OK, role.status, role.bodyAsText())
                val created = post(ownerToken, "employees/create", """{"id":"$employeeId","name":"Анна","roleIds":["$roleId"]}""")
                assertEquals(HttpStatusCode.OK, created.status, created.bodyAsText())
                val invited = post(ownerToken, "employees/send-access", """{"employeeId":"$employeeId","email":"anna@example.com","password":"password123"}""")
                assertEquals(HttpStatusCode.OK, invited.status, invited.bodyAsText())

                val login =
                    client.post("/api/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"username":"anna@example.com","password":"password123","branchId":"${owner.branchId}"}""")
                    }
                assertEquals(HttpStatusCode.OK, login.status, login.bodyAsText())
                val employeeToken = json.decodeFromString<LoginResponse>(login.bodyAsText()).accessToken

                assertEquals(setOf(UserPermission.CAN_VIEW_CLIENT_BALANCE), mePermissions(employeeToken))
            }
        }
}
