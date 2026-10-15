package org.athletica.crm.read.messaging

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.TestPostgres
import org.athletica.crm.api.schemas.messaging.ChannelProviderSchema
import org.athletica.crm.api.schemas.messaging.ClientChannelSchema
import org.athletica.crm.api.schemas.messaging.ClientChannelsResponse
import org.athletica.crm.core.Lang
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.ChannelBindingId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.money.Currency
import org.athletica.crm.domain.employees.EmployeePermission
import org.athletica.crm.employeeContext
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Тесты проекции каналов клиента: доступность по способу адресации, скоуп организации, отсутствие выключенных. */
class DbClientChannelsViewTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val clientId = ClientId.new()

    private val view = DbClientChannelsView()

    private val ctx =
        employeeContext(
            lang = Lang.RU,
            orgId = orgId,
            currency = Currency.RUB,
            userId = UserId.new(),
            branchId = BranchId.new(),
            employeeId = EmployeeId.new(),
            username = "test@example.com",
            clientIp = null,
            permissions = EmployeePermission(),
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            listOf(orgId, otherOrgId).forEach { org ->
                TestPostgres.db.sql("INSERT INTO organizations (id, name) VALUES (:id, :name)")
                    .bind("id", org).bind("name", "Org").execute()
            }
            TestPostgres.db.sql("INSERT INTO clients (id, org_id, name, gender) VALUES (:id, :orgId, :name, 'MALE'::gender)")
                .bind("id", clientId).bind("orgId", orgId).bind("name", "Клиент").execute()
        }
    }

    /** Вставляет интеграцию [name] с настройками [config] в организацию [org]. */
    private suspend fun insertIntegration(
        name: String,
        config: String,
        enabled: Boolean = true,
        org: OrgId = orgId,
    ): ChannelIntegrationId {
        val id = ChannelIntegrationId.new()
        TestPostgres.db.sql(
            """
            INSERT INTO channel_integrations (id, org_id, name, config, enabled)
            VALUES (:id, :orgId, :name, :config::jsonb, :enabled)
            """.trimIndent(),
        ).bind("id", id).bind("orgId", org).bind("name", name).bind("config", config).bind("enabled", enabled).execute()
        return id
    }

    /** Добавляет клиенту контакт типа [type] со значением [value]. */
    private suspend fun insertContact(type: String, value: String) {
        TestPostgres.db.sql(
            """
            INSERT INTO client_contacts (id, org_id, client_id, type, value)
            VALUES (:id, :orgId, :clientId, :type, :value)
            """.trimIndent(),
        ).bind("id", ClientContactId.new()).bind("orgId", orgId).bind("clientId", clientId)
            .bind("type", type).bind("value", value).execute()
    }

    /** Связывает клиента с каналом [integrationId] по адресу [address]. */
    private suspend fun insertBinding(integrationId: ChannelIntegrationId, address: String) {
        TestPostgres.db.sql(
            """
            INSERT INTO channel_bindings (id, org_id, integration_id, client_id, address)
            VALUES (:id, :orgId, :integrationId, :clientId, :address)
            """.trimIndent(),
        ).bind("id", ChannelBindingId.new()).bind("orgId", orgId).bind("integrationId", integrationId)
            .bind("clientId", clientId).bind("address", address).execute()
    }

    /** Каналы клиента через проекцию. */
    private suspend fun channels(): List<ClientChannelSchema> {
        val result = either { TestPostgres.db.transaction { context(ctx) { view.list(clientId) } } }
        return assertIs<Either.Right<ClientChannelsResponse>>(result).value.channels
    }

    @Test
    fun `SMS доступен при наличии телефона и предлагает только телефоны`() =
        runTest {
            insertIntegration("SMS", SMS_CONFIG)
            insertContact("PHONE", "+79990001122")
            insertContact("TELEGRAM", "@client")

            val sms = channels().single()

            assertTrue(sms.available)
            assertEquals(ChannelProviderSchema.SMSC_SMS, sms.provider)
            assertEquals(listOf("+79990001122"), sms.contacts.map { it.value })
        }

    @Test
    fun `SMS без телефона недоступен`() =
        runTest {
            insertIntegration("SMS", SMS_CONFIG)

            assertFalse(channels().single().available)
        }

    @Test
    fun `Telegram без связки недоступен, даже если есть контакт-юзернейм`() =
        runTest {
            insertIntegration("Бот", TELEGRAM_CONFIG)
            insertContact("TELEGRAM", "@client")

            val telegram = channels().single()

            assertFalse(telegram.available)
            assertTrue(telegram.contacts.isEmpty())
        }

    @Test
    fun `Telegram со связкой доступен без выбора контакта`() =
        runTest {
            val telegramId = insertIntegration("Бот", TELEGRAM_CONFIG)
            insertBinding(telegramId, "chat-1")

            val telegram = channels().single()

            assertTrue(telegram.available)
            assertTrue(telegram.contacts.isEmpty())
        }

    @Test
    fun `выключенные и чужие каналы не попадают в список`() =
        runTest {
            insertIntegration("Выключенный", SMS_CONFIG, enabled = false)
            insertIntegration("Чужой", SMS_CONFIG, org = otherOrgId)
            insertIntegration("Рабочий", SMS_CONFIG)

            assertEquals(listOf("Рабочий"), channels().map { it.name })
        }

    private companion object {
        const val SMS_CONFIG = """{"type":"smsc_sms","login":"l","password":"p"}"""
        const val TELEGRAM_CONFIG = """{"type":"telegram_bot","botToken":"tok"}"""
    }
}
