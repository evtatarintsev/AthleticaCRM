package org.athletica.crm.schedule

import arrow.core.Either
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.schedule.ScheduleListResponse
import org.athletica.crm.configureServer
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
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
import kotlin.test.assertTrue

/**
 * Тесты маршрута `POST /api/schedule/list`: проверка границ периода до обращения к проекции,
 * нормализация пустых фильтров и денормализованный ответ.
 */
class ScheduleListRouteTest {
    private lateinit var di: Di
    private lateinit var user: User
    private lateinit var token: String
    private val monday = LocalDate(2026, 3, 16)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию и запоминает её первого сотрудника и его токен. */
    private suspend fun signUpOwner() {
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
        user = assertIs<Either.Right<User>>(result).value
        token = testJwtConfig.makeAccessToken(user)
    }

    /** Создаёт в филиале владельца зал, группу и занятие с тренером-владельцем в понедельник. */
    private suspend fun insertSession(): SessionId {
        val hallId = HallId.new()
        TestPostgres.db
            .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Большой зал')")
            .bind("id", hallId)
            .bind("orgId", user.orgId)
            .bind("branchId", user.branchId)
            .execute()
        val groupId = GroupId.new()
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Йога')")
            .bind("id", groupId)
            .bind("orgId", user.orgId)
            .bind("branchId", user.branchId)
            .execute()
        val sessionId = SessionId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO sessions (id, org_id, group_id, date, start_time, end_time, hall_id)
                VALUES (:id, :orgId, :groupId, :date, '10:00'::time, '11:00'::time, :hallId)
                """.trimIndent(),
            )
            .bind("id", sessionId)
            .bind("orgId", user.orgId)
            .bind("groupId", groupId)
            .bind("date", monday)
            .bind("hallId", hallId)
            .execute()
        TestPostgres.db
            .sql("INSERT INTO session_employees (session_id, employee_id) VALUES (:s, :e)")
            .bind("s", sessionId)
            .bind("e", user.employeeId)
            .execute()
        return sessionId
    }

    private suspend fun sessionCount(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) AS cnt FROM sessions")
            .firstOrNull { it.asLong("cnt") } ?: 0L

    private suspend fun ApplicationTestBuilder.list(body: String): HttpResponse =
        client.post("/api/schedule/list") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private fun period(
        from: LocalDate,
        days: Int,
    ): String = """{"from":"$from","to":"${from.plus(days - 1, DateTimeUnit.DAY)}"}"""

    @Test
    fun `период в 62 дня принимается`() =
        runTest {
            signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }

                assertEquals(HttpStatusCode.OK, list(period(monday, 62)).status)
            }
        }

    @Test
    fun `период в 63 дня отклоняется без выдачи занятий`() =
        runTest {
            signUpOwner()
            insertSession()
            val before = sessionCount()
            testApplication {
                application { context(di) { configureServer() } }

                val response = list(period(monday, 63))

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue("SCHEDULE_PERIOD_TOO_LONG" in response.bodyAsText())
            }
            assertEquals(before, sessionCount())
        }

    @Test
    fun `конец периода раньше начала отклоняется`() =
        runTest {
            signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }

                val response = list("""{"from":"$monday","to":"${monday.plus(-1, DateTimeUnit.DAY)}"}""")

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue("INVALID_SCHEDULE_PERIOD" in response.bodyAsText())
            }
        }

    @Test
    fun `запрос с пустыми фильтрами возвращает все занятия недели с названиями`() =
        runTest {
            signUpOwner()
            val sessionId = insertSession()
            testApplication {
                application { context(di) { configureServer() } }

                val response =
                    list(
                        """{"from":"$monday","to":"${monday.plus(6, DateTimeUnit.DAY)}",""" +
                            """"hallIds":[],"disciplineIds":[],"employeeIds":[]}""",
                    )

                assertEquals(HttpStatusCode.OK, response.status)
                val item = json.decodeFromString<ScheduleListResponse>(response.bodyAsText()).sessions.single()
                assertEquals(sessionId, item.id)
                assertEquals("Йога", item.group.name)
                assertEquals("Большой зал", item.hall.name)
                assertEquals(listOf<EmployeeId>(user.employeeId), item.coaches.map { it.id })
                assertEquals(listOf("John"), item.coaches.map { it.name })
            }
        }
}
