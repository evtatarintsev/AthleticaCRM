package org.athletica.crm.usecases

import arrow.core.Either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestAuditLog
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clients.AdjustBalanceRequest
import org.athletica.crm.api.schemas.clients.ClientDetailResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.usecases.clients.adjustClientBalance
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class AdjustClientBalanceTest {
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

    private suspend fun insertUser(orgId: Uuid): Uuid {
        val userId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
            .bind("id", userId)
            .bind("login", "$userId@example.com")
            .bind("hash", "hash")
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (user_id, org_id, name, is_owner) VALUES (:userId, :orgId, :name, true)")
            .bind("userId", userId)
            .bind("orgId", orgId)
            .bind("name", "Admin")
            .execute()
        return userId
    }

    private suspend fun insertClient(orgId: Uuid, name: String = "Иван Петров"): Uuid {
        val clientId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return clientId
    }

    private suspend fun insertBalanceEntry(
        orgId: Uuid,
        clientId: Uuid,
        amount: Double,
        balanceAfter: Double,
    ) {
        TestPostgres.db
            .sql(
                """
                INSERT INTO client_balance_journal (id, org_id, client_id, amount, balance_after, operation_type)
                VALUES (:id, :orgId, :clientId, :amount, :balanceAfter, 'admin_credit'::balance_operation_type)
                """.trimIndent(),
            )
            .bind("id", Uuid.generateV7())
            .bind("orgId", orgId)
            .bind("clientId", clientId)
            .bind("amount", java.math.BigDecimal(amount.toString()))
            .bind("balanceAfter", java.math.BigDecimal(balanceAfter.toString()))
            .execute()
    }

    private fun ctx(userId: Uuid, orgId: Uuid) =
        RequestContext(
            lang = Lang.EN,
            userId = UserId(userId),
            orgId = OrgId(orgId),
            username = "admin@example.com",
            clientIp = "127.0.0.1",
        )

    @Test
    fun `adjustClientBalance пополняет баланс клиента`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)
            val clientId = insertClient(orgId)

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                val result = adjustClientBalance(AdjustBalanceRequest(clientId, 500.0, "Бонус"))
                val client = assertIs<Either.Right<ClientDetailResponse>>(result).value
                assertEquals(500.0, client.balance)
            }
        }

    @Test
    fun `adjustClientBalance списывает с баланса клиента`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)
            val clientId = insertClient(orgId)
            insertBalanceEntry(orgId, clientId, amount = 1000.0, balanceAfter = 1000.0)

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                val result = adjustClientBalance(AdjustBalanceRequest(clientId, -300.0, "Корректировка"))
                val client = assertIs<Either.Right<ClientDetailResponse>>(result).value
                assertEquals(700.0, client.balance)
            }
        }

    @Test
    fun `adjustClientBalance накапливает несколько операций`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)
            val clientId = insertClient(orgId)

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                assertIs<Either.Right<ClientDetailResponse>>(adjustClientBalance(AdjustBalanceRequest(clientId, 200.0, "Первое пополнение")))
                assertIs<Either.Right<ClientDetailResponse>>(adjustClientBalance(AdjustBalanceRequest(clientId, 300.0, "Второе пополнение")))
                val result = adjustClientBalance(AdjustBalanceRequest(clientId, -100.0, "Списание"))
                val client = assertIs<Either.Right<ClientDetailResponse>>(result).value
                assertEquals(400.0, client.balance)
            }
        }

    @Test
    fun `adjustClientBalance возвращает ошибку если сумма нулевая`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)
            val clientId = insertClient(orgId)

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                val result = adjustClientBalance(AdjustBalanceRequest(clientId, 0.0, "Комментарий"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("BALANCE_AMOUNT_ZERO", error.code)
            }
        }

    @Test
    fun `adjustClientBalance возвращает ошибку если комментарий пустой`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)
            val clientId = insertClient(orgId)

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                val result = adjustClientBalance(AdjustBalanceRequest(clientId, 100.0, ""))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("BALANCE_NOTE_REQUIRED", error.code)
            }
        }

    @Test
    fun `adjustClientBalance возвращает ошибку если комментарий состоит из пробелов`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)
            val clientId = insertClient(orgId)

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                val result = adjustClientBalance(AdjustBalanceRequest(clientId, 100.0, "   "))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("BALANCE_NOTE_REQUIRED", error.code)
            }
        }

    @Test
    fun `adjustClientBalance возвращает ошибку если клиент не найден`() =
        runTest {
            val orgId = insertOrg()
            val userId = insertUser(orgId)

            context(TestPostgres.db, ctx(userId, orgId), TestAuditLog()) {
                val result = adjustClientBalance(AdjustBalanceRequest(Uuid.generateV7(), 100.0, "Комментарий"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("CLIENT_NOT_FOUND", error.code)
            }
        }

    @Test
    fun `adjustClientBalance не применяет корректировку к клиенту из чужой организации`() =
        runTest {
            val orgId1 = insertOrg("Org 1")
            val orgId2 = insertOrg("Org 2")
            val userId2 = insertUser(orgId2)
            val clientId = insertClient(orgId1)

            context(TestPostgres.db, ctx(userId2, orgId2), TestAuditLog()) {
                val result = adjustClientBalance(AdjustBalanceRequest(clientId, 500.0, "Попытка корректировки"))
                val error = assertIs<Either.Left<CommonDomainError>>(result).value
                assertEquals("CLIENT_NOT_FOUND", error.code)
            }
        }
}
