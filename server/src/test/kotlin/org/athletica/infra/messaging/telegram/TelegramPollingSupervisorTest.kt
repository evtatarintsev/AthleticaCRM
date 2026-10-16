package org.athletica.infra.messaging.telegram

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.athletica.crm.MessagingFixture
import org.athletica.crm.TestPostgres
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.OrgId
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты супервизора поллеров [TelegramPollingSupervisor] с фейковой фабрикой. */
class TelegramPollingSupervisorTest {
    private val orgId = OrgId.new()

    /** Запущенные фейковые поллеры: интеграция и сигнал об их отмене. */
    private val started = mutableListOf<Pair<PolledIntegration, CompletableDeferred<Unit>>>()

    private val supervisor =
        TelegramPollingSupervisor(
            db = TestPostgres.db,
            factory = { integration ->
                val stopped = CompletableDeferred<Unit>()
                started += integration to stopped
                PollingJob {
                    try {
                        awaitCancellation()
                    } finally {
                        stopped.complete(Unit)
                    }
                }
            },
        )

    @Before
    fun setUp() {
        TestPostgres.truncate()
        runBlocking { MessagingFixture.insertOrg(orgId) }
    }

    private suspend fun setEnabled(id: ChannelIntegrationId, enabled: Boolean) {
        TestPostgres.db.sql("UPDATE channel_integrations SET enabled = :enabled WHERE id = :id").bind("enabled", enabled).bind("id", id).execute()
    }

    private suspend fun stopped(index: Int) = withTimeout(5_000) { started[index].second.await() }

    @Test
    fun `запускает новые и включённые, останавливает отключённые, удалённые и со сменой токена`() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            try {
                val a = MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig("1:a"))
                val b = MessagingFixture.insertIntegration(orgId, MessagingFixture.telegramConfig("2:b"), enabled = false)
                MessagingFixture.insertIntegration(orgId, """{"type":"smsc_sms","login":"l","password":"p"}""")

                supervisor.reconcile(scope)
                assertEquals(listOf(a), started.map { it.first.id })

                setEnabled(b, true)
                supervisor.reconcile(scope)
                assertEquals(listOf(a, b), started.map { it.first.id })

                setEnabled(a, false)
                supervisor.reconcile(scope)
                stopped(0)

                TestPostgres.db.sql("UPDATE channel_integrations SET config = :config::jsonb WHERE id = :id")
                    .bind("config", MessagingFixture.telegramConfig("3:new")).bind("id", b).execute()
                supervisor.reconcile(scope)
                stopped(1)
                assertEquals("3:new", started[2].first.token)

                TestPostgres.db.sql("DELETE FROM channel_integrations WHERE id = :id").bind("id", b).execute()
                supervisor.reconcile(scope)
                stopped(2)

                supervisor.reconcile(scope)
                assertEquals(3, started.size)
            } finally {
                scope.cancel()
            }
        }
}
