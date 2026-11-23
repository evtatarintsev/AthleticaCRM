package org.athletica.crm.domain.telegramgateway

import arrow.core.Either
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Currency
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.domain.audit.AuditEvent
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.Transaction
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** Тесты интеграции с Telegram Gateway [DbTelegramGatewayIntegrations] и её аудита. */
class TelegramGatewayIntegrationsTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val events = mutableListOf<AuditEvent>()
    private val integrations =
        AuditTelegramGatewayIntegrations(
            DbTelegramGatewayIntegrations(),
            object : AuditLog {
                context(tr: Transaction, raise: Raise<AppError>)
                override suspend fun log(event: AuditEvent) {
                    events += event
                }
            },
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertOrg(otherOrgId, "Other")
        }
    }

    /** Выполняет [block] в транзакции от имени сотрудника организации [org]. */
    private suspend fun <T> asOrg(org: OrgId, block: suspend context(EmployeeRequestContext, Transaction, Raise<AppError>) () -> T): Either<AppError, T> = either { TestPostgres.db.transaction { context(employeeContext(orgId = org)) { block() } } }

    @Test
    fun `подключение включает интеграцию, замена токена не меняет включённость`() =
        runTest {
            asOrg(orgId) { integrations.saveToken("first-token-123") }
            asOrg(orgId) { integrations.setEnabled(false) }
            asOrg(orgId) { integrations.saveToken("second-token-456") }

            val current = assertIs<Either.Right<TelegramGatewayIntegration?>>(asOrg(orgId) { integrations.current() }).value
            assertEquals(TelegramGatewayIntegration("second-token-456", enabled = false, balance = null), current)
        }

    @Test
    fun `вторая организация не видит и не меняет чужую интеграцию`() =
        runTest {
            asOrg(orgId) { integrations.saveToken("first-token-123") }

            assertNull(assertIs<Either.Right<TelegramGatewayIntegration?>>(asOrg(otherOrgId) { integrations.current() }).value)
            val disabled = asOrg(otherOrgId) { integrations.setEnabled(false) }
            assertEquals("TELEGRAM_GATEWAY_NOT_CONNECTED", assertIs<Either.Left<AppError>>(disabled).value.code)

            val own = assertIs<Either.Right<TelegramGatewayIntegration?>>(asOrg(orgId) { integrations.current() }).value
            assertEquals(true, own?.enabled)
            assertEquals("first-token-123", own?.token)
        }

    @Test
    fun `остаток записывается вместе со временем ответа`() =
        runTest {
            val at = Instant.parse("2026-11-22T10:15:30Z")
            asOrg(orgId) { integrations.saveToken("first-token-123") }
            asOrg(orgId) { integrations.recordBalance(GatewayBalance(Money(427, Currency.USD), at)) }

            val current = assertIs<Either.Right<TelegramGatewayIntegration?>>(asOrg(orgId) { integrations.current() }).value
            assertEquals(GatewayBalance(Money(427, Currency.USD), at), current?.balance)
        }

    @Test
    fun `аудит подключения, замены и включённости без токена`() =
        runTest {
            asOrg(orgId) { integrations.saveToken("first-token-123") }
            asOrg(orgId) { integrations.saveToken("second-token-456") }
            asOrg(orgId) { integrations.setEnabled(false) }

            assertEquals(listOf(AuditActionType.CREATE, AuditActionType.UPDATE, AuditActionType.UPDATE), events.map { it.actionType })
            assertEquals(listOf(orgId.value), events.map { it.entityId }.distinct())
            events.forEach {
                assertFalse(it.toString().contains("token-123") || it.toString().contains("token-456"), it.toString())
            }
        }
}
