package org.athletica.crm.domain.payment

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.employees.EmployeePermission
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** Интеграционные тесты репозитория [DbPayments]. */
class DbPaymentsTest {
    private val payments = DbPayments()
    private val userId = UserId.new()
    private val employeeId = EmployeeId.new()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            val orgId = insertOrg()
            insertEmployee(orgId, userId, employeeId)
        }
    }

    @Test
    fun `create создаёт транзакцию в статусе PENDING`() =
        runTest {
            val orgId = insertOrg()
            val empId = EmployeeId.new()
            insertEmployee(orgId, userId, empId)
            val ctx = ctx(orgId, empId)

            val payment =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            payments.create(
                                gatewayName = "yookassa",
                                externalPaymentId = "ext_001",
                                amount = Money(100_00, Currency.RUB),
                                description = "Тестовый платёж",
                                confirmationUrl = "https://yookassa.ru/pay/001",
                            )
                        }
                    }
                }

            assertIs<Either.Right<Payment>>(payment)
            val p = payment.value
            assertEquals(PaymentStatus.PENDING, p.status)
            assertNull(p.confirmedAt)
            assertEquals("ext_001", p.externalPaymentId)
            assertEquals("yookassa", p.gatewayName)
            assertEquals(Money(100_00, Currency.RUB), p.amount)
            assertEquals(empId, p.createdBy)
        }

    @Test
    fun `markAsPaid переводит статус в PAID и проставляет confirmedAt`() =
        runTest {
            val orgId = insertOrg()
            val empId = EmployeeId.new()
            insertEmployee(orgId, userId, empId)
            val ctx = ctx(orgId, empId)

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        payments.create(
                            gatewayName = "yookassa",
                            externalPaymentId = "ext_002",
                            amount = Money(50_00, Currency.RUB),
                            description = "Платёж для обновления",
                            confirmationUrl = "https://yookassa.ru/pay/002",
                        )
                    }
                }
            }

            val paid =
                either {
                    TestPostgres.db.transaction {
                        payments.markAsPaid("yookassa", "ext_002")
                    }
                }

            assertIs<Either.Right<Payment>>(paid)
            assertEquals(PaymentStatus.PAID, paid.value.status)
            assertNotNull(paid.value.confirmedAt)
        }

    @Test
    fun `markAsPaid на уже оплаченной транзакции возвращает PaymentAlreadyProcessed`() =
        runTest {
            val orgId = insertOrg()
            val empId = EmployeeId.new()
            insertEmployee(orgId, userId, empId)
            val ctx = ctx(orgId, empId)

            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        payments.create(
                            gatewayName = "yookassa",
                            externalPaymentId = "ext_003",
                            amount = Money(75_00, Currency.RUB),
                            description = "Дублированный платёж",
                            confirmationUrl = "https://yookassa.ru/pay/003",
                        )
                    }
                }
            }

            // Первый вызов — успех
            either {
                TestPostgres.db.transaction {
                    payments.markAsPaid("yookassa", "ext_003")
                }
            }

            // Второй вызов — ошибка: уже обработан
            val result =
                either {
                    TestPostgres.db.transaction {
                        payments.markAsPaid("yookassa", "ext_003")
                    }
                }

            assertIs<Either.Left<PaymentAlreadyProcessed>>(result)
        }

    @Test
    fun `markAsPaid на несуществующей транзакции возвращает PaymentAlreadyProcessed`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        payments.markAsPaid("yookassa", "nonexistent_id")
                    }
                }

            assertIs<Either.Left<PaymentAlreadyProcessed>>(result)
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

    private suspend fun insertEmployee(
        orgId: Uuid,
        userId: UserId,
        employeeId: EmployeeId,
    ) {
        TestPostgres.db
            .sql("INSERT INTO users (id, login, password_hash) VALUES (:id, :login, :hash) ON CONFLICT DO NOTHING")
            .bind("id", userId)
            .bind("login", "${userId.value}@test.com")
            .bind("hash", "hash")
            .execute()
        TestPostgres.db
            .sql("INSERT INTO employees (id, org_id, user_id, name) VALUES (:id, :orgId, :userId, 'Тест')")
            .bind("id", employeeId)
            .bind("orgId", orgId)
            .bind("userId", userId)
            .execute()
    }

    private fun ctx(
        orgId: Uuid,
        empId: EmployeeId = employeeId,
    ) = EmployeeRequestContext(
        lang = Lang.RU,
        userId = userId,
        orgId = OrgId(orgId),
        branchId = BranchId.new(),
        employeeId = empId,
        username = "test@example.com",
        clientIp = "127.0.0.1",
        currency = Currency.RUB,
        permission = EmployeePermission(),
    )
}
