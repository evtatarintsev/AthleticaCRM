import Foundation
import PostgresNIO
import Testing
@testable import ClientsService

/// Организация теста со своим филиалом, пользователем и сотрудником; наполняет БД прямым SQL,
/// как вспомогательные функции Kotlin-тестов. Каждый тест работает в своей организации.
struct TestOrg: Sendable {
    /// База данных.
    let db: Database
    /// Контекст сотрудника-владельца.
    let ctx: EmployeeRequestContext

    /// Организация.
    var orgId: OrgId { ctx.orgId }
    /// Филиал контекста.
    var branchId: BranchId { ctx.branchId }

    /// Новая организация с часовым поясом [timezone] и валютой RUB.
    static func make(timezone: String = "Europe/Moscow") async throws -> TestOrg {
        let db = try await TestPostgres.db()
        let orgId = OrgId.new()
        let branchId = BranchId.new()
        try await db.transaction { tr throws(AppError) in
            try await tr.execute("INSERT INTO organizations (id, name, timezone) VALUES (\(orgId.value), 'Org', \(timezone))")
            try await tr.execute("INSERT INTO branches (id, org_id, name) VALUES (\(branchId.value), \(orgId.value), 'Основной')")
        }
        let org = TestOrg(db: db, ctx: EmployeeRequestContext(
            userId: .new(), orgId: orgId, branchId: branchId, employeeId: .new(),
            username: "owner@test", lang: .ru, clientIp: "127.0.0.1", currency: .RUB
        ))
        return try await org.withEmployee(org.ctx, name: "Владелец")
    }

    /// Ещё один сотрудник организации с именем [name]; возвращает его контекст.
    func employee(_ name: String) async throws -> EmployeeRequestContext {
        let other = EmployeeRequestContext(
            userId: .new(), orgId: orgId, branchId: branchId, employeeId: .new(),
            username: "\(name)@test", lang: .ru, clientIp: nil, currency: .RUB
        )
        return try await withEmployee(other, name: name).ctx
    }

    /// Заводит пользователя и сотрудника контекста [ctx].
    private func withEmployee(_ ctx: EmployeeRequestContext, name: String) async throws -> TestOrg {
        try await sql("INSERT INTO users (id, login, password_hash) VALUES (\(ctx.userId.value), \(ctx.userId.description), 'x')")
        try await sql("""
            INSERT INTO employees (id, org_id, user_id, name, all_branches_access)
            VALUES (\(ctx.employeeId.value), \(orgId.value), \(ctx.userId.value), \(name), true)
            """)
        return TestOrg(db: db, ctx: ctx)
    }

    /// Выполняет [query] в отдельной транзакции.
    func sql(_ query: PostgresQuery) async throws {
        try await db.transaction { tr throws(AppError) in try await tr.execute(query) }
    }

    /// Целое из первой колонки первой строки [query].
    func int(_ query: PostgresQuery) async throws -> Int {
        try await db.transaction { tr throws(AppError) in
            try await tr.first(query) { row in try row.decode(Int.self) } ?? 0
        }
    }

    /// Выполняет [body] в транзакции с контекстом владельца.
    func tx<T: Sendable>(_ body: (EmployeeRequestContext, Transaction) async throws(AppError) -> T) async throws(AppError) -> T {
        try await db.transaction { tr throws(AppError) in try await body(ctx, tr) }
    }

    /// Клиент организации прямым SQL.
    @discardableResult
    func client(
        _ name: String, birthday: LocalDate? = nil, gender: Gender = .MALE, archived: Bool = false, id: ClientId = .new()
    ) async throws -> ClientId {
        try await sql("""
            INSERT INTO clients (id, org_id, name, birthday, gender, state)
            VALUES (\(id.value), \(orgId.value), \(name), \(birthday?.description)::date,
                    \(gender.rawValue)::gender, \(archived ? "ARCHIVED" : "ACTIVE")::client_state)
            """)
        return id
    }

    /// Группа организации в филиале [branchId] (по умолчанию — филиал контекста).
    func group(_ name: String, branchId: BranchId? = nil) async throws -> GroupId {
        let id = GroupId.new()
        try await sql("INSERT INTO groups (id, org_id, name, branch_id) VALUES (\(id.value), \(orgId.value), \(name), \((branchId ?? self.branchId).value))")
        return id
    }

