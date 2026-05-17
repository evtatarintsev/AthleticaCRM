package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.clientbalance.DbClientBalances
import org.athletica.crm.domain.clients.DbClients
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.storage.asDouble
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.uuid.Uuid

class DbClientBalancesTest {
    private val orgId = OrgId.new()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()

    private val ctx =
        RequestContext(
            Lang.EN, userId, orgId, BranchId.new(), employeeId, "admin@example.com", null,
            Currency.RUB, EmployeePermission(),
        )

    private fun rub(amount: Double): Money = Money(java.math.BigDecimal(amount.toString()).movePointRight(Currency.RUB.fractionDigits).toLong(), Currency.RUB)

    private val balances = DbClientBalances()
    private val clients = DbClients()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org 1").execute()
            TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash)")
                .bind("id", userId).bind("login", "admin@example.com").bind("hash", "hash").execute()
            TestPostgres.db.sql("INSERT INTO employees (id, user_id, org_id, name, is_owner) VALUES (:id, :userId, :orgId, :name, true)")
                .bind("id", employeeId).bind("userId", userId).bind("orgId", orgId).bind("name", "Admin").execute()
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
                INSERT INTO client_balance_journal (id, org_id, client_id, amount, balance_after, operation_type, performed_by)
                VALUES (:id, :orgId, :clientId, :amount, :balanceAfter, 'admin_credit'::balance_operation_type, :performedBy)
                """.trimIndent(),
            )
            .bind("id", Uuid.generateV7())
            .bind("orgId", orgId)
            .bind("clientId", clientId)
            .bind("amount", java.math.BigDecimal(amount.toString()))
            .bind("balanceAfter", java.math.BigDecimal(balanceAfter.toString()))
            .bind("performedBy", employeeId)
            .execute()
    }

    private suspend fun getBalance(clientId: ClientId): Double =
        TestPostgres.db
            .sql("SELECT COALESCE(SUM(amount), 0) FROM client_balance_journal WHERE client_id = :id")
            .bind("id", clientId)
            .firstOrNull { row -> row.asDouble(0) } ?: 0.0

    // ── currentOf ────────────────────────────────────────────────────────────

    @Test
    fun `currentOf возвращает нулевой баланс если нет операций`() =
        runTest {
            val clientId = insertClient()
            val balance =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            balances.currentOf(clients.byId(clientId))
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(Money.zero(Currency.RUB), balance.totalAmount)
        }

    @Test
    fun `currentOf возвращает текущий баланс из последней записи журнала`() =
        runTest {
            val clientId = insertClient()
            insertBalanceEntry(clientId, 500.0, 500.0)
            insertBalanceEntry(clientId, -200.0, 300.0)

            val balance =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            balances.currentOf(clients.byId(clientId))
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(rub(300.0), balance.totalAmount)
        }

    @Test
    fun `currentOf для списка клиентов возвращает баланс по каждому`() =
        runTest {
            val clientId1 = insertClient(name = "Клиент 1")
            val clientId2 = insertClient(name = "Клиент 2")
            insertBalanceEntry(clientId1, 500.0, 500.0)

            val byClient =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            balances.currentOf(clients.list()).associateBy { it.clientId }
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(2, byClient.size)
            assertEquals(rub(500.0), byClient.getValue(clientId1).totalAmount)
            assertEquals(Money.zero(Currency.RUB), byClient.getValue(clientId2).totalAmount)
        }

    // ── history ──────────────────────────────────────────────────────────────

    @Test
    fun `history загружает все операции клиента в обратном хронологическом порядке`() =
        runTest {
            val clientId = insertClient()
            insertBalanceEntry(clientId, 500.0, 500.0)
            insertBalanceEntry(clientId, -200.0, 300.0)

            val history =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            balances.currentOf(clients.byId(clientId)).history()
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(2, history.size)
            assertEquals(rub(-200.0), history[0].amount)
            assertEquals(rub(500.0), history[1].amount)
        }

    @Test
    fun `history возвращает пустой список если операций нет`() =
        runTest {
            val clientId = insertClient()

            val history =
                either<DomainError, _> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            balances.currentOf(clients.byId(clientId)).history()
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(emptyList(), history)
        }

    // ── adjust ───────────────────────────────────────────────────────────────

    @Test
    fun `adjust пополняет баланс клиента`() =
        runTest {
            val clientId = insertClient()

            either<DomainError, Unit> {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        balances.currentOf(clients.byId(clientId)).adjust(rub(500.0), "Бонус")
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
                    context(ctx, this) {
                        balances.currentOf(clients.byId(clientId)).adjust(rub(-300.0), "Корректировка")
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
                    context(ctx, this) {
                        val b = balances.currentOf(clients.byId(clientId))
                        val b2 = b.adjust(rub(200.0), "Первое пополнение")
                        val b3 = b2.adjust(rub(300.0), "Второе пополнение")
                        b3.adjust(rub(-100.0), "Списание")
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
                        context(ctx, this) {
                            balances.currentOf(clients.byId(clientId)).adjust(rub(750.0), "Пополнение")
                        }
                    }
                }.getOrElse { fail("Expected success: $it") }

            assertEquals(rub(750.0), updated.totalAmount)
        }

    @Test
    fun `adjust возвращает ошибку если сумма нулевая`() =
        runTest {
            val clientId = insertClient()

            val result =
                either<DomainError, Unit> {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            balances.currentOf(clients.byId(clientId)).adjust(Money.zero(Currency.RUB), "Комментарий")
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
                        context(ctx, this) {
                            balances.currentOf(clients.byId(clientId)).adjust(rub(100.0), "")
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
                        context(ctx, this) {
                            balances.currentOf(clients.byId(clientId)).adjust(rub(100.0), "   ")
                        }
                    }
                }

            assertIs<Either.Left<DomainError>>(result)
            assertEquals("BALANCE_NOTE_REQUIRED", result.value.code)
        }
}
