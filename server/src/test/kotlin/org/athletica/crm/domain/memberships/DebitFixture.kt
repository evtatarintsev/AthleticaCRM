package org.athletica.crm.domain.memberships

import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.domain.attendance.DbParticipations
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.auth.DbUsers
import org.athletica.crm.domain.employees.DbEmployees
import org.athletica.crm.domain.employees.DbRoles
import org.athletica.crm.domain.notifications.DbNotifications
import org.athletica.crm.domain.org.DbOrgCalendar
import org.athletica.crm.domain.tasks.DbTasks
import org.athletica.crm.read.memberships.DbUnpaidSessionsView
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.asUuid
import org.athletica.crm.usecases.memberships.DebitReconciliation
import kotlin.uuid.Uuid

/**
 * Окружение списаний поверх [base]: зал, занятия, отметки журнала и сверка списаний
 * с теми же часами, что у [base].
 */
class DebitFixture(val base: MembershipFixture) {
    /** Зал занятий. */
    private val hallId: Uuid = Uuid.generateV7()

    /** Сверка списаний, как её вызывает воркер событий. */
    val reconciliation =
        DebitReconciliation(
            db = TestPostgres.db,
            memberships = base.memberships,
            participations = DbParticipations(),
            notifications = DbNotifications(),
            employees = DbEmployees(DbUsers(PasswordHasher()), DbRoles()),
            calendar = DbOrgCalendar(base.clock),
            unpaidSessions = DbUnpaidSessionsView(),
            tasks = DbTasks(),
            audit = PostgresAuditLog(),
        )

    /** Создаёт зал; вызывать после `base.setUp()`. */
    suspend fun setUp() {
        TestPostgres.db
            .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, 'Зал')")
            .bind("id", hallId)
            .bind("orgId", base.orgId)
            .bind("branchId", base.branchId)
            .execute()
    }

    /** Создаёт занятие группы [groupId] от [date] в статусе [status] (`scheduled`, `completed`). */
    suspend fun session(date: LocalDate, status: String = "completed", groupId: GroupId = base.groupId): SessionId {
        val id = SessionId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO sessions (id, org_id, group_id, hall_id, date, start_time, end_time, status)
                VALUES (:id, :orgId, :groupId, :hallId, :date, '10:00', '11:00', CAST(:status AS session_status))
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", base.orgId)
            .bind("groupId", groupId)
            .bind("hallId", hallId)
            .bind("date", date)
            .bind("status", status)
            .execute()
        return id
    }

    /** Ставит клиенту [clientId] в занятии [sessionId] отметку [presence] (`present`, `absent`, `null`). */
    suspend fun mark(sessionId: SessionId, clientId: ClientId, presence: String?) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO session_attendance (session_id, client_id, kind, presence)
                VALUES (:sessionId, :clientId, 'regular', CAST(:presence AS attendance_presence))
                ON CONFLICT (session_id, client_id) DO UPDATE SET presence = EXCLUDED.presence
                """.trimIndent(),
            )
            .bind("sessionId", sessionId)
            .bind("clientId", clientId)
            .bind("presence", presence)
            .execute()
    }

    /** Меняет статус занятия [sessionId]. */
    suspend fun status(sessionId: SessionId, status: String) {
        TestPostgres.db
            .sql("UPDATE sessions SET status = CAST(:status AS session_status) WHERE id = :id")
            .bind("status", status)
            .bind("id", sessionId)
            .execute()
    }

    /** Сверяет списание участия, как обработчик события. */
    suspend fun reconcile(sessionId: SessionId, clientId: ClientId) {
        reconciliation.reconcile(base.orgId, sessionId, clientId)
    }

    /** Абонемент действующего списания за участие; `null` — списания нет. */
    suspend fun debitedFrom(sessionId: SessionId, clientId: ClientId): MembershipId? =
        TestPostgres.db
            .sql("SELECT membership_id FROM membership_debits WHERE session_id = :s AND client_id = :c AND reversed_at IS NULL")
            .bind("s", sessionId)
            .bind("c", clientId)
            .firstOrNull { it.asUuid("membership_id").toMembershipId() }

    /** Число всех списаний (с возвращёнными) за участие. */
    suspend fun debitRows(sessionId: SessionId, clientId: ClientId): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) AS c FROM membership_debits WHERE session_id = :s AND client_id = :c")
            .bind("s", sessionId)
            .bind("c", clientId)
            .firstOrNull { it.get("c", java.lang.Long::class.java)!!.toLong() } ?: 0

    /** Устанавливает срок абонемента [id] напрямую в БД. */
    suspend fun setTerm(id: MembershipId, start: LocalDate, end: LocalDate) {
        TestPostgres.db
            .sql("UPDATE memberships SET start_date = :s, end_date = :e WHERE id = :id")
            .bind("s", start)
            .bind("e", end)
            .bind("id", id)
            .execute()
    }

    /** Число уведомлений выдавшему сотруднику. */
    suspend fun notifications(): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) AS c FROM notification_recipients WHERE employee_id = :e")
            .bind("e", base.employeeId)
            .firstOrNull { it.get("c", java.lang.Long::class.java)!!.toLong() } ?: 0
}
