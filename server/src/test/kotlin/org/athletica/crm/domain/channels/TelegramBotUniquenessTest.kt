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
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.clientcontacts.DbClientContacts
import org.athletica.crm.employeeContext
import org.athletica.infra.messaging.ProviderChannelTransports
import org.athletica.infra.messaging.telegram.telegramTestApi
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты правила «один бот — одна интеграция» в [DbChannelIntegrations]. */
class TelegramBotUniquenessTest {
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

    private suspend fun add(org: OrgId, id: ChannelIntegrationId, token: String): Either<DomainError, Unit> =
        either {
            TestPostgres.db.transaction {
                context(employeeContext(orgId = org)) {
                    integrations.add(id, "Бот", ChannelConfig.TelegramBot(botToken = token, botUsername = "club_bot"))
                }
            }
        }

    @Test
    fun `вторая интеграция с тем же токеном в другой организации отклоняется`() =
        runTest {
            assertIs<Either.Right<Unit>>(add(orgId, ChannelIntegrationId.new(), "123:abc"))

            val result = add(otherOrgId, ChannelIntegrationId.new(), "123:abc")

            assertEquals("TELEGRAM_BOT_IN_USE", assertIs<Either.Left<DomainError>>(result).value.code)
        }

    @Test
    fun `сохранение интеграции с её же токеном проходит`() =
        runTest {
            val id = ChannelIntegrationId.new()
            add(orgId, id, "123:abc")

            val result =
                either {
                    TestPostgres.db.transaction {
                        context(employeeContext(orgId = orgId)) {
                            integrations.update(id, "Бот клуба", ChannelConfig.TelegramBot(botToken = "123:abc"), enabled = false)
                        }
                    }
                }

            assertIs<Either.Right<Unit>>(result)
        }
}
