package org.athletica.infra.messaging.telegram

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.OrgId
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Тесты аренды опроса и offset [TelegramPollState]. */
class TelegramPollStateTest {
    private val orgId = OrgId.new()
    private val integrationId = ChannelIntegrationId.new()
    private val state = TelegramPollState(leaseFor = 60.seconds)

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking {
            MessagingFixture.insertOrg(orgId)
            MessagingFixture.insertIntegration(orgId, id = integrationId)
        }
    }

    private suspend fun acquire(owner: String, botId: String = "1"): Long? = TestPostgres.db.transaction { state.acquire(integrationId, owner, botId) }

    private suspend fun expireLease() {
        TestPostgres.db.sql("UPDATE telegram_poll_state SET lease_until = now() - interval '1 second' WHERE integration_id = :id")
            .bind("id", integrationId).execute()
    }

    @Test
    fun `второй владелец не захватывает живую аренду`() =
        runTest {
            assertEquals(0L, acquire("a"))

            assertNull(acquire("b"))
            assertEquals(0L, acquire("a"))
        }

    @Test
    fun `истёкшую аренду захватывает другой владелец с сохранённым offset`() =
        runTest {
            acquire("a")
            TestPostgres.db.transaction { state.advance(integrationId, "a", 42) }
            expireLease()

            assertEquals(42L, acquire("b"))
        }

    @Test
    fun `после перехвата аренды прежний владелец её не держит и offset не сдвигает`() =
        runTest {
            acquire("a")
            expireLease()
            acquire("b")

            assertFalse(TestPostgres.db.transaction { state.holds(integrationId, "a") })
            TestPostgres.db.transaction { state.advance(integrationId, "a", 100) }

            assertTrue(TestPostgres.db.transaction { state.holds(integrationId, "b") })
            assertEquals(0L, acquire("b"))
        }

    @Test
    fun `смена бота сбрасывает offset`() =
        runTest {
            acquire("a", botId = "1")
            TestPostgres.db.transaction { state.advance(integrationId, "a", 42) }

            assertEquals(0L, acquire("a", botId = "2"))
        }
}
