package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.usecases.clients.clientList
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ClientListTest {
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
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
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
    fun `clientList возвращает пустой список если клиентов нет`() =
        runTest {
            val orgId = insertOrg()
            context(TestPostgres.db, ctx(orgId)) {
                val result = clientList(ClientListRequest())
                val clients = assertIs<Either.Right<List<ClientListItem>>>(result).value
                assertTrue(clients.isEmpty())
            }
        }

    @Test
    fun `clientList возвращает клиентов организации`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Анна Иванова")
            insertClient(orgId, "Борис Петров")

            context(TestPostgres.db, ctx(orgId)) {
                val result = clientList(ClientListRequest())
                val clients = assertIs<Either.Right<List<ClientListItem>>>(result).value
                assertEquals(2, clients.size)
                assertTrue(clients.any { it.name == "Анна Иванова" })
                assertTrue(clients.any { it.name == "Борис Петров" })
            }
        }

    @Test
    fun `clientList возвращает пустой список групп если клиент не состоит ни в одной группе`() =
        runTest {
            val orgId = insertOrg()
            insertClient(orgId, "Клиент Без Групп")

            context(TestPostgres.db, ctx(orgId)) {
                val result = clientList(ClientListRequest())
                val clients = assertIs<Either.Right<List<ClientListItem>>>(result).value
                assertEquals(1, clients.size)
                assertTrue(clients[0].groups.isEmpty())
            }
        }

    @Test
    fun `clientList возвращает группы клиента`() =
        runTest {
            val orgId = insertOrg()
            val clientId = insertClient(orgId, "Клиент С Группами")
            val groupId1 = insertGroup(orgId, "Боевое самбо")
            val groupId2 = insertGroup(orgId, "Мужчины")
            addClientToGroup(clientId, groupId1)
            addClientToGroup(clientId, groupId2)

            context(TestPostgres.db, ctx(orgId)) {
                val result = clientList(ClientListRequest())
                val clients = assertIs<Either.Right<List<ClientListItem>>>(result).value
                val client = clients.single()
                assertEquals(2, client.groups.size)
                assertTrue(client.groups.any { it.name == "Боевое самбо" })
                assertTrue(client.groups.any { it.name == "Мужчины" })
            }
        }

    @Test
    fun `clientList у разных клиентов свои группы`() =
        runTest {
            val orgId = insertOrg()
            val client1Id = insertClient(orgId, "Клиент 1")
            val client2Id = insertClient(orgId, "Клиент 2")
            val groupId = insertGroup(orgId, "Боевое самбо")
            addClientToGroup(client1Id, groupId)

            context(TestPostgres.db, ctx(orgId)) {
                val result = clientList(ClientListRequest())
                val clients = assertIs<Either.Right<List<ClientListItem>>>(result).value
                val client1 = clients.first { it.name == "Клиент 1" }
                val client2 = clients.first { it.name == "Клиент 2" }
                assertEquals(1, client1.groups.size)
                assertTrue(client2.groups.isEmpty())
            }
        }

    @Test
    fun `clientList изолирует клиентов между организациями`() =
        runTest {
            val orgId1 = insertOrg("Org 1")
            val orgId2 = insertOrg("Org 2")
            insertClient(orgId1, "Клиент Орг 1")
            insertClient(orgId2, "Клиент Орг 2")

            context(TestPostgres.db, ctx(orgId1)) {
                val result = clientList(ClientListRequest())
                val clients = assertIs<Either.Right<List<ClientListItem>>>(result).value
                assertEquals(1, clients.size)
                assertEquals("Клиент Орг 1", clients[0].name)
            }
        }

    @Test
    fun `clientList не возвращает группы чужой организации`() =
        runTest {
            val orgId1 = insertOrg("Org 1")
            val orgId2 = insertOrg("Org 2")
            val clientId = insertClient(orgId1, "Клиент")
            val foreignGroupId = insertGroup(orgId2, "Чужая группа")
            // группа из другой орг не должна быть связана через client_groups,
            // но убеждаемся что clientList не возвращает чужие группы
            insertGroup(orgId1, "Своя группа").also { addClientToGroup(clientId, it) }

            context(TestPostgres.db, ctx(orgId1)) {
                val result = clientList(ClientListRequest())
                val clients = assertIs<Either.Right<List<ClientListItem>>>(result).value
                val client = clients.single()
                assertEquals(1, client.groups.size)
                assertEquals("Своя группа", client.groups[0].name)
            }
        }
}
