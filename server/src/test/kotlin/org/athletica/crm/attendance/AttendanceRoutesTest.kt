package org.athletica.crm.attendance

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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.Di
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.api.schemas.attendance.AttendanceLabelListResponse
import org.athletica.crm.api.schemas.attendance.ClientAttendanceResponse
import org.athletica.crm.api.schemas.attendance.GroupAttendanceResponse
import org.athletica.crm.api.schemas.attendance.SessionJournalResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.configureServer
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.sessions.SessionStatus
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

/**
 * Тесты маршрутов журнала посещаемости: справочник меток, журнал занятия,
 * проведение занятия, сводки и коды ошибок отказов.
 */
class AttendanceRoutesTest {
    private lateinit var di: Di
    private lateinit var user: User
    private lateinit var token: String
    private val json = Json { ignoreUnknownKeys = true }
    private val yesterday = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toKotlinLocalDate()

    /** Группа занятий, созданных [sessionWith]. */
    private val groupIdOf = mutableMapOf<SessionId, GroupId>()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        di = testDi()
    }

    /** Регистрирует организацию; её владелец — автор запросов. */
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

    /** Группа «Йога» с занятием вчера и постоянными участниками [names]; возвращает занятие и клиентов. */
    private suspend fun sessionWith(vararg names: String): Pair<SessionId, List<ClientId>> {
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
        val clients = names.map { insertClient(it) }
        clients.forEach { clientId ->
            TestPostgres.db
                .sql("INSERT INTO enrollments (group_id, client_id, enrolled_at) VALUES (:g, :c, now() - interval '30 days')")
                .bind("g", groupId)
                .bind("c", clientId)
                .execute()
        }
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
            .bind("date", yesterday)
            .bind("hallId", hallId)
            .execute()
        groupIdOf[sessionId] = groupId
        return sessionId to clients
    }

    private suspend fun insertClient(name: String): ClientId {
        val id = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", id)
            .bind("orgId", user.orgId)
            .bind("name", name)
            .execute()
        return id
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String): HttpResponse =
        client.post("/api/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.get(path: String): HttpResponse =
        client.get("/api/$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    private suspend fun HttpResponse.journal(): SessionJournalResponse {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        return json.decodeFromString(bodyAsText())
    }

    private suspend fun HttpResponse.errorCode(): String {
        assertEquals(HttpStatusCode.BadRequest, status, bodyAsText())
        return json.decodeFromString<ErrorResponse>(bodyAsText()).code
    }

    private suspend fun ApplicationTestBuilder.labels(includeArchived: Boolean = false): AttendanceLabelListResponse {
        val response = get("attendance-labels/list?includeArchived=$includeArchived")
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.labelId(name: String): AttendanceLabelId = labels().labels.single { it.name == name }.id

    private fun mark(
        sessionId: SessionId,
        clientId: ClientId,
        presence: AttendancePresence?,
        vararg labels: String,
    ): String =
        """{"sessionId":"$sessionId","clientId":"$clientId","presence":${presence?.let { "\"$it\"" }},""" +
            """"labelIds":[${labels.joinToString(",") { "\"$it\"" }}]}"""

    @Test
    fun `справочник меток создаётся, правится и архивируется`() =
        runTest {
            signUpOwner()
            testApplication {
                application { context(di) { configureServer() } }
                assertEquals(5, labels().labels.size)

                val id = AttendanceLabelId.new()
                val created = post("attendance-labels/create", """{"id":"$id","name":"Соревнования","scope":"ABSENT"}""")
                val duplicate = post("attendance-labels/create", """{"id":"${AttendanceLabelId.new()}","name":"соревнования","scope":"ABSENT"}""")
                val updated = post("attendance-labels/update", """{"id":"$id","name":"Турнир","position":0}""")
                val archived = post("attendance-labels/archive", """{"id":"$id"}""")

                assertEquals(HttpStatusCode.OK, created.status)
                assertEquals("ATTENDANCE_LABEL_ALREADY_EXISTS", duplicate.errorCode())
                assertEquals(HttpStatusCode.OK, updated.status)
                assertEquals(HttpStatusCode.OK, archived.status)
                assertTrue(labels().labels.none { it.id == id })
                val all = labels(includeArchived = true).labels
                assertEquals(id, all.first().id)
                assertEquals("Турнир", all.first().name)
                assertTrue(all.first().isArchived)
                assertEquals(AttendanceLabelScope.ABSENT, all.first().scope)

                assertEquals(HttpStatusCode.OK, post("attendance-labels/restore", """{"id":"$id"}""").status)
                assertTrue(labels().labels.any { it.id == id })
            }
        }

    @Test
    fun `журнал занятия читается и отмечается, отказы возвращают коды ошибок`() =
        runTest {
            signUpOwner()
            val (session, clients) = sessionWith("Аня", "Борис")
            val stranger = insertClient("Чужой")
            testApplication {
                application { context(di) { configureServer() } }
                val late = labelId("Опоздал")
                val sick = labelId("Болеет")

                val initial = get("sessions/journal?sessionId=$session").journal()
                val present = post("sessions/journal/mark", mark(session, clients[0], AttendancePresence.PRESENT, "$late")).journal()
                val noLabel = post("sessions/journal/mark", mark(session, clients[1], AttendancePresence.ABSENT))
                val wrongLabel = post("sessions/journal/mark", mark(session, clients[1], AttendancePresence.ABSENT, "$late"))
                val notInRoster = post("sessions/journal/mark", mark(session, stranger, AttendancePresence.PRESENT))
                val reloaded = get("sessions/journal?sessionId=$session").journal()

                assertEquals("Йога", initial.group.name)
                assertEquals("Большой зал", initial.hall.name)
                assertEquals(listOf("Аня", "Борис"), initial.participants.map { it.name })
                assertTrue(initial.participants.all { it.presence == null })
                assertEquals(AttendancePresence.PRESENT, present.participants.first().presence)
                assertEquals(listOf("Опоздал"), present.participants.first().labels.map { it.name })
                assertEquals("ATTENDANCE_ABSENCE_REQUIRES_LABEL", noLabel.errorCode())
                assertEquals("ATTENDANCE_LABEL_NOT_APPLICABLE", wrongLabel.errorCode())
                assertEquals("ATTENDANCE_PARTICIPANT_NOT_FOUND", notInRoster.errorCode())
                assertEquals(listOf(AttendancePresence.PRESENT, null), reloaded.participants.map { it.presence })

                val complete = post("sessions/complete", """{"sessionId":"$session"}""")
                assertEquals("SESSION_JOURNAL_INCOMPLETE", complete.errorCode())

                val absent = post("sessions/journal/mark", mark(session, clients[1], AttendancePresence.ABSENT, "$sick")).journal()
                assertEquals(AttendancePresence.ABSENT, absent.participants[1].presence)
            }
        }

    @Test
    fun `разовый участник добавляется и удаляется, постоянного удалить нельзя`() =
        runTest {
            signUpOwner()
            val (session, clients) = sessionWith("Аня")
            val guest = insertClient("Гость")
            testApplication {
                application { context(di) { configureServer() } }
                val body = """{"sessionId":"$session","clientId":"$guest"}"""

                val added = post("sessions/journal/add-participant", body).journal()
                val again = post("sessions/journal/add-participant", body)
                val removeRegular = post("sessions/journal/remove-participant", """{"sessionId":"$session","clientId":"${clients[0]}"}""")
                val removed = post("sessions/journal/remove-participant", body).journal()

                assertEquals(ParticipationKind.ONE_TIME, added.participants.single { it.clientId == guest }.kind)
                assertEquals("ATTENDANCE_PARTICIPANT_ALREADY_EXISTS", again.errorCode())
                assertEquals("ATTENDANCE_CANNOT_REMOVE_REGULAR", removeRegular.errorCode())
                assertEquals(listOf(clients[0]), removed.participants.map { it.clientId })
            }
        }

    @Test
    fun `массовая отметка, проведение и сводки посещаемости`() =
        runTest {
            signUpOwner()
            val (session, clients) = sessionWith("Аня", "Борис", "Вера")
            testApplication {
                application { context(di) { configureServer() } }
                val truant = labelId("Прогул")
                post("sessions/journal/mark", mark(session, clients[0], AttendancePresence.PRESENT)).journal()

                val bulk = post("sessions/journal/mark-remaining-absent", """{"sessionId":"$session","labelId":"$truant"}""").journal()
                val completed = post("sessions/complete", """{"sessionId":"$session"}""").journal()
                val cancelCompleted = post("sessions/$session/cancel", "{}")
                val unmark = post("sessions/journal/mark", mark(session, clients[1], null))
                val group =
                    get("attendance/group?groupId=${groupIdOf.getValue(session)}&from=${yesterday.minusDays(7)}&to=$yesterday")
                val clientReport = get("attendance/client?clientId=${clients[0]}&from=$yesterday&to=$yesterday")
                val badPeriod = get("attendance/client?clientId=${clients[0]}&from=$yesterday&to=${yesterday.minusDays(1)}")

                assertEquals(
                    listOf(AttendancePresence.PRESENT, AttendancePresence.ABSENT, AttendancePresence.ABSENT),
                    bulk.participants.map { it.presence },
                )
                assertEquals(SessionStatus.COMPLETED, completed.status)
                assertEquals(HttpStatusCode.BadRequest, cancelCompleted.status)
                assertEquals("ATTENDANCE_CANNOT_UNMARK_COMPLETED", unmark.errorCode())
                assertEquals(HttpStatusCode.OK, group.status)
                val summary = json.decodeFromString<GroupAttendanceResponse>(group.bodyAsText())
                assertEquals(listOf(session), summary.sessions.map { it.id })
                assertEquals(listOf(1, 0, 0), summary.participants.map { it.presentCount })
                val items = json.decodeFromString<ClientAttendanceResponse>(clientReport.bodyAsText()).items
                assertEquals(listOf(session), items.map { it.sessionId })
                assertEquals("INVALID_ATTENDANCE_PERIOD", badPeriod.errorCode())
            }
        }

    private fun LocalDate.minusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() - days)
}
