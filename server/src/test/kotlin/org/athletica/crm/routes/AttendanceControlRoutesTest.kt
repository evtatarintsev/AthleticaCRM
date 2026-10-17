package org.athletica.crm.routes

import arrow.core.Either
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.attendancecontrol.AttendanceControlSettingsResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.configureServer
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.usecases.auth.User
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Тесты маршрутов настроек контроля посещаемости: значения по умолчанию, сохранение и отказ вне диапазона. */
class AttendanceControlRoutesTest {
    private lateinit var di: Di
    private lateinit var token: String
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию и запоминает токен её владельца. */
    private suspend fun signUp() {
        val result =
            TestPostgres.db.transaction {
                context(this, PasswordHasher(), DbUserDisplaySettings()) {
                    org.athletica.crm.usecases.auth.signUp(
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
        token = testJwtConfig.makeAccessToken(assertIs<Either.Right<User>>(result).value)
    }

    private suspend fun ApplicationTestBuilder.settings(): AttendanceControlSettingsResponse {
        val response = client.get("/api/org/attendance-control") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.update(enabled: Boolean, minutes: Int) =
        client.post("/api/org/attendance-control/update") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"overdueTaskEnabled":$enabled,"overdueTaskDelayMinutes":$minutes}""")
        }

    @Test
    fun `настройки по умолчанию, сохранение и отказ вне диапазона`() =
        runTest {
            signUp()
            testApplication {
                application { context(di) { configureServer() } }

                assertEquals(AttendanceControlSettingsResponse(true, 120), settings())

                assertEquals(HttpStatusCode.OK, update(enabled = false, minutes = 240).status)
                assertEquals(AttendanceControlSettingsResponse(false, 240), settings())

                val rejected = update(enabled = true, minutes = 5)
                assertTrue(rejected.status.value in 400..499, rejected.status.toString())
                assertTrue(rejected.bodyAsText().contains("INVALID_JOURNAL_CLOSE_DELAY"), rejected.bodyAsText())
                assertEquals(AttendanceControlSettingsResponse(false, 240), settings())
            }
        }
}
