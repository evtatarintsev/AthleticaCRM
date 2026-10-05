package org.athletica.crm.schedule

import arrow.core.Either
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
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.configureServer
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.notifications.GroupScheduleChanged
import org.athletica.crm.domain.notifications.NotificationContent
import org.athletica.crm.domain.settings.DbUserDisplaySettings
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.usecases.auth.User
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты маршрутов расписания группы: установка расписания с датой вступления в силу,
 * отклонение даты в прошлом и немедленное появление занятий после создания группы.
 */
class GroupScheduleRouteTest {
    private lateinit var di: Di
    private lateinit var token: String
    private lateinit var user: User
    private var hallId: HallId = HallId.new()
    private val today = java.time.LocalDate.now().toKotlinLocalDate()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию, запоминает токен сотрудника и создаёт зал в его филиале. */
    private suspend fun signUpWithHall() {
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
        user = assertIs<Either.Right<User>>(result).value
        token = testJwtConfig.makeAccessToken(user)
        hallId = HallId.new()
        TestPostgres.db
            .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Зал')")
            .bind("id", hallId)
            .bind("orgId", user.orgId)
            .bind("branchId", user.branchId)
            .execute()
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) =
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.createGroup(id: GroupId) = postJson("/api/groups/create", """{"id":"$id","name":"Йога"}""")

    private fun slotsJson() = """[{"dayOfWeek":"MONDAY","startAt":"10:00","endAt":"11:00","hallId":"$hallId"}]"""

    @Test
    fun `расписание с будущей датой не действует сегодня и объявлено как изменение`() =
        runTest {
            signUpWithHall()
            val groupId = GroupId.new()
            testApplication {
                application { context(di) { configureServer() } }
                assertEquals(HttpStatusCode.OK, createGroup(groupId).status)

                val effectiveFrom = today.plusDays(30)
                val response =
                    postJson(
                        "/api/groups/set-schedule",
                        """{"groupId":"$groupId","effectiveFrom":"$effectiveFrom","slots":${slotsJson()}}""",
                    )

                assertEquals(HttpStatusCode.OK, response.status)
                val detail = json.decodeFromString<GroupDetailResponse>(response.bodyAsText())
                assertEquals(emptyList(), detail.schedule)
                assertEquals(effectiveFrom, detail.scheduleChangeAt)
            }
        }

    @Test
    fun `расписание с сегодняшней датой действует сразу`() =
        runTest {
            signUpWithHall()
            val groupId = GroupId.new()
            testApplication {
                application { context(di) { configureServer() } }
                createGroup(groupId)

                val response = postJson("/api/groups/set-schedule", """{"groupId":"$groupId","slots":${slotsJson()}}""")

                val detail = json.decodeFromString<GroupDetailResponse>(response.bodyAsText())
                assertEquals(hallId, detail.schedule.single().hallId)
                assertEquals(today, detail.schedule.single().validity?.from)
                assertNull(detail.scheduleChangeAt)
            }
        }

    @Test
    fun `дата вступления в силу в прошлом отклоняется`() =
        runTest {
            signUpWithHall()
            val groupId = GroupId.new()
            testApplication {
                application { context(di) { configureServer() } }
                createGroup(groupId)

                val response =
                    postJson(
                        "/api/groups/set-schedule",
                        """{"groupId":"$groupId","effectiveFrom":"${today.plusDays(-1)}","slots":${slotsJson()}}""",
                    )

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("SCHEDULE_EFFECTIVE_FROM_IN_PAST"))
            }
        }

    @Test
    fun `сразу после установки расписания занятия существуют`() =
        runTest {
            signUpWithHall()
            val groupId = GroupId.new()
            testApplication {
                application { context(di) { configureServer() } }
                createGroup(groupId)
                postJson("/api/groups/set-schedule", """{"groupId":"$groupId","slots":${slotsJson()}}""")

                val count =
                    TestPostgres.db
                        .sql("SELECT COUNT(*) AS cnt FROM sessions WHERE group_id = :groupId")
                        .bind("groupId", groupId)
                        .firstOrNull { it.asLong("cnt") } ?: 0L
                assertTrue(count > 0, "занятия должны существовать сразу после изменения расписания")
            }
        }

    /** Добавляет в организацию сотрудника [name] с признаком активности [isActive] и доступом [allBranches]. */
    private suspend fun insertColleague(name: String, isActive: Boolean, allBranches: Boolean): EmployeeId {
        val id = EmployeeId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO employees (id, org_id, name, is_owner, is_active, all_branches_access)
                VALUES (:id, :orgId, :name, false, :isActive, :allBranches)
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", user.orgId)
            .bind("name", name)
            .bind("isActive", isActive)
            .bind("allBranches", allBranches)
            .execute()
        return id
    }

    /** Получатели уведомлений в порядке возрастания идентификатора. */
    private suspend fun notificationRecipients(): List<EmployeeId> =
        TestPostgres.db
            .sql("SELECT employee_id FROM notification_recipients ORDER BY employee_id")
            .list { it.asUuid("employee_id").toEmployeeId() }

    @Test
    fun `изменение расписания уведомляет коллег с доступом к филиалу группы, кроме автора`() =
        runTest {
            signUpWithHall()
            val colleague = insertColleague("Коллега", isActive = true, allBranches = true)
            val assigned = insertColleague("Сотрудник филиала", isActive = true, allBranches = false)
            TestPostgres.db
                .sql("INSERT INTO employee_branches (employee_id, branch_id) VALUES (:employeeId, :branchId)")
                .bind("employeeId", assigned)
                .bind("branchId", user.branchId)
                .execute()
            insertColleague("Другой филиал", isActive = true, allBranches = false)
            insertColleague("Неактивный", isActive = false, allBranches = true)
            val groupId = GroupId.new()
            testApplication {
                application { context(di) { configureServer() } }
                createGroup(groupId)

                val response = postJson("/api/groups/set-schedule", """{"groupId":"$groupId","slots":${slotsJson()}}""")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(listOf(colleague, assigned).sortedBy { it.value }, notificationRecipients())
                val content =
                    TestPostgres.db
                        .sql("SELECT content FROM notifications")
                        .list { Json.decodeFromString<NotificationContent>(it.asString("content")) }
                        .single()
                assertEquals(GroupScheduleChanged(groupId, "Йога", today, "John"), content)
            }
        }
}
