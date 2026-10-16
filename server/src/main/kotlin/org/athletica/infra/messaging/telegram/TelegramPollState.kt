package org.athletica.infra.messaging.telegram

import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import kotlin.time.Duration

/**
 * Аренда опроса и позиция (offset) `getUpdates` по Telegram-интеграции в таблице `telegram_poll_state`.
 * Аренда вместо advisory-lock: она не привязана к соединению пула и сама истекает, если экземпляр
 * сервера пропал, — тогда бота подхватывает другой экземпляр.
 */
class TelegramPollState(
    /** Срок аренды: столько экземпляр может молчать, прежде чем бота заберёт другой. */
    private val leaseFor: Duration,
) {
    /**
     * Захватывает или продлевает аренду интеграции [integrationId] для владельца [owner] и возвращает
     * offset, с которого опрашивать. `null` — аренду держит другой живой владелец.
     * [botId] — числовой id бота из токена: при смене бота offset прежнего бота сбрасывается.
     */
    context(tr: Transaction)
    suspend fun acquire(integrationId: ChannelIntegrationId, owner: String, botId: String): Long? {
        tr.sql(
            """
            INSERT INTO telegram_poll_state (integration_id, bot_id)
            VALUES (:integrationId, :botId)
            ON CONFLICT (integration_id) DO NOTHING
            """.trimIndent(),
        )
            .bind("integrationId", integrationId)
            .bind("botId", botId)
            .execute()
        return tr.sql(
            """
            UPDATE telegram_poll_state
            SET lease_owner = :owner,
                lease_until = now() + make_interval(secs => :leaseSeconds),
                update_offset = CASE WHEN bot_id = :botId THEN update_offset ELSE 0 END,
                bot_id = :botId
            WHERE integration_id = :integrationId
              AND (lease_owner IS NULL OR lease_owner = :owner OR lease_until < now())
            RETURNING update_offset
            """.trimIndent(),
        )
            .bind("owner", owner)
            .bind("leaseSeconds", leaseFor.inWholeSeconds.toDouble())
            .bind("botId", botId)
            .bind("integrationId", integrationId)
            .firstOrNull { row -> row.asLong("update_offset") }
    }

    /**
     * Проверяет в транзакции записи, что аренда интеграции [integrationId] всё ещё у [owner],
     * и блокирует строку до конца транзакции, чтобы её не перехватили во время записи.
     */
    context(tr: Transaction)
    suspend fun holds(integrationId: ChannelIntegrationId, owner: String): Boolean =
        tr.sql(
            """
            SELECT update_offset
            FROM telegram_poll_state
            WHERE integration_id = :integrationId AND lease_owner = :owner AND lease_until > now()
            FOR UPDATE
            """.trimIndent(),
        )
            .bind("integrationId", integrationId)
            .bind("owner", owner)
            .firstOrNull { row -> row.asLong("update_offset") } != null

    /** Сдвигает offset интеграции [integrationId] на [offset] и продлевает аренду [owner]. */
    context(tr: Transaction)
    suspend fun advance(integrationId: ChannelIntegrationId, owner: String, offset: Long) {
        tr.sql(
            """
            UPDATE telegram_poll_state
            SET update_offset = :offset, lease_until = now() + make_interval(secs => :leaseSeconds)
            WHERE integration_id = :integrationId AND lease_owner = :owner
            """.trimIndent(),
        )
            .bind("offset", offset)
            .bind("leaseSeconds", leaseFor.inWholeSeconds.toDouble())
            .bind("integrationId", integrationId)
            .bind("owner", owner)
            .execute()
    }
}
