package org.athletica.crm.schedule

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.SlotId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import kotlin.uuid.Uuid

/**
 * Набор помощников для DB-тестов расписания и занятий: создаёт минимальный
 * граф сущностей (организация, филиал, зал, группа, слот, занятие) прямым SQL,
 * минуя доменные репозитории.
 */
class ScheduleFixture {
    /** Организация, в контексте которой выполняются запросы теста. */
    var orgId: Uuid = Uuid.NIL
        private set

    /** Филиал организации [orgId]. */
    var branchId: BranchId = BranchId.notSelected()
        private set

    /** Создаёт организацию с филиалом и запоминает их как текущие. */
    suspend fun setUp(name: String = "Test Org") {
        orgId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", name)
            .execute()
        val branch = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", branch)
            .bind("orgId", orgId)
            .bind("name", "Основной")
            .execute()
        branchId = branch.toBranchId()
    }

    /** Создаёт зал [name] в текущем филиале. */
    suspend fun insertHall(name: String = "Зал ${Uuid.generateV7()}"): HallId {
        val id = HallId.new()
        TestPostgres.db
            .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("branchId", branchId)
            .bind("name", name)
            .execute()
        return id
    }

    /** Создаёт группу [name] в текущем филиале. */
    suspend fun insertGroup(name: String = "Группа ${Uuid.generateV7()}"): GroupId {
        val id = GroupId.new()
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("branchId", branchId)
            .bind("name", name)
            .execute()
        return id
    }

    /** Создаёт сотрудника [name] с доступом ко всем филиалам организации. */
    suspend fun insertEmployee(name: String = "Тренер ${Uuid.generateV7()}"): EmployeeId {
        val id = EmployeeId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO employees (id, org_id, name, is_active, all_branches_access, joined_at)
                VALUES (:id, :orgId, :name, true, true, NOW())
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return id
    }

    /** Привязывает сотрудника [employeeId] к группе [groupId] как её тренера. */
    suspend fun linkGroupEmployee(groupId: GroupId, employeeId: EmployeeId) {
        TestPostgres.db
            .sql("INSERT INTO group_employees (group_id, employee_id) VALUES (:g, :e)")
            .bind("g", groupId)
            .bind("e", employeeId)
            .execute()
    }

    /** Вставляет версию слота с периодом действия [from]..[to] (верхняя граница исключительна). */
    suspend fun insertSlot(
        groupId: GroupId,
        hallId: HallId,
        dayOfWeek: DayOfWeek,
        startAt: LocalTime,
        endAt: LocalTime,
        from: LocalDate,
        to: LocalDate? = null,
    ): SlotId {
        val id = SlotId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO schedule_slots (id, org_id, group_id, day_of_week, start_time, end_time, hall_id, validity)
                VALUES (:id, :orgId, :groupId, :dayOfWeek::day_of_week, :startAt::time, :endAt::time, :hallId,
                        daterange(:from::date, :to::date))
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("groupId", groupId)
            .bind("dayOfWeek", dayOfWeek.name)
            .bind("startAt", startAt.toString())
            .bind("endAt", endAt.toString())
            .bind("hallId", hallId)
            .bind("from", from)
            .bind("to", to)
            .execute()
        return id
    }

    /** Вставляет занятие напрямую, минуя сверку; [originSlotId] = null означает ручное занятие. */
    suspend fun insertSession(
        groupId: GroupId,
        hallId: HallId,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        originSlotId: SlotId? = null,
        originDate: LocalDate? = originSlotId?.let { date },
        status: String = "scheduled",
        isRescheduled: Boolean = false,
        notes: String? = null,
        isEmployeeAssignmentOverridden: Boolean = false,
    ): SessionId {
        val id = SessionId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO sessions (id, org_id, group_id, date, start_time, end_time, hall_id, status,
                                      is_rescheduled, notes, is_employee_assignment_overridden,
                                      origin_slot_id, origin_date)
                VALUES (:id, :orgId, :groupId, :date, :startTime::time, :endTime::time, :hallId,
                        :status::session_status, :isRescheduled, :notes, :overridden, :slotId, :originDate)
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("groupId", groupId)
            .bind("date", date)
            .bind("startTime", startTime.toString())
            .bind("endTime", endTime.toString())
            .bind("hallId", hallId)
            .bind("status", status)
            .bind("isRescheduled", isRescheduled)
            .bind("notes", notes)
            .bind("overridden", isEmployeeAssignmentOverridden)
            .bind("slotId", originSlotId)
            .bind("originDate", originDate)
            .execute()
        return id
    }

    /** Привязывает сотрудника [employeeId] к занятию [sessionId]. */
    suspend fun linkSessionEmployee(sessionId: SessionId, employeeId: EmployeeId) {
        TestPostgres.db
            .sql("INSERT INTO session_employees (session_id, employee_id) VALUES (:s, :e)")
            .bind("s", sessionId)
            .bind("e", employeeId)
            .execute()
    }

    /** Контекст сотрудника текущей организации и филиала. */
    fun ctx(): EmployeeRequestContext =
        EmployeeRequestContext(
            lang = Lang.RU,
            userId = UserId.new(),
            orgId = OrgId(orgId),
            branchId = branchId,
            employeeId = EmployeeId.new(),
            username = "test@example.com",
            clientIp = null,
            currency = Currency.RUB,
            permission = EmployeePermission(),
        )

    /** Выполняет [block] в транзакции с контекстом сотрудника; ошибка домена роняет тест. */
    suspend fun <T> inContext(block: suspend context(EmployeeRequestContext, Transaction, Raise<DomainError>) () -> T): T = runInContext(block).getOrElse { error(it.toString()) }

    /** Выполняет [block] в транзакции с контекстом сотрудника, возвращая результат вместе с ошибкой домена. */
    suspend fun <T> runInContext(
        block: suspend context(EmployeeRequestContext, Transaction, Raise<DomainError>) () -> T,
    ): arrow.core.Either<DomainError, T> {
        val context = ctx()
        return either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(context, this, raise) { block() }
            }
        }
    }

    /** Количество занятий группы [groupId]; без группы — по всей организации. */
    suspend fun sessionCount(groupId: GroupId? = null): Long =
        TestPostgres.db
            .sql(
                "SELECT COUNT(*) AS cnt FROM sessions WHERE org_id = :orgId" +
                    if (groupId != null) " AND group_id = :groupId" else "",
            )
            .bind("orgId", orgId)
            .let { q -> if (groupId != null) q.bind("groupId", groupId) else q }
            .firstOrNull { it.asLong("cnt") } ?: 0L
}
