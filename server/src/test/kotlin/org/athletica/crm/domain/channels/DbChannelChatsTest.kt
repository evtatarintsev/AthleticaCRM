package org.athletica.crm.domain.channels

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.SystemContexts
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты хранилища чатов связанных каналов [DbChannelChats]. */
class DbChannelChatsTest {
    private val orgId = OrgId.new()
    private val integrationId = ChannelIntegrationId.new()
    private val chats = DbChannelChats()

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertIntegration(orgId, id = integrationId)
        }
    }

    private suspend fun upsert(name: String, username: String?, phone: String?): ChannelChat {
        val result: Either<DomainError, ChannelChat> =
            either {
                TestPostgres.db.transaction {
                    context(SystemContexts.forOrg(orgId)) {
                        chats.upsert(integrationId, "100", name, username, phone)
                    }
                }
            }
        return assertIs<Either.Right<ChannelChat>>(result).value
    }

    @Test
    fun `повторный upsert обновляет имя и не затирает телефон пустым значением`() =
        runTest {
            upsert("Пётр", "petr", "+79001234567")

            val updated = upsert("Пётр Иванов", null, null)

            assertEquals("Пётр Иванов", updated.displayName)
            assertEquals(null, updated.username)
            assertEquals("+79001234567", updated.phone)
        }
}
