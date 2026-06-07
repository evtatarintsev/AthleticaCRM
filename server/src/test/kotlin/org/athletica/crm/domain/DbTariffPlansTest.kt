package org.athletica.crm.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.tariffs.DbTariffPlans
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DbTariffPlansTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()

    private val ctx =
        EmployeeRequestContext(
            lang = Lang.EN,
            orgId = orgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "user@example.com",
            clientIp = "127.0.0.1",
            permission = EmployeePermission(),
        )
    private val otherCtx =
        EmployeeRequestContext(
            lang = Lang.EN,
            orgId = otherOrgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "user@example.com",
            clientIp = "127.0.0.1",
            permission = EmployeePermission(),
        )

    private lateinit var tariffs: DbTariffPlans

    /** Создаёт и сохраняет тариф с параметрами по умолчанию, возвращая его идентификатор. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun saveNewPlan(
        id: TariffPlanId = TariffPlanId.new(),
        name: String = "8 занятий",
        sessions: Int? = 8,
        durationValue: Int = 1,
        durationUnit: DurationUnit = DurationUnit.MONTHS,
        price: Money = Money(4_000_00, Currency.RUB),
    ): TariffPlanId {
        tariffs.new(id, name, sessions, durationValue, durationUnit, price).save()
        return id
    }

    @Before
    fun setUp() {
        TestPostgres.truncate()
        tariffs = DbTariffPlans()
        runBlocking {
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", orgId).bind("name", "Org 1").execute()
            TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                .bind("id", otherOrgId).bind("name", "Org 2").execute()
        }
    }

    @Test
    fun `save сохраняет тариф со всеми полями`() =
        runTest {
            val id = TariffPlanId.new()
            either {
                TestPostgres.db.transaction {
                    context(ctx) {
                        saveNewPlan(id = id, name = "Безлимит", sessions = null, durationValue = 12, price = Money(60_000_00, Currency.RUB))
                    }
                }
                val list = TestPostgres.db.transaction { context(ctx) { tariffs.list(includeArchived = false) } }
                assertEquals(1, list.size)
                val actual = list.first()
                assertEquals(id, actual.id)
                assertEquals("Безлимит", actual.name)
                assertNull(actual.sessions)
                assertEquals(12, actual.durationValue)
                assertEquals(DurationUnit.MONTHS, actual.durationUnit)
                assertEquals(Money(60_000_00, Currency.RUB), actual.price)
                assertTrue(!actual.archived)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `list возвращает только тарифы своей организации`() =
        runTest {
            either {
                TestPostgres.db.transaction { context(ctx) { saveNewPlan(name = "Свой") } }
                TestPostgres.db.transaction { context(otherCtx) { saveNewPlan(name = "Чужой") } }
                val list = TestPostgres.db.transaction { context(ctx) { tariffs.list(includeArchived = false) } }
                assertEquals(listOf("Свой"), list.map { it.name })
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `withNew + save изменяет поля тарифа`() =
        runTest {
            val id = TariffPlanId.new()
            either {
                TestPostgres.db.transaction { context(ctx) { saveNewPlan(id = id, name = "Старое", price = Money(1_000_00, Currency.RUB)) } }
                TestPostgres.db.transaction {
                    context(ctx) {
                        tariffs.byId(id)
                            .withNew(
                                name = "Новое",
                                sessions = 12,
                                durationValue = 2,
                                durationUnit = DurationUnit.MONTHS,
                                price = Money(5_500_00, Currency.RUB),
                            ).save()
                    }
                }
                val actual = TestPostgres.db.transaction { context(ctx) { tariffs.list(includeArchived = false) } }.first()
                assertEquals("Новое", actual.name)
                assertEquals(12, actual.sessions)
                assertEquals(2, actual.durationValue)
                assertEquals(Money(5_500_00, Currency.RUB), actual.price)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `byId возвращает TARIFF_NOT_FOUND для неизвестного id`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction {
                        context(ctx) {
                            tariffs.byId(TariffPlanId.new())
                                .withNew(
                                    name = "Новое",
                                    sessions = 8,
                                    durationValue = 1,
                                    durationUnit = DurationUnit.MONTHS,
                                    price = Money(4_000_00, Currency.RUB),
                                ).save()
                        }
                    }
                }
            assertEquals("TARIFF_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `archive скрывает тариф из активного списка но оставляет при includeArchived`() =
        runTest {
            val id = TariffPlanId.new()
            either {
                TestPostgres.db.transaction { context(ctx) { saveNewPlan(id = id) } }
                TestPostgres.db.transaction { context(ctx) { tariffs.byId(id).archive() } }

                val active = TestPostgres.db.transaction { context(ctx) { tariffs.list(includeArchived = false) } }
                assertTrue(active.isEmpty())

                val all = TestPostgres.db.transaction { context(ctx) { tariffs.list(includeArchived = true) } }
                assertEquals(1, all.size)
                assertTrue(all.first().archived)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `unarchive восстанавливает тариф`() =
        runTest {
            val id = TariffPlanId.new()
            either {
                TestPostgres.db.transaction { context(ctx) { saveNewPlan(id = id) } }
                TestPostgres.db.transaction { context(ctx) { tariffs.byId(id).archive() } }
                TestPostgres.db.transaction { context(ctx) { tariffs.byId(id).unarchive() } }
                val active = TestPostgres.db.transaction { context(ctx) { tariffs.list(includeArchived = false) } }
                assertEquals(1, active.size)
            }.getOrElse { fail("Unexpected error: $it") }
        }

    @Test
    fun `archive возвращает TARIFF_NOT_FOUND для неизвестного id`() =
        runTest {
            val result =
                either {
                    TestPostgres.db.transaction { context(ctx) { tariffs.byId(TariffPlanId.new()).archive() } }
                }
            assertEquals("TARIFF_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `save сохраняет признак архивности при правке полей`() =
        runTest {
            val id = TariffPlanId.new()
            either {
                TestPostgres.db.transaction { context(ctx) { saveNewPlan(id = id, name = "Старое") } }
                TestPostgres.db.transaction { context(ctx) { tariffs.byId(id).archive() } }
                TestPostgres.db.transaction {
                    context(ctx) {
                        tariffs.byId(id)
                            .withNew(
                                name = "Переименовано",
                                sessions = 8,
                                durationValue = 1,
                                durationUnit = DurationUnit.MONTHS,
                                price = Money(4_000_00, Currency.RUB),
                            ).save()
                    }
                }
                val all = TestPostgres.db.transaction { context(ctx) { tariffs.list(includeArchived = true) } }
                assertEquals(1, all.size)
                assertEquals("Переименовано", all.first().name)
                assertTrue(all.first().archived)
            }.getOrElse { fail("Unexpected error: $it") }
        }
}
