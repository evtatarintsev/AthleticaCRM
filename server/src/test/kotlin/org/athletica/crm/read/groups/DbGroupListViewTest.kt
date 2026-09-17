package org.athletica.crm.read.groups

import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
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
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asUuid
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Тесты read-проекции списка групп: фильтры, изоляция по организации и сборка расписания. */
class DbGroupListViewTest {
    private val view = DbGroupListView()

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

    private suspend fun insertSlot(
        orgId: Uuid,
        groupId: GroupId,
        hallId: HallId,
        dayOfWeek: DayOfWeek,
        startAt: LocalTime,
        endAt: LocalTime,
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO schedule_slots (org_id, group_id, day_of_week, start_time, end_time, hall_id)
                VALUES (:orgId, :groupId, :dayOfWeek::day_of_week, :startAt::time, :endAt::time, :hallId)
                """.trimIndent(),
            )
            .bind("orgId", orgId).bind("groupId", groupId)
            .bind("dayOfWeek", dayOfWeek.name)
            .bind("startAt", startAt.toString()).bind("endAt", endAt.toString())
            .bind("hallId", hallId)
            .execute()
    }

    private suspend fun ctx(orgId: Uuid) =
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

    private suspend fun <T> inContext(orgId: Uuid, block: suspend context(EmployeeRequestContext, Transaction, Raise<DomainError>) () -> T): T {
        val context = ctx(orgId)
        return either {
            val raise: Raise<DomainError> = this
            TestPostgres.db.transaction {
                context(context, this, raise) { block() }
            }
        }.getOrElse { error(it.toString()) }
    }

    private suspend fun list(
        orgId: Uuid,
        name: String? = null,
        disciplineIds: List<DisciplineId> = emptyList(),
        employeeIds: List<EmployeeId> = emptyList(),
    ): GroupListResponse = inContext(orgId) { view.list(GroupListQuery(name, disciplineIds, employeeIds)) }

    private suspend fun forSelect(orgId: Uuid): List<GroupSelectItem> = inContext(orgId) { view.forSelect() }

    @Test
    fun `без фильтров возвращает все группы организации`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога")
            insertGroup(orgId, "Бокс")

            val result = list(orgId)

            assertEquals(2, result.groups.size)
            assertEquals(2u, result.total)
        }

    @Test
    fun `фильтрует по подстроке в имени без учёта регистра`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога утро")
            insertGroup(orgId, "Бокс взрослые")
            insertGroup(orgId, "Йога вечер")

            val result = list(orgId, name = "йога")

            assertEquals(2, result.groups.size)
            assertTrue(result.groups.all { it.name.contains("Йога") })
        }

    @Test
    fun `фильтрует по disciplineIds`() =
        runTest {
            val orgId = insertOrg()
            val yoga = insertDiscipline(orgId, "Йога")
            val box = insertDiscipline(orgId, "Бокс")
            val yogaGroup = insertGroup(orgId, "Утренняя йога")
            val boxGroup = insertGroup(orgId, "Бокс")
            val mixedGroup = insertGroup(orgId, "Микс")
            linkDiscipline(yogaGroup, yoga)
            linkDiscipline(boxGroup, box)
            linkDiscipline(mixedGroup, yoga)
            linkDiscipline(mixedGroup, box)

            val onlyYoga = list(orgId, disciplineIds = listOf(yoga))

            assertEquals(setOf(yogaGroup, mixedGroup), onlyYoga.groups.map { it.id }.toSet())
        }

    @Test
    fun `фильтрует по employeeIds`() =
        runTest {
            val orgId = insertOrg()
            val anna = insertEmployee(orgId, "Анна")
            val boris = insertEmployee(orgId, "Борис")
            val annaGroup = insertGroup(orgId, "Группа Анны")
            val borisGroup = insertGroup(orgId, "Группа Бориса")
            insertGroup(orgId, "Без тренеров")
            linkEmployee(annaGroup, anna)
            linkEmployee(borisGroup, boris)

            val result = list(orgId, employeeIds = listOf(anna))

            assertEquals(listOf(annaGroup), result.groups.map { it.id })
        }

    @Test
    fun `пересекает фильтры — имя и дисциплина одновременно`() =
        runTest {
            val orgId = insertOrg()
            val yoga = insertDiscipline(orgId, "Йога")
            val targetGroup = insertGroup(orgId, "Йога утро")
            val wrongName = insertGroup(orgId, "Силовая")
            val wrongDiscipline = insertGroup(orgId, "Йога вечер")
            linkDiscipline(targetGroup, yoga)
            linkDiscipline(wrongName, yoga)

            val result = list(orgId, name = "утро", disciplineIds = listOf(yoga))

            assertEquals(listOf(targetGroup), result.groups.map { it.id })
            assertTrue(wrongDiscipline !in result.groups.map { it.id })
        }

    @Test
    fun `изолирует группы по org_id`() =
        runTest {
            val orgA = insertOrg("A")
            val orgB = insertOrg("B")
            insertGroup(orgA, "A-Йога")
            insertGroup(orgB, "B-Йога")

            val result = list(orgA, name = "Йога")

            assertEquals(1, result.groups.size)
            assertEquals("A-Йога", result.groups.single().name)
        }

    @Test
    fun `total считает все группы независимо от фильтров`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога")
            insertGroup(orgId, "Бокс")
            insertGroup(orgId, "Плавание")

            val filtered = list(orgId, name = "Бокс")

            assertEquals(1, filtered.groups.size)
            assertEquals(3u, filtered.total)
        }

    @Test
    fun `total изолирует подсчёт по org_id`() =
        runTest {
            val orgA = insertOrg("A")
            val orgB = insertOrg("B")
            insertGroup(orgA, "A-1")
            insertGroup(orgA, "A-2")
            insertGroup(orgB, "B-1")

            assertEquals(2u, list(orgA).total)
            assertEquals(1u, list(orgB).total)
        }

    @Test
    fun `пустая строка в поиске не фильтрует`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога")
            insertGroup(orgId, "Бокс")

            assertEquals(2, list(orgId, name = "").groups.size)
            assertEquals(2, list(orgId, name = "   ").groups.size)
        }

    @Test
    fun `собирает расписание с названием зала и тренеров`() =
        runTest {
            val orgId = insertOrg()
            val groupId = insertGroup(orgId, "Йога")
            val hallId = insertHall(orgId, "Большой зал")
            val anna = insertEmployee(orgId, "Анна")
            linkEmployee(groupId, anna)
            insertSlot(orgId, groupId, hallId, DayOfWeek.MONDAY, LocalTime(10, 0), LocalTime(11, 30))

            val item = list(orgId).groups.single()

            val slot = item.schedule.single()
            assertEquals(DayOfWeek.MONDAY, slot.dayOfWeek)
            assertEquals(LocalTime(10, 0), slot.startAt)
            assertEquals(LocalTime(11, 30), slot.endAt)
            assertEquals(hallId, slot.hallId)
            assertEquals("Большой зал", slot.hallName)

            val employee = item.employees.single()
            assertEquals(anna, employee.id)
            assertEquals("Анна", employee.name)
            assertNull(employee.avatarId)
        }

    @Test
    fun `forSelect возвращает группы организации по имени`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога")
            insertGroup(orgId, "Бокс")

            assertEquals(listOf("Бокс", "Йога"), forSelect(orgId).map { it.name })
        }
}
