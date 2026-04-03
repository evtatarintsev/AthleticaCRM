package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestAuditLog
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.api.schemas.clients.CreateClientRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.usecases.clients.createClient
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class CreateClientTest {
    @Before
    fun setUp() = TestPostgres.truncate()

    private suspend fun insertOrg(): Uuid {
        val orgId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", "Test Org")
            .execute()
        return orgId
    }

    private suspend fun insertUser(orgId: Uuid): Uuid {
        val userId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, name, password_hash) VALUES (:id, :login, :name, :hash)")
            .bind("id", userId)
            .bind("login", "user@example.com")
            .bind("name", "User")
            .bind("hash", "hash")
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (user_id, org_id, is_owner) VALUES (:userId, :orgId, true)")
            .bind("userId", userId)
            .bind("orgId", orgId)
            .execute()
        return userId
    }

    private fun ctx(userId: Uuid, orgId: Uuid) =
        RequestContext(
            lang = Lang.EN,
            userId = UserId(userId),
            orgId = OrgId(orgId),
            username = "user@example.com",
            clientIp = "127.0.0.1",
        )

    @Test
    fun `createClient returns ClientDetailResponse on success`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)
            val request = CreateClientRequest(id = Uuid.generateV7(), name = "Иван Петров")

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                val result = createClient(request)
                val client = assertIs<Either.Right<ClientDetailResponse>>(result).value
                assertEquals(request.id, client.id)
                assertEquals("Иван Петров", client.name)
            }
        }

    @Test
    fun `createClient returns error when client with same id already exists`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)
            val request = CreateClientRequest(id = Uuid.generateV7(), name = "Иван Петров")

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                createClient(request)
                val result = createClient(request)
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("CLIENT_ALREADY_EXISTS", error.code)
            }
        }

    @Test
    fun `createClient isolates clients between organizations`() =
        runTest {
            val orgId1 = insertOrg()
            val orgId2 =
                run {
                    val id = Uuid.generateV7()
                    TestPostgres.db
                        .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                        .bind("id", id)
                        .bind("name", "Org 2")
                        .execute()
                    id
                }
            val userId = insertUser(orgId1)
            val sharedId = Uuid.generateV7()

            context(TestPostgres.db, ctx(userId, orgId1), TestAuditLog()) {
                assertIs<Either.Right<ClientDetailResponse>>(
                    createClient(CreateClientRequest(id = sharedId, name = "Клиент")),
                )
            }
            // тот же UUID, но другая org — не конфликт на уровне БД,
            // так как PK на id уникален глобально; ожидаем ошибку дублирования PK
            context(TestPostgres.db, ctx(userId, orgId2), TestAuditLog()) {
                val result = createClient(CreateClientRequest(id = sharedId, name = "Клиент"))
                assertIs<Either.Left<CommonDomainError>>(result)
            }
        }

    @Test
    fun `createClient allows same name for different clients in same org`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                assertIs<Either.Right<ClientDetailResponse>>(
                    createClient(CreateClientRequest(id = Uuid.generateV7(), name = "Алексей")),
                )
                assertIs<Either.Right<ClientDetailResponse>>(
                    createClient(CreateClientRequest(id = Uuid.generateV7(), name = "Алексей")),
                )
            }
        }
}
