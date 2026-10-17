package org.athletica.crm.domain.channels

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.employeeContext
import org.athletica.crm.storage.asBoolean
import org.athletica.infra.messaging.ProviderChannelTransports
import org.athletica.infra.messaging.telegram.telegramTestApi
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты включения и выключения интеграции [DbChannelIntegrations.setEnabled]. */
class ChannelEnabledTest {
    private val orgId = OrgId.new()
    private val otherOrgId = OrgId.new()
    private val integrations =
        DbChannelIntegrations(ProviderChannelTransports(telegramTestApi { error("Bot API не вызывается") }), DbClientContacts(), DbChannelBindings())

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertOrg(otherOrgId, "Other")
        }
    }

    private suspend fun setEnabled(org: OrgId, id: ChannelIntegrationId, enabled: Boolean): Either<DomainError, Unit> =
        either {
            TestPostgres.db.transaction {
                context(employeeContext(orgId = org)) {
                    integrations.setEnabled(id, enabled)
                }
            }
        }

    private suspend fun enabled(id: ChannelIntegrationId): Boolean =
        TestPostgres.db.sql("SELECT enabled FROM channel_integrations WHERE id = :id")
            .bind("id", id)
            .firstOrNull { row -> row.asBoolean("enabled") } ?: error("интеграция не найдена")

    @Test
    fun `канал выключается и снова включается`() =
        runTest {
            val id = MessagingFixture.insertIntegration(orgId)

            assertIs<Either.Right<Unit>>(setEnabled(orgId, id, enabled = false))
            assertEquals(false, enabled(id))

            assertIs<Either.Right<Unit>>(setEnabled(orgId, id, enabled = true))
            assertEquals(true, enabled(id))
        }

    @Test
    fun `канал другой организации не найден и не меняется`() =
        runTest {
            val id = MessagingFixture.insertIntegration(otherOrgId)

            val result = setEnabled(orgId, id, enabled = false)

            assertEquals("CHANNEL_NOT_FOUND", assertIs<Either.Left<DomainError>>(result).value.code)
            assertEquals(true, enabled(id))
        }
}
