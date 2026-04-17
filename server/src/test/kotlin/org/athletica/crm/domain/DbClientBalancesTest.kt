package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestAuditLog
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.ClientId
import org.athletica.crm.core.Lang
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clientbalance.DbClientBalances
import org.athletica.crm.storage.asDouble
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.uuid.Uuid

class DbClientBalancesTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val userId = UserId.new()

    private val ctx = RequestContext(Lang.EN, userId, orgId, "admin@example.com", null)
    private val otherCtx = RequestContext(Lang.EN, UserId.new(), otherOrgId, "admin@example.com", null)

    private val balances = DbClientBalances()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org 1").execute()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Org 2").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", "admin@example.com").bind("hash", "hash").execute()
            TestPostgres.db.sql("INSERT INTO employees (user_id, org_id, name, is_owner) VALUES (:userId, :orgId, :name, true)")
                .bind("userId", userId).bind("orgId", orgId).bind("name", "Admin").execute()
        }
    }

    private suspend fun insertClient(orgId: OrgId = this.orgId, name: String = "Иван Петров"): ClientId {
        val clientId = ClientId.new()
        TestPostgres.db
            .sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
            .bind("id", clientId)
            .bind("orgId", orgId)
            .bind("name", name)
            .execute()
        return clientId
    }

    private suspend fun insertBalanceEntry(clientId: ClientId, amount: Double, balanceAfter: Double) {
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

    private suspend fun getBalance(clientId: ClientId): Double =
        TestPostgres.db
            .sql("SELECT COALESCE(SUM(amount), 0) FROM client_balance_journal WHERE client_id = :id")
            .bind("id", clientId)
            .firstOrNull { row -> row.asDouble(0) } ?: 0.0

    // ── forClient ────────────────────────────────────────────────────────────

    @Test
    fun `forClient возвращает нулевой баланс для нового клиента`() =
        runTest {
            val clientId = insertClient()
            val balance =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { balances.forClient(clientId) }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(0.0, balance.totalAmount)
            assertEquals(emptyList(), balance.history)
        }

    @Test
    fun `forClient загружает историю операций`() =
        runTest {
            val clientId = insertClient()
            insertBalanceEntry(clientId, 500.0, 500.0)
            insertBalanceEntry(clientId, -200.0, 300.0)

            val balance =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { balances.forClient(clientId) }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(300.0, balance.totalAmount)
            assertEquals(2, balance.history.size)
        }

    @Test
    fun `forClient возвращает ошибку если клиент не найден`() =
        runTest {
            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) { balances.forClient(ClientId.new()) }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("CLIENT_NOT_FOUND", result.value.code)
        }

    @Test
    fun `forClient изолирует клиентов разных организаций`() =
        runTest {
            val clientId = insertClient(orgId)

            val result =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(otherCtx, this) { balances.forClient(clientId) }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("CLIENT_NOT_FOUND", result.value.code)
        }

    // ── adjust ───────────────────────────────────────────────────────────────

    @Test
    fun `adjust пополняет баланс клиента`() =
        runTest {
            val clientId = insertClient()

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this, TestAuditLog()) {
                        balances.forClient(clientId).adjust(500.0, "Бонус")
                    }
                }
            }.getOrElse { fail("Expected success: $it") }

            assertEquals(500.0, getBalance(clientId))
        }

    @Test
    fun `adjust списывает с баланса клиента`() =
        runTest {
            val clientId = insertClient()
            insertBalanceEntry(clientId, 1000.0, 1000.0)

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this, TestAuditLog()) {
                        balances.forClient(clientId).adjust(-300.0, "Корректировка")
                    }
                }
            }.getOrElse { fail("Expected success: $it") }

            assertEquals(700.0, getBalance(clientId))
        }

    @Test
    fun `adjust накапливает несколько операций`() =
        runTest {
            val clientId = insertClient()

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this, TestAuditLog()) {
                        val b = balances.forClient(clientId)
                        val b2 = b.adjust(200.0, "Первое пополнение")
                        val b3 = b2.adjust(300.0, "Второе пополнение")
                        b3.adjust(-100.0, "Списание")
                    }
                }
            }.getOrElse { fail("Expected success: $it") }

            assertEquals(400.0, getBalance(clientId))
        }

    @Test
    fun `adjust возвращает обновлённый объект с корректным totalAmount`() =
        runTest {
            val clientId = insertClient()

            val updated =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this, TestAuditLog()) {
                            balances.forClient(clientId).adjust(750.0, "Пополнение")
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(750.0, updated.totalAmount)
            assertEquals(1, updated.history.size)
            assertEquals(750.0, updated.history.first().amount)
        }

    @Test
    fun `adjust возвращает ошибку если сумма нулевая`() =
        runTest {
            val clientId = insertClient()

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(ctx, this, TestAuditLog()) {
                            balances.forClient(clientId).adjust(0.0, "Комментарий")
                        }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("BALANCE_AMOUNT_ZERO", result.value.code)
        }

    @Test
    fun `adjust возвращает ошибку если комментарий пустой`() =
        runTest {
            val clientId = insertClient()

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(ctx, this, TestAuditLog()) {
                            balances.forClient(clientId).adjust(100.0, "")
                        }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("BALANCE_NOTE_REQUIRED", result.value.code)
        }

    @Test
    fun `adjust возвращает ошибку если комментарий из пробелов`() =
        runTest {
            val clientId = insertClient()

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(ctx, this, TestAuditLog()) {
                            balances.forClient(clientId).adjust(100.0, "   ")
                        }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("BALANCE_NOTE_REQUIRED", result.value.code)
        }

    @Test
    fun `adjust не применяет корректировку к клиенту из чужой организации`() =
        runTest {
            val clientId = insertClient(orgId)

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(otherCtx, this, TestAuditLog()) {
                            balances.forClient(clientId).adjust(500.0, "Попытка корректировки")
                        }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("CLIENT_NOT_FOUND", result.value.code)
            assertEquals(0.0, getBalance(clientId))
        }
}
