package org.athletica.crm.schedule

import arrow.core.Either
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.configureServer
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.asLong
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.usecases.auth.User
import org.athletica.crm.usecases.auth.signUp
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Регрессия на исходную уязвимость: `GET /api/sessions/list` без параметров периода
 * отклоняется и не приводит к записи в БД, а слишком длинный период отклоняется отдельно.
 */
class SessionListRouteTest {
    private lateinit var di: Di
    private val today = java.time.LocalDate.now().toKotlinLocalDate()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию и возвращает access-токен её первого сотрудника. */
    private suspend fun accessToken(): String {
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
        return testJwtConfig.makeAccessToken(assertIs<Either.Right<User>>(result).value)
    }

    /** Количество занятий в базе. */
    private suspend fun sessionCount(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) AS cnt FROM sessions")
            .firstOrNull { it.asLong("cnt") } ?: 0L

    @Test
    fun `запрос без параметров отклоняется и не пишет в базу`() =
        runTest {
            val token = accessToken()
            testApplication {
                application { context(di) { configureServer() } }

                val response =
                    client.get("/api/sessions/list") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
            assertEquals(0L, sessionCount())
        }

    @Test
    fun `слишком длинный период отклоняется`() =
        runTest {
            val token = accessToken()
            testApplication {
                application { context(di) { configureServer() } }

                val response =
                    client.get("/api/sessions/list?from=$today&to=${today.plusDays(365)}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
            assertEquals(0L, sessionCount())
        }

    @Test
    fun `запрос с корректным периодом проходит`() =
        runTest {
            val token = accessToken()
            testApplication {
                application { context(di) { configureServer() } }

                val response =
                    client.get("/api/sessions/list?from=$today&to=${today.plusDays(30)}") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
            }
            assertEquals(0L, sessionCount())
        }
}
