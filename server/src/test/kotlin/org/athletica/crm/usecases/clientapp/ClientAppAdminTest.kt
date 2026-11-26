package org.athletica.crm.usecases.clientapp

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.clientappadmin.ClientAppInvitePreviewResponse
import org.athletica.crm.clientapp.ClientAppFixture
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.clientapp.ClientAppAccessStatus
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.domain.audit.PostgresAuditLog
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.ChannelTransports
import org.athletica.crm.domain.channels.DbChannelBindings
import org.athletica.crm.domain.channels.DbChannelIntegrations
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.domain.clientapp.AuditClientAppSettings
import org.athletica.crm.domain.clientapp.ClientAppConfig
import org.athletica.crm.domain.clientapp.DbClientAppAccesses
import org.athletica.crm.domain.clientapp.DbClientAppSettings
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.athletica.crm.domain.conversations.DbConversations
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.domain.messagedelivery.DbDeliveries
import org.athletica.crm.domain.org.DbOrganizations
import org.athletica.crm.domain.telegramgateway.DbTelegramGatewayIntegrations
import org.athletica.crm.employeeContext
import org.athletica.crm.read.clientappadmin.DbClientAppInvitePreviewView
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/** Тесты управления кабинетом из CRM ([ClientAppAdmin]) и подготовки массового приглашения. */
class ClientAppAdminTest {
    private val mom = "+7 903 123-45-67"
    private val dad = "+7 903 765-43-21"
    private val accesses = DbClientAppAccesses()
    private val admin =
        ClientAppAdmin(
            AuditClientAppSettings(DbClientAppSettings(), PostgresAuditLog()),
            accesses,
            DbTelegramGatewayIntegrations(),
            DbChannelIntegrations(
                object : ChannelTransports {
                    override fun transport(config: ChannelConfig): ChannelTransport =
                        object : ChannelTransport {
                            override suspend fun send(address: String, body: String): SendResult = SendResult.Sent("ref")
                        }
                },
                DbClientContacts(),
                DbChannelBindings(),
            ),
            DbConversations(),
            DbDeliveries(),
            DbOrganizations(),
            ClientAppConfig("https://clients.example.com", "v1", secureCookies = true),
            PostgresAuditLog(),
        )

