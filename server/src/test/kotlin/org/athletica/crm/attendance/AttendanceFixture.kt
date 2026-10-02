package org.athletica.crm.attendance

import arrow.core.Either
import arrow.core.raise.Raise
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.attendance.AttendanceLabelScope
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.attendance.DbAttendanceLabels
import org.athletica.crm.domain.attendance.DbSessionJournals
import org.athletica.crm.domain.attendance.SessionJournal
import org.athletica.crm.domain.events.DomainEvent
import org.athletica.crm.domain.events.DomainEventBus
import org.athletica.crm.domain.sessions.DbSessions
import org.athletica.crm.schedule.ScheduleFixture
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/**
 * Помощники DB-тестов журнала посещаемости: организация с автором запросов, залом и группой,
 * клиенты, записи в группу, метки и занятия — прямым SQL, а команды журнала — через доменные
 * репозитории с настоящей шиной событий.
 */
class AttendanceFixture {
    /** Базовые помощники расписания: организация, зал, группа, занятие. */
    val schedule = ScheduleFixture()

    /** Сегодняшняя дата по UTC — часовому поясу тестовой организации. */
    val today: LocalDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toKotlinLocalDate()

    /** Вчерашняя дата: занятие на неё уже началось. */
    val yesterday: LocalDate = today.plusDays(-1)

    /** Зал тестовой группы. */
    var hallId: HallId = HallId.new()
        private set

    /** Тестовая группа. */
    var groupId: GroupId = GroupId.new()
        private set

    /** Метки журнала: справочник организации. */
    val labels = DbAttendanceLabels()

    /** Занятия с публикацией событий в outbox. */
    val sessions = DbSessions(DomainEventBus())

    /** Журналы занятий с публикацией событий в outbox. */
    val journals = DbSessionJournals(labels, DomainEventBus())

    /** Создаёт организацию с автором запросов, залом и группой. */
    suspend fun setUp() {
        schedule.setUp()
        schedule.insertActor()
        hallId = schedule.insertHall()
        groupId = schedule.insertGroup()
    }

    /** Создаёт клиента [name] в организации. */
    suspend fun insertClient(name: String): ClientId {
        val id = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", id)
            .bind("orgId", schedule.orgId)
            .bind("name", name)
            .execute()
        return id
    }

    /** Записывает клиента [clientId] в группу [group] с даты [from] до даты выхода [leftAt]. */
    suspend fun enroll(
        clientId: ClientId,
        from: LocalDate,
        leftAt: LocalDate? = null,
        group: GroupId = groupId,
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO enrollments (group_id, client_id, enrolled_at, left_at)
                VALUES (:groupId, :clientId, :from::date::timestamptz, :leftAt::date::timestamptz)
                """.trimIndent(),
            )
            .bind("groupId", group)
            .bind("clientId", clientId)
            .bind("from", from)
            .bind("leftAt", leftAt)
            .execute()
    }

    /** Создаёт метку [name] с применимостью [scope]; архивную при [archived]. */
    suspend fun insertLabel(
        name: String,
        scope: AttendanceLabelScope,
        archived: Boolean = false,
    ): AttendanceLabelId {
        val id = AttendanceLabelId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO attendance_labels (id, org_id, name, scope, archived_at)
                VALUES (:id, :orgId, :name, :scope::attendance_label_scope, CASE WHEN :archived THEN now() END)
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", schedule.orgId)
            .bind("name", name)
            .bind("scope", scope.name.lowercase())
            .bind("archived", archived)
            .execute()
        return id
    }

    /** Создаёт занятие группы на дату [date] в 10:00–11:00 со статусом [status]. */
    suspend fun insertSession(
        date: LocalDate = yesterday,
        status: String = "scheduled",
        originSlotId: SlotId? = null,
        group: GroupId = groupId,
    ): SessionId = schedule.insertSession(group, hallId, date, LocalTime(10, 0), LocalTime(11, 0), originSlotId = originSlotId, status = status)

    /** Журнал занятия [sessionId], загруженный в контексте автора запросов. */
    suspend fun journal(sessionId: SessionId): SessionJournal = inContext { journals.byId(sessionId) }

    /** Выполняет [block] в транзакции; ошибка домена роняет тест. */
    suspend fun <T> inContext(block: suspend context(EmployeeRequestContext, Transaction, Raise<DomainError>) () -> T): T = schedule.inContext(block)

    /** Выполняет [block] в транзакции, возвращая ошибку домена значением. */
    suspend fun <T> runInContext(block: suspend context(EmployeeRequestContext, Transaction, Raise<DomainError>) () -> T): Either<DomainError, T> = schedule.runInContext(block)

    /** Код ошибки домена из [result]; успех роняет тест. */
    fun <T> errorCode(result: Either<DomainError, T>): String =
        when (result) {
            is Either.Left -> result.value.code
            is Either.Right -> error("ожидалась ошибка, получено ${result.value}")
        }

    /** События организации в порядке публикации, десериализованные как [DomainEvent]. */
    suspend fun events(): List<DomainEvent> =
        TestPostgres.db
            .sql("SELECT payload::text AS payload FROM domain_events WHERE org_id = :orgId ORDER BY created_at, id")
            .bind("orgId", schedule.orgId)
            .list { Json.decodeFromString<DomainEvent>(it.asString("payload")) }
}

/** Дата, сдвинутая на [days] дней. */
fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
