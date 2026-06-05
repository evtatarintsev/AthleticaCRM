package org.athletica.crm.domain

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.SystemRequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.events.DomainEventBus
import org.athletica.crm.domain.groups.DbGroups
import org.athletica.crm.domain.groups.Group
import org.athletica.crm.storage.asUuid
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Тесты фильтрации в [DbGroups.list] и подсчёта общего количества групп
 * через [DbGroups.totalCount]. Покрывает фильтры по `name`, `disciplineIds`,
 * `employeeIds`, изоляцию по `org_id` и независимость `totalCount` от фильтров.
 */
class DbGroupsListTest {
    private val groups = DbGroups(DomainEventBus())

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    private suspend fun insertOrg(name: String = "Test Org"): Uuid {
        val orgId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", name)
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
            .bind("id", branchId)
            .bind("orgId", orgId)
            .bind("name", "Основной")
            .execute()
        return branchId
    }

    private suspend fun insertGroup(orgId: Uuid, name: String): GroupId {
        val groupId = GroupId.new()
        val branchId = ensureBranch(orgId)
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, name, branch_id) VALUES (:id, :orgId, :name, :branchId)")
            .bind("id", groupId)
            .bind("orgId", orgId)
            .bind("name", name)
            .bind("branchId", branchId)
            .execute()
        return groupId
    }

    private suspend fun insertDiscipline(orgId: Uuid, name: String): DisciplineId {
        val id = DisciplineId.new()
        TestPostgres.db
            .sql("INSERT INTO disciplines (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return id
    }

    private suspend fun linkDiscipline(groupId: GroupId, disciplineId: DisciplineId) {
        TestPostgres.db
            .sql("INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:g, :d)")
            .bind("g", groupId)
            .bind("d", disciplineId)
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
            .bind("id", id)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return id
    }

    private suspend fun linkEmployee(groupId: GroupId, employeeId: EmployeeId) {
        TestPostgres.db
            .sql("INSERT INTO group_employees (group_id, employee_id) VALUES (:g, :e)")
            .bind("g", groupId)
            .bind("e", employeeId)
            .execute()
    }

    private fun ctx(orgId: Uuid) =
        SystemRequestContext(
            lang = Lang.RU,
            orgId = OrgId(orgId),
            currency = Currency.RUB,
            branchId = null,
        )

    private suspend fun listGroups(
        orgId: Uuid,
        name: String? = null,
        disciplineIds: List<DisciplineId> = emptyList(),
        employeeIds: List<EmployeeId> = emptyList(),
    ): List<Group> =
        either {
            TestPostgres.db.transaction {
                context(ctx(orgId)) {
                    groups.list(nameQuery = name, disciplineIds = disciplineIds, employeeIds = employeeIds)
                }
            }
        }.getOrElse { error(it.message) }

    private suspend fun totalCount(orgId: Uuid): Int =
        either {
            TestPostgres.db.transaction {
                context(ctx(orgId)) {
                    groups.totalCount()
                }
            }
        }.getOrElse { error(it.message) }

    @Test
    fun `list без фильтров возвращает все группы организации`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога")
            insertGroup(orgId, "Бокс")

            val result = listGroups(orgId)

            assertEquals(2, result.size)
        }

    @Test
    fun `list фильтрует по подстроке в имени без учёта регистра`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога утро")
            insertGroup(orgId, "Бокс взрослые")
            insertGroup(orgId, "Йога вечер")

            val result = listGroups(orgId, name = "йога")

            assertEquals(2, result.size)
            assertTrue(result.all { it.name.contains("Йога") })
        }

    @Test
    fun `list фильтрует по disciplineIds`() =
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

            val onlyYoga = listGroups(orgId, disciplineIds = listOf(yoga))

            assertEquals(setOf(yogaGroup, mixedGroup), onlyYoga.map { it.id }.toSet())
        }

    @Test
    fun `list фильтрует по employeeIds`() =
        runTest {
            val orgId = insertOrg()
            val anna = insertEmployee(orgId, "Анна")
            val boris = insertEmployee(orgId, "Борис")
            val annaGroup = insertGroup(orgId, "Группа Анны")
            val borisGroup = insertGroup(orgId, "Группа Бориса")
            insertGroup(orgId, "Без тренеров")
            linkEmployee(annaGroup, anna)
            linkEmployee(borisGroup, boris)

            val result = listGroups(orgId, employeeIds = listOf(anna))

            assertEquals(listOf(annaGroup), result.map { it.id })
        }

    @Test
    fun `list пересекает фильтры — имя и дисциплина одновременно`() =
        runTest {
            val orgId = insertOrg()
            val yoga = insertDiscipline(orgId, "Йога")
            val targetGroup = insertGroup(orgId, "Йога утро")
            val wrongName = insertGroup(orgId, "Силовая")
            val wrongDiscipline = insertGroup(orgId, "Йога вечер")
            linkDiscipline(targetGroup, yoga)
            linkDiscipline(wrongName, yoga)

            val result = listGroups(orgId, name = "утро", disciplineIds = listOf(yoga))

            assertEquals(listOf(targetGroup), result.map { it.id })
            assertTrue(wrongDiscipline !in result.map { it.id })
        }

    @Test
    fun `list изолирует группы по org_id`() =
        runTest {
            val orgA = insertOrg("A")
            val orgB = insertOrg("B")
            insertGroup(orgA, "A-Йога")
            insertGroup(orgB, "B-Йога")

            val result = listGroups(orgA, name = "Йога")

            assertEquals(1, result.size)
            assertEquals("A-Йога", result.single().name)
        }

    @Test
    fun `totalCount считает все группы независимо от фильтров`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога")
            insertGroup(orgId, "Бокс")
            insertGroup(orgId, "Плавание")

            assertEquals(3, totalCount(orgId))
            assertEquals(1, listGroups(orgId, name = "Бокс").size)
            assertEquals(3, totalCount(orgId))
        }

    @Test
    fun `totalCount изолирует подсчёт по org_id`() =
        runTest {
            val orgA = insertOrg("A")
            val orgB = insertOrg("B")
            insertGroup(orgA, "A-1")
            insertGroup(orgA, "A-2")
            insertGroup(orgB, "B-1")

            assertEquals(2, totalCount(orgA))
            assertEquals(1, totalCount(orgB))
        }

    @Test
    fun `list игнорирует пустую строку в name`() =
        runTest {
            val orgId = insertOrg()
            insertGroup(orgId, "Йога")
            insertGroup(orgId, "Бокс")

            assertEquals(2, listGroups(orgId, name = "").size)
            assertEquals(2, listGroups(orgId, name = "   ").size)
        }
}