    private val userId = UserId.new()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        orThrow {
            runBlocking {
                TestPostgres.db.sql("INSERT INTO users (id, login, password_hash) VALUES (:id, 'admin@example.com', 'h')").bind("id", userId).execute()
            }
        }
    }

    /** Контекст сотрудника организации [orgId]; [manager] — с правом `CAN_MANAGE_CLIENT_APP`. */
    private fun employee(
        orgId: OrgId,
        manager: Boolean = true,
    ): EmployeeRequestContext =
        employeeContext(
            orgId = orgId,
            userId = userId,
            permissions = EmployeePermission(emptyList(), if (manager) setOf(UserPermission.CAN_MANAGE_CLIENT_APP) else emptySet(), emptySet()),
        )

    /** Выполняет [block] в транзакции от имени сотрудника [ctx]. */
    private suspend fun <T> run(
        ctx: EmployeeRequestContext,
        block: suspend context(EmployeeRequestContext, Transaction, Raise<AppError>) () -> T,
    ): Either<AppError, T> = either { TestPostgres.db.transaction { context(ctx) { block() } } }

    /** Статусы доступов клиента [clientId] по номерам. */
    private suspend fun statuses(clientId: ClientId): Map<String, String> =
        orThrow {
            TestPostgres.db.sql("SELECT phone_digits, status FROM client_app_access WHERE client_id = :id").bind("id", clientId)
                .list { it.asString("phone_digits") to it.asString("status") }.toMap()
        }

    /** Число доставок организации [orgId]. */
    private suspend fun deliveries(orgId: OrgId): Int = orThrow { checkNotNull(TestPostgres.db.sql("SELECT count(*)::int AS n FROM message_deliveries WHERE org_id = :id").bind("id", orgId).firstOrNull { it.asInt("n") }) }

    /** Текст уведомлений организации [orgId]. */
    private suspend fun bodies(orgId: OrgId): List<String> = orThrow { TestPostgres.db.sql("SELECT body FROM messages WHERE org_id = :id").bind("id", orgId).list { it.asString("body") } }

    /** Добавляет клиенту [clientId] организации [orgId] телефон [phone] в позиции [position]. */
    private suspend fun addPhone(
        orgId: OrgId,
        clientId: ClientId,
        phone: String,
        position: Int = 1,
    ) = orThrow {
        TestPostgres.db.sql("INSERT INTO client_contacts (id, org_id, client_id, type, value, position, is_primary) VALUES (:cid, :orgId, :id, 'PHONE', :phone, :pos, false)")
            .bind("cid", Uuid.generateV7()).bind("orgId", orgId).bind("id", clientId).bind("phone", phone).bind("pos", position).execute()
    }

    /** Клиент организации [orgId] без контактов. */
    private suspend fun clientWithoutPhone(orgId: OrgId): ClientId =
        orThrow {
            val id = ClientId.new()
            TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, 'Без телефона', 'MALE'::gender)")
                .bind("id", id).bind("orgId", orgId).execute()
            id
        }

    @Test
    fun `включение без права — PERMISSION_DENIED`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", cabinet = false, sms = true) }

            val result = run(employee(org, manager = false)) { admin.setEnabled(true) }

            assertEquals("PERMISSION_DENIED", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `нет способа доставки — CLIENT_APP_NO_CODE_TRANSPORT`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", cabinet = false) }

            val result = run(employee(org)) { admin.setEnabled(true) }

            assertEquals("CLIENT_APP_NO_CODE_TRANSPORT", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `только SMS — кабинет включается, изменение в аудите`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", cabinet = false, sms = true) }

            run(employee(org)) { admin.setEnabled(true) }.also { assertIs<Either.Right<Unit>>(it) }

            assertTrue(assertIs<Either.Right<Boolean>>(run(employee(org)) { DbClientAppSettings().enabled() }).value)
            val audited = orThrow { TestPostgres.db.sql("SELECT count(*)::int AS n FROM audit_logs WHERE org_id = :id AND entity_type = 'client_app_settings'").bind("id", org).firstOrNull { it.asInt("n") } }
            assertEquals(1, audited)
        }

    @Test
    fun `только Telegram Gateway — кабинет включается`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", cabinet = false, gateway = true) }

            assertIs<Either.Right<Unit>>(run(employee(org)) { admin.setEnabled(true) })
        }

    @Test
    fun `пригласить клиента с двумя телефонами — оба приглашены, уведомление на оба без данных клиента`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", sms = true) }
            val ivan = orThrow { ClientAppFixture.client(org, mom, status = null, name = "Иван Петров") }
            addPhone(org, ivan, dad)

            val notified = run(employee(org, manager = false)) { admin.invite(ivan) }

            assertTrue(assertIs<Either.Right<Boolean>>(notified).value)
            assertEquals(mapOf("79031234567" to "INVITED", "79037654321" to "INVITED"), statuses(ivan))
            assertEquals(2, deliveries(org))
            val body = bodies(org).single()
            assertTrue("Динамо" in body && "https://clients.example.com" in body)
            assertFalse("Иван" in body)
        }

    @Test
    fun `нет канала — приглашение действует, уведомление не ушло`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", gateway = true) }
            val ivan = orThrow { ClientAppFixture.client(org, mom, status = null) }

            val notified = run(employee(org)) { admin.invite(ivan) }

            assertFalse(assertIs<Either.Right<Boolean>>(notified).value)
            assertEquals(mapOf("79031234567" to "INVITED"), statuses(ivan))
            assertEquals(0, deliveries(org))
        }

    @Test
    fun `кабинет выключен — CLIENT_APP_DISABLED`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", cabinet = false, sms = true) }
            val ivan = orThrow { ClientAppFixture.client(org, mom, status = null) }

            val result = run(employee(org)) { admin.invite(ivan) }

            assertEquals("CLIENT_APP_DISABLED", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `из карточки приглашаются и номера «это не я» и «отключён»`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", sms = true) }
            val ivan = orThrow { ClientAppFixture.client(org, mom, status = ClientAppAccessStatus.NOT_ME) }
            val maria = orThrow { ClientAppFixture.client(org, dad, status = ClientAppAccessStatus.DISABLED) }

            run(employee(org)) { admin.invite(ivan) }
            run(employee(org)) { admin.invite(maria) }

            assertEquals("INVITED", statuses(ivan).values.single())
            assertEquals("INVITED", statuses(maria).values.single())
        }

    @Test
    fun `массовое приглашение — подсчёт, повторный запуск, «это не я» и «отключён» не затронуты`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", sms = true) }
            val ready = orThrow { listOf(ClientAppFixture.client(org, mom, status = null), ClientAppFixture.client(org, dad, status = null)) }
            val notMe = orThrow { ClientAppFixture.client(org, "+7 900 000-00-01", status = ClientAppAccessStatus.NOT_ME) }
            val disabled = orThrow { ClientAppFixture.client(org, "+7 900 000-00-02", status = ClientAppAccessStatus.DISABLED) }
            clientWithoutPhone(org)
            val ctx = employee(org)
            val preview = DbClientAppInvitePreviewView()

            val before = assertIs<Either.Right<*>>(run(ctx) { preview.preview(null) }).value
            val first = assertIs<Either.Right<BulkInvite>>(run(ctx) { admin.bulkInvite(null) }).value
            val second = assertIs<Either.Right<BulkInvite>>(run(ctx) { admin.bulkInvite(null) }).value
            val after = assertIs<Either.Right<*>>(run(ctx) { preview.preview(null) }).value

            assertEquals(ClientAppInvitePreviewResponse(ready = 2, withoutPhone = 1), before)
            assertEquals(BulkInvite(2, true), first)
            assertEquals(BulkInvite(0, true), second)
            assertEquals(ClientAppInvitePreviewResponse(ready = 0, withoutPhone = 1), after)
            ready.forEach { assertEquals("INVITED", statuses(it).values.single()) }
            assertEquals("NOT_ME", statuses(notMe).values.single())
            assertEquals("DISABLED", statuses(disabled).values.single())
            assertEquals(2, deliveries(org))
        }

    @Test
    fun `массовое приглашение без права — PERMISSION_DENIED`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", sms = true) }

            val result = run(employee(org, manager = false)) { admin.bulkInvite(null) }

            assertEquals("PERMISSION_DENIED", assertIs<Either.Left<AppError>>(result).value.code)
        }

    @Test
    fun `напомнить второй раз за сутки нельзя, через сутки — можно`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", sms = true) }
            val yesterday = orThrow { ClientAppFixture.client(org, mom, invitedAt = Clock.System.now() - 2.days) }
            val phone = checkNotNull(PhoneDigits.from(mom))

            val first = run(employee(org)) { admin.remind(yesterday, phone) }
            val second = run(employee(org)) { admin.remind(yesterday, phone) }

            assertTrue(assertIs<Either.Right<Boolean>>(first).value)
            assertEquals("CLIENT_APP_REMIND_TOO_SOON", assertIs<Either.Left<AppError>>(second).value.code)
            assertEquals(1, deliveries(org))
            val audited = orThrow { TestPostgres.db.sql("SELECT count(*)::int AS n FROM audit_logs WHERE org_id = :id AND entity_type = 'client_app_access'").bind("id", org).firstOrNull { it.asInt("n") } }
            assertEquals(1, audited)
        }

    @Test
    fun `отключение в одной организации не трогает профили другой и сеансы аккаунта`() =
        runTest {
            val dynamo = orThrow { ClientAppFixture.org("Динамо", sms = true) }
            val volna = orThrow { ClientAppFixture.org("Волна", sms = true) }
            val dynamoIvan = orThrow { ClientAppFixture.client(dynamo, mom, ClientAppAccessStatus.CONNECTED) }
            val volnaIvan = orThrow { ClientAppFixture.client(volna, mom, ClientAppAccessStatus.CONNECTED) }
            val account = orThrow { ClientAppFixture.account(mom) }
            orThrow {
                TestPostgres.db.sql(
                    """
                    INSERT INTO client_app_sessions (id, account_id, platform, created_at, last_seen_at, refresh_jti, rotated_at)
                    VALUES (:id, :account, 'WEB', now(), now(), :jti, now())
                    """.trimIndent(),
                ).bind("id", Uuid.generateV7()).bind("account", account).bind("jti", Uuid.generateV7()).execute()
            }

            assertIs<Either.Right<Unit>>(run(employee(dynamo, manager = false)) { admin.disable(dynamoIvan, checkNotNull(PhoneDigits.from(mom))) })

            assertEquals("DISABLED", statuses(dynamoIvan).values.single())
            assertEquals("CONNECTED", statuses(volnaIvan).values.single())
            val sessions = orThrow { TestPostgres.db.sql("SELECT count(*)::int AS n FROM client_app_sessions WHERE account_id = :id").bind("id", account).firstOrNull { it.asInt("n") } }
            assertEquals(1, sessions)
        }

    @Test
    fun `отключить номер «это не я» нельзя`() =
        runTest {
            val org = orThrow { ClientAppFixture.org("Динамо", sms = true) }
            val ivan = orThrow { ClientAppFixture.client(org, mom, ClientAppAccessStatus.NOT_ME) }

            val result = run(employee(org)) { admin.disable(ivan, checkNotNull(PhoneDigits.from(mom))) }

            assertEquals("CLIENT_APP_ACCESS_CHANGED", assertIs<Either.Left<AppError>>(result).value.code)
        }
}
