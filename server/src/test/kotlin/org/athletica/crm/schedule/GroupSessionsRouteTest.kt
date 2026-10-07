package org.athletica.crm.schedule

import arrow.core.Either
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import org.athletica.crm.api.schemas.groups.GroupSessionsResponse
import org.athletica.crm.configureServer
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.usecases.auth.User
import org.athletica.crm.usecases.auth.signUp
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Тесты маршрута `GET /api/groups/sessions`: проверка периода и ответ проекции. */
class GroupSessionsRouteTest {
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

    /** Создаёт в филиале владельца группу с одним занятием в понедельник; возвращает обе сущности. */
    private suspend fun insertGroupWithSession(): Pair<GroupId, SessionId> {
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
        return groupId to sessionId
    }

    private suspend fun ApplicationTestBuilder.sessions(
        groupId: GroupId,
        from: LocalDate,
        to: LocalDate,
    ): HttpResponse =
        client.get("/api/groups/sessions?groupId=$groupId&from=$from&to=$to") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    @Test
    fun `возвращает занятия группы за период`() =
        runTest {
            signUpOwner()
            val (groupId, sessionId) = insertGroupWithSession()
            testApplication {
                application { context(di) { configureServer() } }

                val response = sessions(groupId, monday, monday.plus(6, DateTimeUnit.DAY))

                assertEquals(HttpStatusCode.OK, response.status)
                val body = json.decodeFromString<GroupSessionsResponse>(response.bodyAsText())
                assertEquals(listOf(sessionId), body.sessions.map { it.id })
                assertEquals("Большой зал", body.sessions.single().hall.name)
            }
        }

    @Test
    fun `период длиннее 62 дней отклоняется`() =
        runTest {
            signUpOwner()
            val (groupId, _) = insertGroupWithSession()
            testApplication {
                application { context(di) { configureServer() } }

                val response = sessions(groupId, monday, monday.plus(62, DateTimeUnit.DAY))

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue("SCHEDULE_PERIOD_TOO_LONG" in response.bodyAsText())
            }
        }

    @Test
    fun `конец периода раньше начала отклоняется`() =
        runTest {
            signUpOwner()
            val (groupId, _) = insertGroupWithSession()
            testApplication {
                application { context(di) { configureServer() } }

                val response = sessions(groupId, monday, monday.plus(-1, DateTimeUnit.DAY))

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue("INVALID_SCHEDULE_PERIOD" in response.bodyAsText())
            }
        }
}
