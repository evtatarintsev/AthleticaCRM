package org.athletica.crm.read.groups

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asUuid
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Тесты read-проекции карточки группы: расписание, дисциплины, тренеры и участники. */
class DbGroupDetailViewTest {
    private val view = DbGroupDetailView()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private suspend fun insertOrg(name: String = "Test Org"): Uuid {
        val orgId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId).bind("name", name)
            .execute()
        return orgId
    }

    private suspend fun ensureBranch(orgId: Uuid): Uuid {
        val existing =
            TestPostgres.db
                .sql("SELECT id FROM branches WHERE org_id = :orgId LIMIT 1")
                .bind("orgId", orgId)
                .firstOrNull { it.asUuid("id") }
        if (existing != null) return existing
        val branchId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", branchId).bind("orgId", orgId).bind("name", "Основной")
            .execute()
        return branchId
    }

    private suspend fun insertGroup(orgId: Uuid, name: String): GroupId {
        val groupId = GroupId.new()
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, name, branch_id) VALUES (:id, :orgId, :name, :branchId)")
            .bind("id", groupId).bind("orgId", orgId).bind("name", name)
            .bind("branchId", ensureBranch(orgId))
            .execute()
        return groupId
    }

    private suspend fun insertClient(orgId: Uuid, name: String): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId).bind("orgId", orgId).bind("name", name)
            .execute()
        return clientId
    }

    private suspend fun enroll(clientId: ClientId, groupId: GroupId, left: Boolean = false) {
        TestPostgres.db
            .sql(
                "INSERT INTO enrollments (client_id, group_id, left_at) VALUES (:c, :g, ${if (left) "NOW()" else "NULL"})",
            )
            .bind("c", clientId).bind("g", groupId)
            .execute()
    }

    private suspend fun insertDiscipline(orgId: Uuid, name: String): DisciplineId {
        val id = DisciplineId.new()
        TestPostgres.db
            .sql("INSERT INTO disciplines (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", id).bind("orgId", orgId).bind("name", name)
            .execute()
        return id
    }

    private suspend fun linkDiscipline(groupId: GroupId, disciplineId: DisciplineId) {
        TestPostgres.db
            .sql("INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:g, :d)")
            .bind("g", groupId).bind("d", disciplineId)
            .execute()
    }

    private suspend fun insertEmployee(orgId: Uuid, name: String): EmployeeId {
        val id = EmployeeId.new()
        TestPostgres.db
            .sql(
                """
                INSERT INTO employees (id, org_id, name, is_active, all_branches_access, joined_at)
                VALUES (:id, :orgId, :name, true, true, NOW())
                """.trimIndent(),
            )
            .bind("id", id).bind("orgId", orgId).bind("name", name)
            .execute()
        return id
    }

    private suspend fun linkEmployee(groupId: GroupId, employeeId: EmployeeId) {
        TestPostgres.db
            .sql("INSERT INTO group_employees (group_id, employee_id) VALUES (:g, :e)")
            .bind("g", groupId).bind("e", employeeId)
            .execute()
    }

    private suspend fun insertHall(orgId: Uuid, name: String): HallId {
        val id = HallId.new()
        TestPostgres.db
            .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
            .bind("id", id).bind("orgId", orgId).bind("branchId", ensureBranch(orgId)).bind("name", name)
            .execute()
        return id
    }

    private suspend fun insertSlot(orgId: Uuid, groupId: GroupId, hallId: HallId) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO schedule_slots (org_id, group_id, day_of_week, start_time, end_time, hall_id, validity)
                VALUES (:orgId, :groupId, 'WEDNESDAY'::day_of_week, '18:00'::time, '19:00'::time, :hallId,
                        daterange(CURRENT_DATE, NULL))
                """.trimIndent(),
            )
            .bind("orgId", orgId).bind("groupId", groupId).bind("hallId", hallId)
            .execute()
    }

    private suspend fun detail(orgId: Uuid, id: GroupId): Either<DomainError, GroupDetailResponse> {
        val context =
            EmployeeRequestContext(
                lang = Lang.RU,
                userId = UserId.new(),
                orgId = OrgId(orgId),
                branchId = ensureBranch(orgId).toBranchId(),
                employeeId = EmployeeId.new(),
                username = "test@example.com",
                clientIp = null,
                currency = Currency.RUB,
                permission = EmployeePermission(),
            )
        return either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(context, this, raise) { view.byId(id) }
            }
        }
    }

    private suspend fun succeeding(orgId: Uuid, id: GroupId): GroupDetailResponse = assertIs<Either.Right<GroupDetailResponse>>(detail(orgId, id)).value

    @Test
    fun `собирает карточку со всеми связями`() =
        runTest {
            val orgId = insertOrg()
            val groupId = insertGroup(orgId, "Йога")
            val hallId = insertHall(orgId, "Малый зал")
            val yoga = insertDiscipline(orgId, "Йога")
            val anna = insertEmployee(orgId, "Анна")
            val client = insertClient(orgId, "Пётр Клиентов")
            linkDiscipline(groupId, yoga)
            linkEmployee(groupId, anna)
            enroll(client, groupId)
            insertSlot(orgId, groupId, hallId)

            val result = succeeding(orgId, groupId)

            assertEquals("Йога", result.name)
            assertEquals(listOf("Йога"), result.disciplines.map { it.name })
            assertEquals(listOf("Анна"), result.employees.map { it.name })
            assertEquals(listOf("Пётр Клиентов"), result.clients.map { it.name })

            val slot = result.schedule.single()
            assertEquals(DayOfWeek.WEDNESDAY, slot.dayOfWeek)
            assertEquals(LocalTime(18, 0), slot.startAt)
            assertEquals("Малый зал", slot.hallName)
        }

    @Test
    fun `отчисленные участники не попадают в карточку`() =
        runTest {
            val orgId = insertOrg()
            val groupId = insertGroup(orgId, "Бокс")
            enroll(insertClient(orgId, "Активный"), groupId)
            enroll(insertClient(orgId, "Ушедший"), groupId, left = true)

            assertEquals(listOf("Активный"), succeeding(orgId, groupId).clients.map { it.name })
        }

    @Test
    fun `группа без связей отдаёт пустые коллекции`() =
        runTest {
            val orgId = insertOrg()
            val groupId = insertGroup(orgId, "Пустая")

            val result = succeeding(orgId, groupId)

            assertTrue(result.schedule.isEmpty())
            assertTrue(result.disciplines.isEmpty())
            assertTrue(result.employees.isEmpty())
            assertTrue(result.clients.isEmpty())
        }

    @Test
    fun `группа чужой организации не находится`() =
        runTest {
            val orgA = insertOrg("A")
            val orgB = insertOrg("B")
            val foreign = insertGroup(orgB, "Чужая")

            assertIs<Either.Left<DomainError>>(detail(orgA, foreign))
        }

    @Test
    fun `карточка показывает действующую версию слота и дату запланированного изменения`() =
        runTest {
            val orgId = insertOrg()
            val groupId = insertGroup(orgId, "Йога")
            val oldHall = insertHall(orgId, "Старый зал")
            val newHall = insertHall(orgId, "Новый зал")
            val today = java.time.LocalDate.now().toKotlinLocalDate()
            val changeAt = today.plusDays(30)
            insertSlotWithValidity(orgId, groupId, oldHall, today, changeAt)
            insertSlotWithValidity(orgId, groupId, newHall, changeAt, null)

            val result = succeeding(orgId, groupId)

            assertEquals(oldHall, result.schedule.single().hallId)
            assertEquals(changeAt, result.scheduleChangeAt)
        }

    @Test
    fun `группа без запланированных изменений отдаёт пустую дату изменения`() =
        runTest {
            val orgId = insertOrg()
            val groupId = insertGroup(orgId, "Йога")
            insertSlot(orgId, groupId, insertHall(orgId, "Зал"))

            assertNull(succeeding(orgId, groupId).scheduleChangeAt)
        }

    /** Вставляет версию слота «среда 18:00–19:00» с заданным периодом действия. */
    private suspend fun insertSlotWithValidity(
        orgId: Uuid,
        groupId: GroupId,
        hallId: HallId,
        from: kotlinx.datetime.LocalDate,
        to: kotlinx.datetime.LocalDate?,
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO schedule_slots (org_id, group_id, day_of_week, start_time, end_time, hall_id, validity)
                VALUES (:orgId, :groupId, 'WEDNESDAY'::day_of_week, '18:00'::time, '19:00'::time, :hallId,
                        daterange(:from::date, :to::date))
                """.trimIndent(),
            )
            .bind("orgId", orgId).bind("groupId", groupId).bind("hallId", hallId)
            .bind("from", from).bind("to", to)
            .execute()
    }
}

/** Прибавляет [days] дней к дате. */
private fun kotlinx.datetime.LocalDate.plusDays(days: Int): kotlinx.datetime.LocalDate = kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() + days)
