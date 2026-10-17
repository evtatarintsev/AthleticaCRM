package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.messaging.ClientConversationItemSchema
import org.athletica.crm.api.schemas.messaging.InboxCounterResponse
import org.athletica.crm.api.schemas.messaging.InboxFilterSchema
import org.athletica.crm.api.schemas.messaging.InboxItemSchema
import org.athletica.crm.api.schemas.messaging.InboxListResponse
import org.athletica.crm.api.schemas.messaging.InboxMessageDirectionSchema
import org.athletica.crm.api.schemas.messaging.UnresolvedChatItemSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/**
 * Реализация [InboxView] поверх PostgreSQL. Внутренний `UNION ALL` отдаёт только ключи записей
 * и момент последнего сообщения, к нему применяются фильтр, сортировка и пагинация;
 * последнее сообщение и непрочитанное считаются только для строк страницы.
 */
class DbInboxView : InboxView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(query: InboxQuery): InboxListResponse {
        val total = count(query.filter)
        if (total == 0) {
            return InboxListResponse(emptyList(), 0)
        }
        val items =
            tr
                .sql(
                    """
                    $ITEMS_CTE,
                    page AS (
                        SELECT * FROM items
                        ORDER BY last_at DESC, conversation_id, integration_id, address
                        LIMIT :limit OFFSET :offset
                    )
                    SELECT p.kind, p.last_at,
                           cl.id AS client_id, cl.name AS client_name,
                           lm.body AS last_message_body, lm.direction AS last_message_direction,
                           un.unread,
                           u.integration_id, u.from_address, u.channel_name, u.unresolved_count, u.last_body,
                           COALESCE(cc.display_name, '') AS display_name, cc.username, cc.phone
                    FROM page p
                    LEFT JOIN conv ON conv.id = p.conversation_id
                    LEFT JOIN clients cl ON cl.id = conv.client_id
                    LEFT JOIN LATERAL (
                        SELECT m.body, m.direction
                        FROM messages m
                        WHERE m.conversation_id = p.conversation_id
                        ORDER BY m.created_at DESC, m.id DESC
                        LIMIT 1
                    ) lm ON true
                    LEFT JOIN LATERAL (
                        SELECT count(*) AS unread
                        FROM messages m
                        WHERE m.conversation_id = p.conversation_id
                          AND m.direction = 'INBOUND'
                          AND m.created_at > COALESCE(conv.last_read_at, 'epoch'::timestamptz)
                    ) un ON true
                    LEFT JOIN unresolved u ON u.integration_id = p.integration_id AND u.from_address = p.address
                    LEFT JOIN channel_chats cc ON cc.integration_id = p.integration_id AND cc.address = p.address
                    ORDER BY p.last_at DESC, p.conversation_id, p.integration_id, p.address
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("filter", query.filter.name)
                .bind("limit", query.limit)
                .bind("offset", query.offset)
                .list { row -> row.toItem() }
        return InboxListResponse(items, total)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun counter(): InboxCounterResponse = InboxCounterResponse(count(InboxFilterSchema.UNREAD))

    /** Число записей списка под фильтром [filter]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun count(filter: InboxFilterSchema): Int =
        tr
            .sql("$ITEMS_CTE\nSELECT count(*) AS total FROM items")
            .bind("orgId", ctx.orgId)
            .bind("filter", filter.name)
            .firstOrNull { row -> row.asLong("total").toInt() } ?: 0

    /** Собирает запись списка из строки выборки. */
    private fun Row.toItem(): InboxItemSchema =
        when (asString("kind")) {
            KIND_CLIENT ->
                ClientConversationItemSchema(
                    clientId = asUuid("client_id").toClientId(),
                    clientName = asString("client_name"),
                    lastMessageBody = asString("last_message_body"),
                    lastMessageAt = asInstant("last_at").toString(),
                    lastMessageDirection = InboxMessageDirectionSchema.valueOf(asString("last_message_direction")),
                    unreadCount = asLong("unread").toInt(),
                )

            else ->
                UnresolvedChatItemSchema(
                    integrationId = asUuid("integration_id").toChannelIntegrationId(),
                    channelName = asString("channel_name"),
                    address = asString("from_address"),
                    displayName = asString("display_name"),
                    username = asStringOrNull("username"),
                    phone = asStringOrNull("phone"),
                    unresolvedCount = asLong("unresolved_count").toInt(),
                    lastMessageBody = asString("last_body"),
                    lastMessageAt = asInstant("last_at").toString(),
                )
        }

    private companion object {
        /** Вид записи «беседа клиента» в колонке `kind`. */
        const val KIND_CLIENT = "client"

        /**
         * Общая часть запросов: неразобранные чаты (`unresolved`), беседы клиентов с сообщениями
         * (`conv`) и их объединение по ключам (`items`) под фильтром `:filter`.
         */
        val ITEMS_CTE =
            """
            WITH unresolved AS (
                SELECT r.integration_id, r.from_address, ci.name AS channel_name,
                       count(*) AS unresolved_count,
                       max(r.received_at) AS last_at,
                       (array_agg(r.body ORDER BY r.received_at DESC, r.id DESC))[1] AS last_body
                FROM inbound_receipts r
                JOIN channel_integrations ci ON ci.id = r.integration_id AND ci.org_id = :orgId
                WHERE r.org_id = :orgId AND r.state = 'UNRESOLVED'
                GROUP BY r.integration_id, r.from_address, ci.name
            ),
            conv AS (
                SELECT c.id, c.client_id, c.last_message_at, rs.last_read_at
                FROM conversations c
                LEFT JOIN conversation_read_state rs ON rs.conversation_id = c.id
                WHERE c.org_id = :orgId
                  AND c.last_message_at IS NOT NULL
            ),
            items AS (
                SELECT '$KIND_CLIENT' AS kind, conv.id AS conversation_id,
                       NULL::uuid AS integration_id, NULL::text AS address, conv.last_message_at AS last_at
                FROM conv
                WHERE CAST(:filter AS text) <> 'UNRESOLVED'
                  AND (
                      CAST(:filter AS text) <> 'UNREAD'
                      OR EXISTS (
                          SELECT 1 FROM messages m
                          WHERE m.conversation_id = conv.id
                            AND m.direction = 'INBOUND'
                            AND m.created_at > COALESCE(conv.last_read_at, 'epoch'::timestamptz)
                      )
                  )
                UNION ALL
                SELECT 'unresolved', NULL::uuid, integration_id, from_address, last_at
                FROM unresolved
            )
            """.trimIndent()
    }
}
