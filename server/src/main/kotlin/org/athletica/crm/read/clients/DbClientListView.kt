package org.athletica.crm.read.clients

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.clients.ActiveClientListMembershipState
import org.athletica.crm.api.schemas.clients.ClientContactSchema
import org.athletica.crm.api.schemas.clients.ClientGroup
import org.athletica.crm.api.schemas.clients.ClientListItem
import org.athletica.crm.api.schemas.clients.ClientListMembership
import org.athletica.crm.api.schemas.clients.ClientListResponse
import org.athletica.crm.api.schemas.clients.ClientState
import org.athletica.crm.api.schemas.clients.InactiveClientListMembershipState
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.ActiveStatus
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [ClientListView] поверх PostgreSQL. Собирает страницу клиентов одним
 * запросом, соединяя баланс (CTE с последней записью журнала), группы, контакты и
 * абонементы (агрегированные в JSONB) и число неоплаченных занятий в группах филиала,
 * и отдельным запросом считает общее число совпадений. «Сегодня» — дата
 * [ClientListQuery.now] в часовом поясе организации; признаки абонемента выводит общий
 * [ActiveStatus.of], порядок частей — Kotlin поверх SQL-порядка внутри части.
 */
class DbClientListView : ClientListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun page(query: ClientListQuery): ClientListResponse {
        val total = countMatching(query)
        if (total == 0) {
            return ClientListResponse(emptyList(), 0u)
        }
        return ClientListResponse(fetchItems(query), total.toUInt())
    }

    /** Считает общее число клиентов, удовлетворяющих фильтрам [query] (без пагинации). */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun countMatching(query: ClientListQuery): Int =
        tr
            .sql("$BAL_CTE\nSELECT COUNT(*) AS total\n$FROM_WHERE")
            .bindFilters(query)
            .firstOrNull { row -> row.asLong("total").toInt() }
            ?: 0

    /** Загружает элементы текущей страницы согласно [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun fetchItems(query: ClientListQuery): List<ClientListItem> {
        val order =
            query.sort.flatMap { criterion ->
                val direction = if (criterion.ascending) "ASC" else "DESC"
                val columns =
                    when (criterion.column) {
                        ClientSortColumn.NAME -> listOf("c.name")
                        ClientSortColumn.BALANCE -> listOf("balance")
                        ClientSortColumn.BIRTHDAY -> listOf("c.birthday")
                        ClientSortColumn.NEXT_BIRTHDAY ->
                            listOf(
                                "($BIRTHDAY_MD < :birthdayFromMd::int)",
                                BIRTHDAY_MD,
                            )
                    }
                columns.map { "$it $direction NULLS LAST" }
            }.plus("c.id ASC").joinToString(", ")

        val sql =
            """
            $BAL_CTE
            SELECT
                c.id, c.name, c.avatar_id, c.birthday, c.gender, c.custom_fields, c.state,
                COALESCE(b.balance_after, 0) AS balance,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', g.id, 'name', g.name) ORDER BY g.name), '[]'::jsonb)
                   FROM enrollments e JOIN groups g ON g.id = e.group_id
                  WHERE e.client_id = c.id AND e.left_at IS NULL) AS groups,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', cc.id, 'type', cc.type, 'value', cc.value, 'label', cc.label, 'isPrimary', cc.is_primary) ORDER BY cc.position), '[]'::jsonb)
                   FROM client_contacts cc
                  WHERE cc.client_id = c.id AND cc.org_id = c.org_id) AS contacts,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                            'id', m.id, 'name', m.name, 'state', m.state,
                            'activationMode', m.activation_mode, 'activateOn', m.activate_on,
                            'startDate', m.start_date, 'endDate', m.end_date,
                            'sessionsTotal', m.sessions_total, 'sessionsRemaining', m.sessions_remaining)
                          ORDER BY m.end_date NULLS LAST, m.issued_on, m.created_at, m.id), '[]'::jsonb)
                   FROM memberships m
                  WHERE m.client_id = c.id AND m.org_id = c.org_id
                    AND m.state IN ('ACTIVE', 'INACTIVE')) AS memberships,
                (SELECT COUNT(*)::int
                   FROM session_attendance a
                   JOIN sessions s ON s.id = a.session_id
                   JOIN groups g ON g.id = s.group_id AND g.branch_id = :branchId
                  WHERE a.client_id = c.id AND a.presence = 'present'
                    AND s.org_id = c.org_id AND s.status = 'completed'
                    AND NOT EXISTS (SELECT 1 FROM membership_debits d
                                     WHERE d.session_id = s.id AND d.client_id = c.id
                                       AND d.reversed_at IS NULL)) AS unpaid_sessions,
                (SELECT (CAST(:now AS timestamptz) AT TIME ZONE o.timezone)::date
                   FROM organizations o WHERE o.id = c.org_id) AS today
            $FROM_WHERE
            ORDER BY $order
            ${if (query.limit != null) "LIMIT :limit OFFSET :offset" else ""}
            """.trimIndent()

        return tr
            .sql(sql)
            .bindFilters(query)
            .bind("now", query.now)
            .bind("branchId", ctx.branchId)
            .let { q -> query.limit?.let { q.bind("limit", it).bind("offset", query.offset) } ?: q }
            .list { row -> row.toListItem() }
    }

    /**
     * Цифры поискового запроса для поиска по телефону, приведённые как телефоны в SQL
     * (11 цифр с ведущей `8` → ведущая `7`); `null`, если цифр меньше трёх — это не номер.
     */
    private fun String.phoneDigits(): String? {
        val digits = filter { it in '0'..'9' }
        return when {
            digits.length < 3 -> null
            digits.length == 11 && digits.startsWith('8') -> "7" + digits.drop(1)
            else -> digits
        }
    }

    /** Привязывает общие для обоих запросов параметры фильтрации. */
    context(ctx: EmployeeRequestContext)
    private fun QueryBuilder.bindFilters(query: ClientListQuery): QueryBuilder {
        // День рождения кодируется как MMDD-целое: месяц × 100 + день.
        // Например: 19 июня → 619, 31 декабря → 1231, 5 января → 105.
        // Множитель 100 достаточен, чтобы дни (макс. 31) не переполняли разряд месяца.
        // Числовое сравнение сохраняет порядок дат внутри года и позволяет
        // обойтись простым BETWEEN в SQL без манипуляций с годом.
        val birthdayFromMd: Int? = query.birthday?.from?.run { (month.ordinal + 1) * 100 + day }
        val birthdayToMd: Int? = query.birthday?.to?.run { (month.ordinal + 1) * 100 + day }
        return bind("orgId", ctx.orgId)
            .bind("state", if (query.archived) "ARCHIVED" else "ACTIVE")
            .bind("gender", query.gender?.name)
            .bind("hasDebt", query.hasDebt)
            .bind("noGroup", query.noGroup)
            .bind("groupId", query.groupId)
            .bind("search", query.search)
            .bind("searchDigits", query.search?.phoneDigits())
            .bind("birthdayFromMd", birthdayFromMd)
            .bind("birthdayToMd", birthdayToMd)
    }

    /**
     * Собирает элемент ответа из строки выборки. Группы и контакты приходят
     * JSONB-массивами, форма которых совпадает со схемами ответа, поэтому
     * декодируются в них напрямую.
     */
    context(ctx: EmployeeRequestContext)
    private fun Row.toListItem(): ClientListItem =
        ClientListItem(
            id = asUuid("id").toClientId(),
            name = asString("name"),
            avatarId = asUuidOrNull("avatar_id")?.toUploadId(),
            birthday = asLocalDateOrNull("birthday"),
            gender = Gender.valueOf(asString("gender")),
            groups = Json.decodeFromString<List<ClientGroup>>(asString("groups")),
            balance = asMoney("balance", ctx.currency),
            customFields = Json.decodeFromString(asString("custom_fields")),
            contacts = Json.decodeFromString<List<ClientContactSchema>>(asString("contacts")),
            state = if (asString("state") == "ARCHIVED") ClientState.ARCHIVED else ClientState.ACTIVE,
            memberships = memberships(asLocalDate("today")),
            unpaidSessions = asInt("unpaid_sessions"),
        )

    /**
     * Абонементы клиента из JSONB-массива с признаками на дату [today]. SQL отдаёт их по дате
     * окончания и дате выдачи; стабильная сортировка по части (активный, «к закрытию»,
     * неактивированный) сохраняет этот порядок внутри части.
     */
    private fun Row.memberships(today: LocalDate): List<ClientListMembership> =
        Json
            .decodeFromString<List<MembershipRow>>(asString("memberships"))
            .map { it.toMembership(today) }
            .sortedBy { m ->
                when (val state = m.state) {
                    is ActiveClientListMembershipState -> if (state.closePending) 1 else 0
                    is InactiveClientListMembershipState -> 2
                }
            }

    /** Абонемент строки JSONB-массива в сыром виде таблицы `memberships`. */
    @Serializable
    private data class MembershipRow(
        /** Идентификатор абонемента. */
        val id: MembershipId,
        /** Название абонемента. */
        val name: String,
        /** Сохранённое состояние: `ACTIVE` или `INACTIVE`. */
        val state: String,
        /** Режим активации. */
        val activationMode: ActivationMode,
        /** Дата активации для режима «с даты». */
        val activateOn: LocalDate?,
        /** Дата начала; у неактивированного — `null`. */
        val startDate: LocalDate?,
        /** Дата окончания; у неактивированного — `null`. */
        val endDate: LocalDate?,
        /** Общее количество занятий; `null` — безлимит. */
        val sessionsTotal: Int?,
        /** Остаток занятий; `null` — безлимит. */
        val sessionsRemaining: Int?,
    ) {
        /** Абонемент ответа с признаками на дату [today]. */
        fun toMembership(today: LocalDate): ClientListMembership {
            val state =
                if (state == "ACTIVE" && startDate != null && endDate != null) {
                    val status = ActiveStatus.of(startDate, endDate, sessionsRemaining, today)
                    ActiveClientListMembershipState(
                        startDate = startDate,
                        endDate = endDate,
                        termPosition = status.termPosition,
                        endingSoon = status.endingSoon.sorted(),
                        closePending = status.closePending,
                    )
                } else {
                    InactiveClientListMembershipState(activationMode, activateOn)
                }
            return ClientListMembership(id, name, sessionsTotal, sessionsRemaining, state)
        }
    }

    private companion object {
        /** День рождения клиента как целое MMDD (месяц × 100 + день), без учёта года. */
        const val BIRTHDAY_MD = "(EXTRACT(MONTH FROM c.birthday)::int * 100 + EXTRACT(DAY FROM c.birthday)::int)"

        /** Подзапрос баланса главной книги по каждому клиенту организации. */
        val BAL_CTE =
            """
            WITH bal AS (
                SELECT a.client_id, -SUM(p.amount) AS balance_after
                FROM ledger_accounts a JOIN ledger_postings p ON p.account_id = a.id
                WHERE a.org_id = :orgId AND a.kind IN ('advance', 'receivable')
                GROUP BY a.client_id
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
              AND (
                :search::text IS NULL
                OR c.name ILIKE '%' || :search || '%'
                OR (:searchDigits::text IS NOT NULL AND EXISTS (
                    SELECT 1
                    FROM (
                        SELECT regexp_replace(pc.value, '[^0-9]', '', 'g') AS digits
                        FROM client_contacts pc
                        WHERE pc.client_id = c.id AND pc.type = 'PHONE'
                    ) phones
                    WHERE CASE
                              WHEN length(phones.digits) = 11 AND left(phones.digits, 1) = '8'
                                  THEN '7' || substr(phones.digits, 2)
                              ELSE phones.digits
                          END LIKE '%' || :searchDigits || '%'))
              )
              AND (
                :birthdayFromMd::int IS NULL
                OR (
                    c.birthday IS NOT NULL
                    AND (
                        (
                            :birthdayFromMd::int <= :birthdayToMd::int
                            AND $BIRTHDAY_MD
                                BETWEEN :birthdayFromMd::int AND :birthdayToMd::int
                        )
                        OR (
                            :birthdayFromMd::int > :birthdayToMd::int
                            AND (
                                $BIRTHDAY_MD
                                    >= :birthdayFromMd::int
                                OR $BIRTHDAY_MD
                                    <= :birthdayToMd::int
                            )
                        )
                    )
                )
              )
            """.trimIndent()
    }
}
