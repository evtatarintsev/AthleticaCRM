package org.athletica.crm

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Организация с филиалом и залом для DB-тестов незакрытых журналов: группы и занятия
 * вставляются прямым SQL, «сейчас» задаётся через [clock].
 */
class SessionsFixture(
    /** Часовой пояс организации. */
    val timezone: String = "UTC",
) {
    /** Организация. */
    val orgId: OrgId = OrgId.new()

    /** Основной филиал организации. */
    val branchId: BranchId = BranchId.new()

    /** Зал основного филиала. */
    val hallId: HallId = HallId.new()

    /** Текущий момент для [clock]. */
    var now: Instant = Instant.parse("2026-10-17T12:00:00Z")

    /** Часы, возвращающие [now]. */
    val clock: Clock =
        object : Clock {
            override fun now(): Instant = this@SessionsFixture.now
        }

    /** Создаёт организацию, филиал и зал. */
    suspend fun setUp() {
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name, timezone) VALUES (:id, 'Org', :tz)")
            .bind("id", orgId)
            .bind("tz", timezone)
            .execute()
        insertBranch(branchId)
        TestPostgres.db
            .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Зал')")
            .bind("id", hallId)
            .bind("orgId", orgId)
            .bind("branchId", branchId)
            .execute()
    }

    /** Создаёт филиал [id] организации. */
    suspend fun insertBranch(id: BranchId) {
        TestPostgres.db
            .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", id)
            .bind("name", "Филиал $id")
            .bind("orgId", orgId)
            .execute()
    }

    /** Создаёт группу [name] в филиале [branch]. */
    suspend fun insertGroup(name: String = "Йога", branch: BranchId = branchId): GroupId {
        val id = GroupId.new()
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("branchId", branch)
            .bind("name", name)
            .execute()
        return id
    }

    /** Создаёт занятие группы [groupId] в статусе [status]. */
    suspend fun insertSession(
        groupId: GroupId,
        date: LocalDate,
        startTime: LocalTime = LocalTime(18, 0),
        endTime: LocalTime = LocalTime(19, 0),
        status: String = "scheduled",
    ): SessionId {
        val id = SessionId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO sessions (id, org_id, group_id, hall_id, date, start_time, end_time, status)
                VALUES (:id, :orgId, :groupId, :hallId, :date, :startTime::time, :endTime::time, :status::session_status)
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("groupId", groupId)
            .bind("hallId", hallId)
            .bind("date", date.toJavaLocalDate())
            .bind("startTime", startTime.toString())
            .bind("endTime", endTime.toString())
            .bind("status", status)
            .execute()
        return id
    }

    /** Переводит занятие [id] в статус [status]. */
    suspend fun setStatus(id: SessionId, status: String) {
        TestPostgres.db
            .sql("UPDATE sessions SET status = :status::session_status WHERE id = :id")
            .bind("id", id)
            .bind("status", status)
            .execute()
    }

    /** Сохраняет настройки контроля посещаемости организации. */
    suspend fun saveSettings(enabled: Boolean, delayMinutes: Int) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO attendance_control_settings (org_id, overdue_task_enabled, overdue_task_delay_minutes)
                VALUES (:orgId, :enabled, :delay)
                """.trimIndent(),
            )
            .bind("orgId", orgId)
            .bind("enabled", enabled)
            .bind("delay", delayMinutes)
            .execute()
    }
}
