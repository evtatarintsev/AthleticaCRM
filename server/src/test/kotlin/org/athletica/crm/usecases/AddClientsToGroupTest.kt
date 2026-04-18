package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestAuditLog
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clients.AddClientsToGroupRequest
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.storage.asLong
import org.athletica.crm.usecases.clients.addClientsToGroup
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class AddClientsToGroupTest {
    @Before
    fun setUp() = TestPostgres.truncate()

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

    private suspend fun insertGroup(orgId: Uuid, name: String = "Группа"): Uuid {
        val groupId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", groupId)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return groupId
    }

    private suspend fun clientGroupCount(clientId: ClientId, groupId: Uuid): Long =
        TestPostgres.db
            .sql("SELECT COUNT(*) FROM client_groups WHERE client_id = :clientId AND group_id = :groupId")
            .bind("clientId", clientId)
            .bind("groupId", groupId)
            .firstOrNull { row -> row.asLong(0) }
            ?: 0L

    private fun ctx(orgId: Uuid) =
        RequestContext(
            lang = Lang.EN,
            userId = UserId.new(),
            orgId = OrgId(orgId),
            username = "user@example.com",
            clientIp = "127.0.0.1",
        )

    @Test
    fun `addClientsToGroup добавляет клиента в группу`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)
            val groupId = insertGroup(orgId)

            val result =
                TestPostgres.db.transaction {
                    context(ctx(orgId), this, TestAuditLog()) {
                        addClientsToGroup(AddClientsToGroupRequest(listOf(clientId), groupId))
                    }
                }
            assertIs<Either.Right<Unit>>(result)

            assertEquals(1L, clientGroupCount(clientId, groupId))
        }

    @Test
    fun `addClientsToGroup добавляет нескольких клиентов в группу`() =
        runTest {
            val orgId = insertOrg()
            val clientId1 = insertClient(orgId, "Клиент 1")
            val clientId2 = insertClient(orgId, "Клиент 2")
            val clientId3 = insertClient(orgId, "Клиент 3")
            val groupId = insertGroup(orgId)

            val result =
                TestPostgres.db.transaction {
                    context(ctx(orgId), this, TestAuditLog()) {
                        addClientsToGroup(AddClientsToGroupRequest(listOf(clientId1, clientId2, clientId3), groupId))
                    }
                }
            assertIs<Either.Right<Unit>>(result)

            assertEquals(1L, clientGroupCount(clientId1, groupId))
            assertEquals(1L, clientGroupCount(clientId2, groupId))
            assertEquals(1L, clientGroupCount(clientId3, groupId))
        }

    @Test
    fun `addClientsToGroup идемпотентен — повторное добавление не создаёт дубликат`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)
            val groupId = insertGroup(orgId)

            TestPostgres.db.transaction {
                context(ctx(orgId), this, TestAuditLog()) {
                    addClientsToGroup(AddClientsToGroupRequest(listOf(clientId), groupId))
                }
            }
            val result =
                TestPostgres.db.transaction {
                    context(ctx(orgId), this, TestAuditLog()) {
                        addClientsToGroup(AddClientsToGroupRequest(listOf(clientId), groupId))
                    }
                }
            assertIs<Either.Right<Unit>>(result)

            assertEquals(1L, clientGroupCount(clientId, groupId))
        }

    @Test
    fun `addClientsToGroup возвращает ошибку если группа не найдена`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId)

            val result =
                TestPostgres.db.transaction {
                    context(ctx(orgId), this, TestAuditLog()) {
                        addClientsToGroup(AddClientsToGroupRequest(listOf(clientId), Uuid.generateV7()))
                    }
                }
            val error = assertIs<Either.Left<CommonDomainError>>(result).value
            assertEquals("GROUP_NOT_FOUND", error.code)
        }

    @Test
    fun `addClientsToGroup не добавляет в группу чужой организации`() =
        runTest {
            val orgId1 = insertOrg("Org 1")
            val orgId2 = insertOrg("Org 2")
            val clientId = insertClient(orgId1)
            val foreignGroupId = insertGroup(orgId2)

            val result =
                TestPostgres.db.transaction {
                    context(ctx(orgId1), this, TestAuditLog()) {
                        addClientsToGroup(AddClientsToGroupRequest(listOf(clientId), foreignGroupId))
                    }
                }
            val error = assertIs<Either.Left<CommonDomainError>>(result).value
            assertEquals("GROUP_NOT_FOUND", error.code)

            assertEquals(0L, clientGroupCount(clientId, foreignGroupId))
        }

    @Test
    fun `addClientsToGroup возвращает ошибку если клиент не существует`() =
        runTest {
            val orgId = insertOrg()
            val groupId = insertGroup(orgId)

            // addClientsToGroup поглощает R2DBC-исключение FK и возвращает Either.Left.
            // При этом PostgreSQL переводит транзакцию в error-state, и commit падает.
            // Захватываем Either до коммита; PostgresqlRollbackException игнорируем.
            var result: Either<CommonDomainError, Unit>? = null
            runCatching {
                TestPostgres.db.transaction {
                    context(ctx(orgId), this, TestAuditLog()) {
                        result = addClientsToGroup(AddClientsToGroupRequest(listOf(ClientId.new()), groupId))
                    }
                }
            }
            val error = assertIs<Either.Left<CommonDomainError>>(result).value
            assertEquals("CLIENT_NOT_FOUND", error.code)
        }

    @Test
    fun `addClientsToGroup с пустым списком клиентов не падает`() =
        runTest {
            val orgId = insertOrg()
            val groupId = insertGroup(orgId)

            val result =
                TestPostgres.db.transaction {
                    context(ctx(orgId), this, TestAuditLog()) {
                        addClientsToGroup(AddClientsToGroupRequest(emptyList(), groupId))
                    }
                }
            assertIs<Either.Right<Unit>>(result)
        }
}
