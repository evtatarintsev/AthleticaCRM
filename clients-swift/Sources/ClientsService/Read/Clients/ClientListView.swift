import Foundation
import PostgresNIO

/// Колонка сортировки списка клиентов на стороне сервера (решение проекции, а не контракта API).
enum ClientSortColumn: Sendable {
    /// По имени.
    case name
    /// По балансу.
    case balance
    /// По дате рождения.
    case birthday
    /// По месяцу и дню относительно начала диапазона дней рождения.
    case nextBirthday
}

/// Критерий сортировки проекции клиентов.
struct ClientSortCriterion: Sendable {
    /// Колонка.
    let column: ClientSortColumn
    /// По возрастанию.
    let ascending: Bool
}

/// Параметры страницы списка клиентов; значения уже нормализованы маршрутом.
struct ClientListQuery: Sendable {
    /// «Сегодня» организации — дата, на которую выводятся признаки абонементов.
    let today: LocalDate
    /// Архивные вместо активных.
    var archived = false
    /// Поиск по имени (подстрока) или телефону (цифры); `nil` — без поиска.
    var search: String? = nil
    /// Фильтр по полу.
    var gender: Gender? = nil
    /// Только с отрицательным балансом.
    var hasDebt = false
    /// Только без активных групп.
    var noGroup = false
    /// Только участники группы.
    var groupId: GroupId? = nil
    /// Фильтр по дню рождения без учёта года.
    var birthday: DateRange? = nil
    /// Критерии сортировки по приоритету.
    var sort: [ClientSortCriterion] = [ClientSortCriterion(column: .name, ascending: true)]
    /// Размер страницы.
    var limit = 50
    /// Смещение.
    var offset = 0
}

