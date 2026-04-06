package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.usecases.clients.clientDetail
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ClientDetailTest {
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

    private suspend fun insertClient(orgId: Uuid, name: String): Uuid {
        val clientId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", clientId)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return clientId
    }

    private suspend fun insertGroup(orgId: Uuid, name: String): Uuid {
        val groupId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO groups (id, org_id, name) VALUES (:id, :orgId, :name)")
            .bind("id", groupId)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return groupId
    }

    private suspend fun addClientToGroup(clientId: Uuid, groupId: Uuid) {
        TestPostgres.db
            .sql("INSERT INTO client_groups (client_id, group_id) VALUES (:clientId, :groupId)")
            .bind("clientId", clientId)
            .bind("groupId", groupId)
            .execute()
    }

    private fun ctx(orgId: Uuid) =
        RequestContext(
            lang = Lang.EN,
            userId = UserId.new(),
            orgId = OrgId(orgId),
            username = "user@example.com",
            clientIp = "127.0.0.1",
        )

    @Test
    fun `clientDetail возвращает данные клиента`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId, "Иван Петров")

            context(TestPostgres.db, ctx(orgId)) {
                val result = clientDetail(clientId)
                val client = assertIs<Either.Right<ClientDetailResponse>>(result).value
                assertEquals(clientId, client.id)
                assertEquals("Иван Петров", client.name)
            }
        }

    @Test
    fun `clientDetail возвращает пустой список групп если клиент не состоит ни в одной группе`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId, "Клиент Без Групп")

            context(TestPostgres.db, ctx(orgId)) {
                val result = clientDetail(clientId)
                val client = assertIs<Either.Right<ClientDetailResponse>>(result).value
                assertTrue(client.groups.isEmpty())
            }
        }

    @Test
    fun `clientDetail возвращает группы клиента`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId, "Клиент С Группами")
            val groupId1 = insertGroup(orgId, "Боевое самбо")
            val groupId2 = insertGroup(orgId, "Мужчины")
            addClientToGroup(clientId, groupId1)
            addClientToGroup(clientId, groupId2)

            context(TestPostgres.db, ctx(orgId)) {
                val result = clientDetail(clientId)
                val client = assertIs<Either.Right<ClientDetailResponse>>(result).value
                assertEquals(2, client.groups.size)
                assertTrue(client.groups.any { it.id == groupId1 && it.name == "Боевое самбо" })
                assertTrue(client.groups.any { it.id == groupId2 && it.name == "Мужчины" })
            }
        }

    @Test
    fun `clientDetail возвращает ошибку если клиент не найден`() =
        runTest {
            val orgId = insertOrg()

            context(TestPostgres.db, ctx(orgId)) {
                val result = clientDetail(Uuid.generateV7())
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("CLIENT_NOT_FOUND", error.code)
            }
        }

    @Test
    fun `clientDetail не возвращает клиента из чужой организации`() =
        runTest {
            val orgId1 = insertOrg("Org 1")
            val orgId2 = insertOrg("Org 2")
            val clientId = insertClient(orgId1, "Клиент Орг 1")

            context(TestPostgres.db, ctx(orgId2)) {
                val result = clientDetail(clientId)
                assertIs<Either.Left<CommonDomainError>>(result)
            }
        }

    @Test
    fun `clientDetail не возвращает группы из чужой организации`() =
        runTest {
            val orgId1 = insertOrg("Org 1")
            val orgId2 = insertOrg("Org 2")
            val clientId = insertClient(orgId1, "Клиент")
            val ownGroupId = insertGroup(orgId1, "Своя группа")
            addClientToGroup(clientId, ownGroupId)
            insertGroup(orgId2, "Чужая группа")

            context(TestPostgres.db, ctx(orgId1)) {
                val result = clientDetail(clientId)
                val client = assertIs<Either.Right<ClientDetailResponse>>(result).value
                assertEquals(1, client.groups.size)
                assertEquals("Своя группа", client.groups[0].name)
            }
        }
}
