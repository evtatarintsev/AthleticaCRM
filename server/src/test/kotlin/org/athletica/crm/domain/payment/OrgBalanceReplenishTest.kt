package org.athletica.crm.domain.payment

import arrow.core.raise.context.either
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.systemContext
import org.athletica.crm.domain.orgbalance.DbOrgBalances
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** Интеграционные тесты метода [org.athletica.crm.domain.orgbalance.OrgBalance.replenish]. */
class OrgBalanceReplenishTest {
    private val orgBalances = DbOrgBalances()

    @Before
    fun setUp() {
        TestPostgres.truncate()
    }

    @Test
    fun `replenish зачисляет сумму на баланс с типом replenishment`() =
        runTest {
            val orgId = insertOrg()
            val ctx = systemContext(OrgId(orgId), currency = Currency.RUB)

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            val balance = orgBalances.current()
                            balance.replenish(
                                amount = Money(500_00, Currency.RUB),
                                description = "Пополнение баланса, платёж #ext_001",
                            )
                        }
                    }
                }

            assertIs<arrow.core.Either.Right<*>>(result)

            // Проверяем запись в журнале
            val operationType =
                TestPostgres.db
                    .sql("SELECT operation_type FROM org_balance_journal WHERE org_id = :orgId")
                    .bind("orgId", orgId)
                    .firstOrNull { row -> row.get("operation_type", String::class.java) }

            assertEquals("replenishment", operationType)
        }

    @Test
    fun `replenish обновляет баланс организации`() =
        runTest {
            val orgId = insertOrg()
            val ctx = systemContext(OrgId(orgId), currency = Currency.RUB)

            either {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val balance = orgBalances.current()
                        balance.replenish(Money(100_00, Currency.RUB), "Первое пополнение")
                    }
                }
            }
            either {
                TestPostgres.db.transaction {
                    context(ctx, this) {
                        val balance = orgBalances.current()
                        balance.replenish(Money(200_00, Currency.RUB), "Второе пополнение")
                    }
                }
            }

            val totalAmount =
                TestPostgres.db
                    .sql("SELECT COALESCE(SUM(amount), 0) AS total FROM org_balance_journal WHERE org_id = :orgId")
                    .bind("orgId", orgId)
                    .firstOrNull { row -> row.get("total", java.math.BigDecimal::class.java)!! }

            assertEquals(java.math.BigDecimal("300.00"), totalAmount)
        }

    @Test
    fun `replenish с нулевой суммой возвращает ошибку`() =
        runTest {
            val orgId = insertOrg()
            val ctx = systemContext(OrgId(orgId), currency = Currency.RUB)

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            val balance = orgBalances.current()
                            balance.replenish(Money(0, Currency.RUB), "Нулевое пополнение")
                        }
                    }
                }

            assertIs<arrow.core.Either.Left<*>>(result)
        }

    @Test
    fun `replenish с отрицательной суммой возвращает ошибку`() =
        runTest {
            val orgId = insertOrg()
            val ctx = systemContext(OrgId(orgId), currency = Currency.RUB)

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx, this) {
                            val balance = orgBalances.current()
                            balance.replenish(Money(-100_00, Currency.RUB), "Отрицательное пополнение")
                        }
                    }
                }

            assertIs<arrow.core.Either.Left<*>>(result)
        }

    // ── Хелперы ──────────────────────────────────────────────────────────────

    private suspend fun insertOrg(name: String = "Тестовая организация"): Uuid {
        val orgId = Uuid.generateV7()
        TestPostgres.db
            .sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
            .bind("id", orgId)
            .bind("name", name)
            .execute()
        return orgId
    }
}
