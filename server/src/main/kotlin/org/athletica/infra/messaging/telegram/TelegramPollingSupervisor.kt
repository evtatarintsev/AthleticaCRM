package org.athletica.infra.messaging.telegram

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Создаёт поллер для интеграции. */
fun interface TelegramPollerFactory {
    /** Поллер интеграции [integration]. */
    fun poller(integration: PolledIntegration): PollingJob
}

/**
 * Супервизор поллеров Telegram: каждые [checkEvery] сверяет включённые Telegram-интеграции всех
 * организаций с запущенными поллерами. Новые и включённые — запускает, отключённые, удалённые и со
 * сменившимся токеном — останавливает. Поэтому изменения настроек подхватываются без рестарта и на
 * любом экземпляре сервера; одновременный опрос одного бота исключает аренда в [TelegramPollState].
 */
class TelegramPollingSupervisor(
    private val db: Database,
    private val factory: TelegramPollerFactory,
    private val checkEvery: Duration = 10.seconds,
) {
    private val logger = LoggerFactory.getLogger(TelegramPollingSupervisor::class.java)

    /** Запущенный поллер интеграции. */
    private class Running(val integration: PolledIntegration, val job: Job)

    /** Запущенные поллеры по интеграциям; меняется только из корутины супервизора. */
    private val running = mutableMapOf<ChannelIntegrationId, Running>()

    /** Запускает бесконечный цикл сверки. Поллеры живут в дочерних корутинах и отменяются вместе с ним. */
    suspend fun run() {
        coroutineScope {
            while (isActive) {
                try {
                    reconcile(this)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Ошибка сверки поллеров Telegram", e)
                }
                delay(checkEvery)
            }
        }
    }

    /** Одна сверка: поллеры запускаются в [scope]. Выделено для прямого вызова в тестах. */
    suspend fun reconcile(scope: CoroutineScope) {
        val desired = enabledIntegrations().associateBy { it.id }
        val stale = running.filter { (id, run) -> desired[id] != run.integration || !run.job.isActive }
        stale.forEach { (id, run) ->
            run.job.cancel()
            running.remove(id)
        }
        desired.values
            .filter { it.id !in running }
            .forEach { integration ->
                val poller = factory.poller(integration)
                running[integration.id] = Running(integration, scope.launch { poller.run() })
            }
    }

    /** Включённые Telegram-интеграции всех организаций — системный запрос без org-скоупа. */
    private suspend fun enabledIntegrations(): List<PolledIntegration> =
        db.transaction {
            sql(
                """
                SELECT id, org_id, config ->> 'botToken' AS token
                FROM channel_integrations
                WHERE enabled AND config ->> 'type' = 'telegram_bot' AND COALESCE(config ->> 'botToken', '') <> ''
                ORDER BY created_at
                """.trimIndent(),
            ).list { row ->
                PolledIntegration(
                    id = row.asUuid("id").toChannelIntegrationId(),
                    orgId = row.asUuid("org_id").toOrgId(),
                    token = row.asString("token"),
                )
            }
        }
}
