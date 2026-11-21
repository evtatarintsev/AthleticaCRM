package org.athletica.crm.schedule

import arrow.core.Either
import arrow.core.raise.context.either
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
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.configureServer
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.notifications.GroupScheduleChanged
import org.athletica.crm.domain.notifications.NotificationContent
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.testDi
import org.athletica.crm.testJwtConfig
import org.athletica.crm.testName
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
    private lateinit var user: AuthenticatedUser
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
        orThrow {
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
            user = assertIs<Either.Right<AuthenticatedUser>>(result).value
            token = testJwtConfig.makeAccessToken(user)
            hallId = HallId.new()
            TestPostgres.db
                .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Зал')")
                .bind("id", hallId)
                .bind("orgId", user.orgId)
                .bind("branchId", user.branchId)
                .execute()
        }
    }

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String) =
        client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.AcceptLanguage, "ru")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.createGroup(id: GroupId) = postJson("/api/groups/create", """{"id":"$id","name":"Йога"}""")

    private fun slotsJson(endAt: String = "11:00") = """[{"dayOfWeek":"MONDAY","startAt":"10:00","endAt":"$endAt","hallId":"$hallId"}]"""

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
    fun `некорректное время возвращает 400 и сохраняет текущее и отложенное расписание`() =
        runTest {
            signUpWithHall()
            val groupId = GroupId.new()
            testApplication {
                application { context(di) { configureServer() } }
                assertEquals(HttpStatusCode.OK, createGroup(groupId).status)
                val initial = postJson("/api/groups/set-schedule", """{"groupId":"$groupId","slots":${slotsJson()}}""")
                assertEquals(HttpStatusCode.OK, initial.status)
                val effectiveFrom = today.plusDays(30)
                val planned =
                    postJson(
                        "/api/groups/set-schedule",
                        """{"groupId":"$groupId","effectiveFrom":"$effectiveFrom","slots":${slotsJson("12:00")}}""",
                    )
                assertEquals(HttpStatusCode.OK, planned.status)
                val before = json.decodeFromString<GroupDetailResponse>(planned.bodyAsText())
                assertEquals(effectiveFrom, before.scheduleChangeAt)

                listOf("09:00", "10:00").forEach { endAt ->
                    val response = postJson("/api/groups/set-schedule", """{"groupId":"$groupId","slots":${slotsJson(endAt)}}""")

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                    val error = json.decodeFromString<ErrorResponse>(response.bodyAsText())
                    assertEquals("INVALID_SCHEDULE_TIME", error.code)
                    assertEquals("Время окончания должно быть позже времени начала: 10:00 – $endAt", error.message)
                    val detail =
                        client.get("/api/groups/detail?id=$groupId") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                    assertEquals(HttpStatusCode.OK, detail.status)
                    assertEquals(before, json.decodeFromString<GroupDetailResponse>(detail.bodyAsText()))
                }
            }
        }

    @Test
    fun `сразу после установки расписания занятия существуют`() =
        orThrow {
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
        }

    /**
     * Добавляет в организацию сотрудника [name] с выданным логином и доступом [allBranches];
     * [blocked] — признак блокировки, [hasAccess] — выдан ли логин.
     */
    private suspend fun insertColleague(
        name: String,
        allBranches: Boolean,
        blocked: Boolean = false,
        hasAccess: Boolean = true,
    ): EmployeeId =
        orThrow {
            val id = EmployeeId.new()
            val userId = if (hasAccess) UserId.new() else null
            if (userId != null) {
                TestPostgres.db
                    .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, 'x')")
                    .bind("id", userId)
                    .bind("login", "$userId@example.com")
                    .execute()
            }
            TestPostgres.db
                .sql(
                    """
                    INSERT INTO employees (id, org_id, user_id, name, is_owner, all_branches_access, blocked_at)
                    VALUES (:id, :orgId, :userId, :name, false, :allBranches, CASE WHEN :blocked THEN now() END)
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", user.orgId)
                .bind("userId", userId)
                .bind("name", name)
                .bind("allBranches", allBranches)
                .bind("blocked", blocked)
                .execute()
            return id
        }

    /** Получатели уведомлений в порядке возрастания идентификатора. */
    private suspend fun notificationRecipients(): List<EmployeeId> =
        orThrow {
            TestPostgres.db
                .sql("SELECT employee_id FROM notification_recipients ORDER BY employee_id")
                .list { it.asUuid("employee_id").toEmployeeId() }
        }

    @Test
    fun `изменение расписания уведомляет коллег с доступом к филиалу группы, кроме автора`() =
        orThrow {
            runTest {
                signUpWithHall()
                val colleague = insertColleague("Коллега", allBranches = true)
                val assigned = insertColleague("Сотрудник филиала", allBranches = false)
                TestPostgres.db
                    .sql("INSERT INTO employee_branches (employee_id, branch_id) VALUES (:employeeId, :branchId)")
                    .bind("employeeId", assigned)
                    .bind("branchId", user.branchId)
                    .execute()
                insertColleague("Другой филиал", allBranches = false)
                insertColleague("Заблокированный", allBranches = true, blocked = true)
                insertColleague("Без доступа", allBranches = true, hasAccess = false)
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
}