    /// Ещё один филиал организации.
    func branch(_ name: String) async throws -> BranchId {
        let id = BranchId.new()
        try await sql("INSERT INTO branches (id, org_id, name) VALUES (\(id.value), \(orgId.value), \(name))")
        return id
    }

    /// Записывает клиента [clientId] в группу [groupId].
    func enroll(_ clientId: ClientId, _ groupId: GroupId) async throws {
        try await sql("INSERT INTO enrollments (group_id, client_id) VALUES (\(groupId.value), \(clientId.value))")
    }

    /// Активные записи клиента [clientId] в группы.
    func activeEnrollments(_ clientId: ClientId) async throws -> Int {
        try await int("SELECT COUNT(*) FROM enrollments WHERE client_id = \(clientId.value) AND left_at IS NULL")
    }

    /// Доводит аванс клиента до [balance] корректирующей проводкой, как `setClientAdvance` в Kotlin-тестах.
    func balance(_ clientId: ClientId, _ balance: Money) async throws {
        let amount = Decimal(balance.minorUnits) / 100
        try await db.transaction { tr throws(AppError) in
            let advance = try await account(tr, kind: "advance", clientId: clientId)
            let adjustment = try await account(tr, kind: "adjustment", clientId: nil)
            let current = try await tr.first(
                "SELECT COALESCE(-SUM(amount), 0)::text FROM ledger_postings WHERE account_id = \(advance)"
            ) { row in try row.decode(String.self) } ?? "0"
            let delta = amount - (Decimal(string: current) ?? 0)
            if delta == 0 {
                return
            }
            let transactionId = UUID.v7()
            try await tr.execute("""
                INSERT INTO ledger_transactions (id, org_id, kind, occurred_on) VALUES (\(transactionId), \(orgId.value), 'adjustment', current_date)
                """)
            try await tr.execute("""
                INSERT INTO ledger_postings (transaction_id, account_id, amount)
                VALUES (\(transactionId), \(advance), \("\(-delta)")::numeric), (\(transactionId), \(adjustment), \("\(delta)")::numeric)
                """)
        }
    }

    /// Счёт главной книги вида [kind] (для клиента [clientId]); создаётся при первом обращении.
    private func account(_ tr: Transaction, kind: String, clientId: ClientId?) async throws(AppError) -> UUID {
        let existing = try await tr.first("""
            SELECT id FROM ledger_accounts WHERE org_id = \(orgId.value) AND kind = \(kind) AND client_id IS NOT DISTINCT FROM \(clientId?.value)
            """) { row in try row.decode(UUID.self) }
        if let existing {
            return existing
        }
        let id = UUID.v7()
        try await tr.execute("INSERT INTO ledger_accounts (id, org_id, kind, client_id) VALUES (\(id), \(orgId.value), \(kind), \(clientId?.value))")
        return id
    }

    /// Загруженный файл организации.
    func upload() async throws -> UploadId {
        let id = UploadId.new()
        try await sql("""
            INSERT INTO uploads (id, org_id, uploaded_by, object_key, original_name, content_type, size_bytes)
            VALUES (\(id.value), \(orgId.value), \(ctx.userId.value), \(id.description), 'file.pdf', 'application/pdf', 1)
            """)
        return id
    }

    /// Определение кастомного поля клиента; [config] — JSON конфигурации, порядок — [order].
    func customField(_ key: String, _ label: String, type: String, required: Bool = false, config: String = "{}", order: Int = 0) async throws {
        try await sql("""
            INSERT INTO custom_field_definitions (org_id, entity_type, field_key, label, field_type, config, is_required, display_order)
            VALUES (\(orgId.value), 'CLIENT', \(key), \(label), \(type)::custom_field_type, \(config)::jsonb, \(required), \(order))
            ON CONFLICT (org_id, entity_type, field_key) DO UPDATE
            SET label = EXCLUDED.label, config = EXCLUDED.config, is_required = EXCLUDED.is_required
            """)
    }

