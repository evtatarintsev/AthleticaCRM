package org.athletica.crm.domain.clients

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [ClientListView] поверх PostgreSQL. Собирает страницу клиентов одним
 * запросом, соединяя баланс (CTE с последней записью журнала), группы и контакты
 * (агрегированные в JSONB), и отдельным запросом считает общее число совпадений.
 */
class DbClientListView : ClientListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun page(query: ClientListQuery): ClientListPage {
        val total = countMatching(query)
        if (total == 0) {
            return ClientListPage(emptyList(), 0)
        }
        return ClientListPage(fetchRows(query), total)
    }

    /** Считает общее число клиентов, удовлетворяющих фильтрам [query] (без пагинации). */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    private suspend fun countMatching(query: ClientListQuery): Int =
        tr
            .sql("$BAL_CTE\nSELECT COUNT(*) AS total\n$FROM_WHERE")
            .bindFilters(query)
            .firstOrNull { row -> row.asLong("total").toInt() }
            ?: 0

    /** Загружает строки текущей страницы согласно [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    private suspend fun fetchRows(query: ClientListQuery): List<ClientListRow> {
        val orderColumn =
            when (query.sortColumn) {
                ClientSortColumn.NAME -> "c.name"
                ClientSortColumn.BALANCE -> "balance"
                ClientSortColumn.BIRTHDAY -> "c.birthday"
            }
        val direction = if (query.ascending) "ASC" else "DESC"

        val sql =
            """
            $BAL_CTE
            SELECT
                c.id, c.name, c.avatar_id, c.birthday, c.gender, c.custom_fields, c.state,
                COALESCE(b.balance_after, 0) AS balance,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', g.id, 'name', g.name) ORDER BY g.name), '[]'::jsonb)
                   FROM enrollments e JOIN groups g ON g.id = e.group_id
                  WHERE e.client_id = c.id AND e.left_at IS NULL) AS groups,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', cc.id, 'type', cc.type, 'value', cc.value) ORDER BY cc.created_at), '[]'::jsonb)
                   FROM client_contacts cc
                  WHERE cc.client_id = c.id AND cc.org_id = c.org_id) AS contacts
            $FROM_WHERE
            ORDER BY $orderColumn $direction NULLS LAST, c.id ASC
            LIMIT :limit OFFSET :offset
            """.trimIndent()

        return tr
            .sql(sql)
            .bindFilters(query)
            .bind("limit", query.limit)
            .bind("offset", query.offset)
            .list { row -> row.toClientListRow() }
    }

    /** Привязывает общие для обоих запросов параметры фильтрации. */
    context(ctx: EmployeeRequestContext)
    private fun QueryBuilder.bindFilters(query: ClientListQuery): QueryBuilder =
        bind("orgId", ctx.orgId)
            .bind("state", if (query.archived) "ARCHIVED" else "ACTIVE")
            .bind("gender", query.gender?.name)
            .bind("hasDebt", query.hasDebt)
            .bind("noGroup", query.noGroup)
            .bind("groupId", query.groupId)
            .bind("search", query.search)

    context(ctx: EmployeeRequestContext)
    private fun Row.toClientListRow(): ClientListRow {
        val clientId = asUuid("id").toClientId()
        return ClientListRow(
            id = clientId,
            name = asString("name"),
            avatarId = asUuidOrNull("avatar_id")?.toUploadId(),
            birthday = asLocalDateOrNull("birthday"),
            gender = Gender.valueOf(asString("gender")),
            balance = asMoney("balance", ctx.currency),
            customFields = Json.decodeFromString(asString("custom_fields")),
            groups = Json.decodeFromString(asString("groups")),
            contacts = decodeContacts(clientId, asString("contacts")),
            archived = asString("state") == "ARCHIVED",
        )
    }

    /** Контакт клиента в JSONB-агрегате запроса (без идентификатора клиента). */
    @Serializable
    private data class ContactRow(
        val id: ClientContactId,
        val type: ContactType,
        val value: String,
    )

    /** Декодирует JSONB-массив контактов в доменные [ClientContact] клиента [clientId]. */
    private fun decodeContacts(clientId: ClientId, json: String): List<ClientContact> =
        Json
            .decodeFromString<List<ContactRow>>(json)
            .map { ClientContact(it.id, clientId, it.type, it.value) }

    private companion object {
        /** Подзапрос последнего баланса по каждому клиенту организации. */
        val BAL_CTE =
            """
            WITH bal AS (
                SELECT DISTINCT ON (client_id) client_id, balance_after
                FROM client_balance_journal
                WHERE org_id = :orgId
                ORDER BY client_id, created_at DESC
            )
            """.trimIndent()

        /** Общий для страницы и счётчика блок FROM/WHERE с фильтрами. */
        val FROM_WHERE =
            """
            FROM clients c
            LEFT JOIN bal b ON b.client_id = c.id
            WHERE c.org_id = :orgId
              AND c.state = :state::client_state
              AND (:gender::gender IS NULL OR c.gender = :gender::gender)
              AND (NOT :hasDebt OR COALESCE(b.balance_after, 0) < 0)
              AND (NOT :noGroup OR NOT EXISTS (
                    SELECT 1 FROM enrollments e2 WHERE e2.client_id = c.id AND e2.left_at IS NULL))
              AND (:groupId::uuid IS NULL OR EXISTS (
                    SELECT 1 FROM enrollments e3
                     WHERE e3.client_id = c.id AND e3.left_at IS NULL AND e3.group_id = :groupId::uuid))
              AND (:search::text IS NULL OR c.name ILIKE '%' || :search || '%')
            """.trimIndent()
    }
}
