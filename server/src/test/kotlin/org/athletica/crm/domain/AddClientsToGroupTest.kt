package org.athletica.crm.domain

import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.enrollments.DbEnrollments
import org.athletica.crm.domain.enrollments.Enrollment
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asUuid
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AddClientsToGroupTest {
    private val userId = UserId.new()
    private val enrollments = DbEnrollments()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db
                .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId)
                .bind("login", "${userId.value}@example.com")
                .bind("hash", "hash")
                .execute()
        }
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

    private suspend fun insertClient(orgId: Uuid, name: String = "Клиент"): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return clientId
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

    private suspend fun insertGroup(orgId: Uuid, name: String = "Группа"): GroupId {
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

    private suspend fun activeEnrollmentCount(clientId: ClientId, groupId: GroupId): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM enrollments WHERE client_id = :clientId AND group_id = :groupId AND left_at IS NULL")
            .bind("clientId", clientId)
            .bind("groupId", groupId)
            .firstOrNull { row -> row.asLong(0) }
            ?: 0L

    private fun ctx(orgId: Uuid) =
        RequestContext(
            lang = Lang.EN,
            userId = userId,
            orgId = OrgId(orgId),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "user@example.com",
            clientIp = "127.0.0.1",
            permission = EmployeePermission(),
        )

    @Test
    fun `add добавляет клиента в группу`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)
            val groupId = insertGroup(orgId)

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx(orgId), this) {
                        enrollments.add(groupId, listOf(clientId))
                    }
                }
            }.getOrElse { error(it.message) }

            assertEquals(1L, activeEnrollmentCount(clientId, groupId))
        }

    @Test
    fun `add добавляет нескольких клиентов в группу`() =
        runTest {
            val orgId = insertOrg()
            val clientId1 = insertClient(orgId, "Клиент 1")
            val clientId2 = insertClient(orgId, "Клиент 2")
            val clientId3 = insertClient(orgId, "Клиент 3")
            val groupId = insertGroup(orgId)

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx(orgId), this) {
                        enrollments.add(groupId, listOf(clientId1, clientId2, clientId3))
                    }
                }
            }.getOrElse { error(it.message) }

            assertEquals(1L, activeEnrollmentCount(clientId1, groupId))
            assertEquals(1L, activeEnrollmentCount(clientId2, groupId))
            assertEquals(1L, activeEnrollmentCount(clientId3, groupId))
        }

    @Test
    fun `add идемпотентен — повторное добавление не создаёт дубликат`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)
            val groupId = insertGroup(orgId)

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx(orgId), this) {
                        enrollments.add(groupId, listOf(clientId))
                        enrollments.add(groupId, listOf(clientId))
                    }
                }
            }.getOrElse { error(it.message) }

            assertEquals(1L, activeEnrollmentCount(clientId, groupId))
        }

    @Test
    fun `remove деактивирует участие клиента`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)
            val groupId = insertGroup(orgId)

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx(orgId), this) {
                        enrollments.add(groupId, listOf(clientId))
                        enrollments.remove(groupId, listOf(clientId))
                    }
                }
            }.getOrElse { error(it.message) }

            assertEquals(0L, activeEnrollmentCount(clientId, groupId))
        }

    @Test
    fun `повторная запись после выхода создаёт новый период`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)
            val groupId = insertGroup(orgId)

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx(orgId), this) {
                        enrollments.add(groupId, listOf(clientId))
                        enrollments.remove(groupId, listOf(clientId))
                        enrollments.add(groupId, listOf(clientId))
                    }
                }
            }.getOrElse { error(it.message) }

            assertEquals(1L, activeEnrollmentCount(clientId, groupId))
            val totalCount =
                TestPostgres.db
                    .sql("SELECT COUNT(*) FROM enrollments WHERE client_id = :c AND group_id = :g")
                    .bind("c", clientId)
                    .bind("g", groupId)
                    .firstOrNull { row -> row.asLong(0) } ?: 0L
            assertEquals(2L, totalCount)
        }

    @Test
    fun `add возвращает ошибку если группа не найдена`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)

            var raised = false
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx(orgId), this) {
                        enrollments.add(GroupId.new(), listOf(clientId))
                    }
                }
            }.onLeft { error ->
                raised = true
                assertEquals("GROUP_NOT_FOUND", (error as CommonDomainError).code)
            }
            assertTrue(raised)
        }

    @Test
    fun `add не добавляет в группу чужой организации`() =
        runTest {
            val orgId1 = insertOrg("Org 1")
            val orgId2 = insertOrg("Org 2")
            val clientId = insertClient(orgId1)
            val foreignGroupId = insertGroup(orgId2)

            var raised = false
            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx(orgId1), this) {
                        enrollments.add(foreignGroupId, listOf(clientId))
                    }
                }
            }.onLeft { error ->
                raised = true
                assertEquals("GROUP_NOT_FOUND", (error as CommonDomainError).code)
            }
            assertTrue(raised)
            assertEquals(0L, activeEnrollmentCount(clientId, foreignGroupId))
        }

    @Test
    fun `activeIn возвращает клиентов активных в периоде`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)
            val groupId = insertGroup(orgId)

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx(orgId), this) {
                        enrollments.add(groupId, listOf(clientId))
                    }
                }
            }.getOrElse { error(it.message) }

            val result =
                either<DomainError, List<Enrollment>> {
                    TestPostgres.db.transaction {
                        context(ctx(orgId), this) {
                            enrollments.activeIn(groupId, LocalDate(2000, 1, 1), LocalDate(2099, 12, 31))
                        }
                    }
                }.getOrElse { error(it.message) }

            assertEquals(1, result.size)
            assertEquals(clientId, result.first().clientId)
        }
}