    /// Записи журнала аудита организации: тип действия и сущности по каждой.
    func audit(entityId: UUID) async throws -> [String] {
        try await db.transaction { tr throws(AppError) in
            try await tr.list("""
                SELECT action_type || ':' || entity_type FROM audit_logs WHERE org_id = \(orgId.value) AND entity_id = \(entityId) ORDER BY created_at, id
                """) { try $0.decode(String.self) }
        }
    }

    /// Контакт клиента прямым SQL на позиции [position].
    func contact(
        _ clientId: ClientId, _ type: ContactType, _ value: String, label: String? = nil, primary: Bool = true, position: Int = 0
    ) async throws {
        try await sql("""
            INSERT INTO client_contacts (id, org_id, client_id, type, value, label, is_primary, position)
            VALUES (\(UUID.v7()), \(orgId.value), \(clientId.value), \(type.rawValue), \(value), \(label), \(primary), \(position))
            """)
    }

    /// Занятие группы [groupId] на дату [date] в [start] со статусом [status].
    func session(_ groupId: GroupId, _ date: String, _ start: String = "10:00", status: String = "completed") async throws -> SessionId {
        let id = SessionId.new()
        let hall = UUID.v7()
        try await sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (\(hall), \(orgId.value), \(branchId.value), \(hall.uuidString))")
        try await sql("""
            INSERT INTO sessions (id, org_id, group_id, date, start_time, end_time, status, hall_id)
            VALUES (\(id.value), \(orgId.value), \(groupId.value), \(date)::date, \(start)::time, \(start)::time + interval '1 hour',
                    \(status)::session_status, \(hall))
            """)
        return id
    }

    /// Отметка клиента [clientId] на занятии [sessionId] с присутствием [presence].
    func attend(_ sessionId: SessionId, _ clientId: ClientId, presence: String? = "present") async throws {
        try await sql("""
            INSERT INTO session_attendance (session_id, client_id, kind, presence)
            VALUES (\(sessionId.value), \(clientId.value), 'regular', \(presence)::attendance_presence)
            """)
    }

    /// Списание занятия [sessionId] клиента [clientId] с нового абонемента; возвращает идентификатор списания.
    func debit(_ sessionId: SessionId, _ clientId: ClientId) async throws -> UUID {
        let saleId = UUID.v7()
        let saleItemId = UUID.v7()
        let membershipId = UUID.v7()
        let debitId = UUID.v7()
        try await sql("INSERT INTO sales (id, org_id, client_id, sold_on) VALUES (\(saleId), \(orgId.value), \(clientId.value), current_date)")
        try await sql("""
            INSERT INTO sale_items (id, sale_id, kind, name, quantity, unit_price, amount)
            VALUES (\(saleItemId), \(saleId), 'membership', 'Абонемент', 1, 0, 0)
            """)
        try await sql("""
            INSERT INTO memberships (id, org_id, client_id, name, state, activation_mode, duration_value, duration_unit, close_mode, issued_on, sale_item_id)
            VALUES (\(membershipId), \(orgId.value), \(clientId.value), 'Абонемент', 'INACTIVE', 'IMMEDIATE', 1, 'MONTH', 'MANUAL', current_date, \(saleItemId))
            """)
        try await sql("""
            INSERT INTO membership_debits (id, org_id, membership_id, client_id, session_id, kind)
            VALUES (\(debitId), \(orgId.value), \(membershipId), \(clientId.value), \(sessionId.value), 'REGULAR')
            """)
        return debitId
    }
}

/// Проверяет, что [body] бросает доменную ошибку с кодом [code].
func expectDomainError(
    _ code: String, sourceLocation: SourceLocation = #_sourceLocation, _ body: () async throws(AppError) -> Void
) async {
    do {
        try await body()
        Issue.record("Ожидалась ошибка \(code)", sourceLocation: sourceLocation)
    } catch {
        guard case let .domain(domain) = error else {
            Issue.record("Ожидалась доменная ошибка \(code), получено \(error)", sourceLocation: sourceLocation)
            return
        }
        #expect(domain.code == code, sourceLocation: sourceLocation)
    }
}

/// Имя клиента из строки, о которой заранее известно, что она непустая.
func name(_ raw: String) -> NonBlankName {
    try! NonBlankName.from(raw)
}