/// Read-проекция списка клиентов: кросс-агрегатная выборка под экран (клиент, баланс, группы,
/// контакты, абонементы, неоплаченные занятия) с фильтрацией, сортировкой и пагинацией в SQL.
protocol ClientListView: Sendable {
    /// Страница клиентов согласно [query].
    func page(_ query: ClientListQuery, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> ClientListResponse
}

/// [ClientListView] поверх PostgreSQL: страница одним запросом и отдельный `COUNT`.
/// SQL перенесён из `DbClientListView.kt` без изменений.
struct DbClientListView: ClientListView {
    func page(_ query: ClientListQuery, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> ClientListResponse {
        let total = try await tr.first(
            try Self.filters(NamedQuery("\(Self.balanceCte)\nSELECT COUNT(*)\n\(Self.fromWhere)"), query, ctx).compiled()
        ) { row in try row.decode(Int.self) } ?? 0
        if total == 0 {
            return ClientListResponse(clients: [], total: 0)
        }
        return ClientListResponse(clients: try await items(query, ctx: ctx, tr: tr), total: total)
    }

    /// Клиенты текущей страницы согласно [query].
    private func items(_ query: ClientListQuery, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> [ClientListItem] {
        let order = (query.sort.flatMap { criterion in
            let direction = criterion.ascending ? "ASC" : "DESC"
            let columns: [String] = switch criterion.column {
            case .name: ["c.name"]
            case .balance: ["balance"]
            case .birthday: ["c.birthday"]
            case .nextBirthday: ["(\(Self.birthdayMd) < :birthdayFromMd::int)", Self.birthdayMd]
            }
            return columns.map { "\($0) \(direction) NULLS LAST" }
        } + ["c.id ASC"]).joined(separator: ", ")

        let sql = """
            \(Self.balanceCte)
            SELECT
                c.id, c.name, c.avatar_id, c.birthday::text, c.gender::text, c.custom_fields::text, c.state::text,
                COALESCE(b.balance_after, 0)::text AS balance,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', g.id, 'name', g.name) ORDER BY g.name), '[]'::jsonb)
                   FROM enrollments e JOIN groups g ON g.id = e.group_id
                  WHERE e.client_id = c.id AND e.left_at IS NULL)::text AS groups,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', cc.id, 'type', cc.type, 'value', cc.value, 'label', cc.label, 'isPrimary', cc.is_primary) ORDER BY cc.position), '[]'::jsonb)
                   FROM client_contacts cc
                  WHERE cc.client_id = c.id AND cc.org_id = c.org_id)::text AS contacts,
                \(Self.membershipsSql)
            \(Self.fromWhere)
            ORDER BY \(order)
            LIMIT :limit OFFSET :offset
            """
        let compiled = try Self.filters(NamedQuery(sql), query, ctx)
            .bind("today", query.today.description)
            .bind("branchId", ctx.branchId.value)
            .bind("limit", query.limit)
            .bind("offset", query.offset)
            .compiled()
        let rows = try await tr.list(compiled) { row in
            try row.decode((UUID, String, UUID?, String?, String, String, String, String, String, String, String, Int).self)
        }
        var result: [ClientListItem] = []
        for (id, name, avatarId, birthday, gender, customFields, state, balance, groups, contacts, memberships, unpaid) in rows {
            result.append(ClientListItem(
                id: ClientId(id),
                name: name,
                avatarId: avatarId.map(UploadId.init),
                birthday: birthday.flatMap(LocalDate.parse),
                gender: Gender(rawValue: gender) ?? .MALE,
                groups: try decodeJson([ClientGroup].self, groups),
                balance: try Money.fromDb(balance, ctx.currency),
                customFields: try decodeJson([CustomFieldValue].self, customFields),
                contacts: try decodeJson([ClientContactSchema].self, contacts),
                state: state == "ARCHIVED" ? .ARCHIVED : .ACTIVE,
                memberships: try decodeJson([MembershipRow].self, memberships).map { $0.membership(on: query.today) },
                unpaidSessions: unpaid
            ))
        }
        return result
    }

    /// Привязывает параметры фильтров, общие для страницы и счётчика.
    private static func filters(_ sql: NamedQuery, _ query: ClientListQuery, _ ctx: EmployeeRequestContext) -> NamedQuery {
        sql.bind("orgId", ctx.orgId.value)
            .bind("state", query.archived ? "ARCHIVED" : "ACTIVE")
            .bind("gender", query.gender?.rawValue)
            .bind("hasDebt", query.hasDebt)
            .bind("noGroup", query.noGroup)
            .bind("groupId", query.groupId?.value)
            .bind("search", query.search)
            .bind("searchDigits", query.search.flatMap(phoneDigits))
            .bind("birthdayFromMd", query.birthday?.from.monthDay)
            .bind("birthdayToMd", query.birthday?.to.monthDay)
    }

    /// Цифры поиска для поиска по телефону (11 цифр с ведущей `8` → ведущая `7`);
    /// `nil`, если цифр меньше трёх — это не номер.
    static func phoneDigits(_ search: String) -> String? {
        let digits = search.filter(\.isASCII).filter(\.isNumber)
        if digits.count < 3 {
            return nil
        }
        return digits.count == 11 && digits.hasPrefix("8") ? "7" + digits.dropFirst() : digits
    }

    /// Абонемент из JSONB-массива в сыром виде таблицы `memberships`.
    private struct MembershipRow: Decodable {
        /// Идентификатор.
        let id: MembershipId
        /// Название.
        let name: String
        /// Сохранённое состояние: `ACTIVE` или `INACTIVE`.
        let state: String
        /// Режим активации.
        let activationMode: ActivationMode
        /// Дата активации для режима «с даты».
        let activateOn: LocalDate?
        /// Дата начала; у неактивированного — `nil`.
        let startDate: LocalDate?
        /// Дата окончания; у неактивированного — `nil`.
        let endDate: LocalDate?
        /// Всего занятий; `nil` — безлимит.
        let sessionsTotal: Int?
        /// Остаток занятий; `nil` — безлимит.
        let sessionsRemaining: Int?

        /// Абонемент ответа с признаками на дату [today].
        func membership(on today: LocalDate) -> ClientListMembership {
            let membershipState: ClientListMembershipState
            if state == "ACTIVE", let startDate, let endDate {
                let status = ActiveStatus.of(startDate: startDate, endDate: endDate, sessionsRemaining: sessionsRemaining, date: today)
                membershipState = .active(
                    startDate: startDate,
                    endDate: endDate,
                    termPosition: status.termPosition,
                    endingSoon: status.endingSoon.sorted(),
                    closePending: status.closePending
                )
            } else {
                membershipState = .inactive(activationMode: activationMode, activateOn: activateOn)
            }
            return ClientListMembership(
                id: id, name: name, sessionsTotal: sessionsTotal, sessionsRemaining: sessionsRemaining, state: membershipState
            )
        }
    }

    /// Абонементы клиента и число неоплаченных занятий в группах филиала.
    /// Ступень «к закрытию» — SQL-копия [ActiveStatus.closePending]: проекция постраничная.
    private static let membershipsSql = """
        (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                    'id', m.id, 'name', m.name, 'state', m.state,
                    'activationMode', m.activation_mode, 'activateOn', m.activate_on,
                    'startDate', m.start_date, 'endDate', m.end_date,
                    'sessionsTotal', m.sessions_total, 'sessionsRemaining', m.sessions_remaining)
                  ORDER BY CASE
                               WHEN m.state = 'INACTIVE' THEN 2
                               WHEN m.end_date < :today::date OR m.sessions_remaining <= 0 THEN 1
                               ELSE 0
                           END,
                           m.end_date NULLS LAST, m.issued_on, m.created_at, m.id), '[]'::jsonb)
           FROM memberships m
          WHERE m.client_id = c.id AND m.org_id = c.org_id
            AND m.state IN ('ACTIVE', 'INACTIVE'))::text AS memberships,
        (SELECT COUNT(*)::int
           FROM session_attendance a
           JOIN sessions s ON s.id = a.session_id
           JOIN groups g ON g.id = s.group_id AND g.branch_id = :branchId
          WHERE a.client_id = c.id AND a.presence = 'present'
            AND s.org_id = c.org_id AND s.status = 'completed'
            AND NOT EXISTS (SELECT 1 FROM membership_debits d
                             WHERE d.session_id = s.id AND d.client_id = c.id
                               AND d.reversed_at IS NULL)) AS unpaid_sessions
        """

    /// День рождения клиента как целое MMDD, без учёта года.
    private static let birthdayMd = "(EXTRACT(MONTH FROM c.birthday)::int * 100 + EXTRACT(DAY FROM c.birthday)::int)"

    /// Баланс главной книги по каждому клиенту организации.
    private static let balanceCte = """
        WITH bal AS (
            SELECT a.client_id, -SUM(p.amount) AS balance_after
            FROM ledger_accounts a JOIN ledger_postings p ON p.account_id = a.id
            WHERE a.org_id = :orgId AND a.kind IN ('advance', 'receivable')
            GROUP BY a.client_id
        )
        """

    /// Общий для страницы и счётчика блок FROM/WHERE с фильтрами.
    private static let fromWhere = """
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
                        AND \(birthdayMd) BETWEEN :birthdayFromMd::int AND :birthdayToMd::int
                    )
                    OR (
                        :birthdayFromMd::int > :birthdayToMd::int
                        AND (\(birthdayMd) >= :birthdayFromMd::int OR \(birthdayMd) <= :birthdayToMd::int)
                    )
                )
            )
          )
        """
}
