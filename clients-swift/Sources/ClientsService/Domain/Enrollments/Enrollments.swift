import PostgresNIO

/// Записи клиентов в группы. Каждый период участия — отдельная строка: повторная запись
/// после выхода создаёт новую, история сохраняется.
protocol Enrollments: Sendable {
    /// Добавляет [clientIds] в группу [groupId] своей организации; уже активные записи не дублируются.
    func add(_ groupId: GroupId, _ clientIds: [ClientId], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError)

    /// Завершает участие [clientIds] в группе [groupId] (soft delete); неактивных пропускает.
    func remove(_ groupId: GroupId, _ clientIds: [ClientId], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError)
}

extension Enrollments {
    /// Записывает клиента [clientId] в каждую из [groupIds] через [add], чтобы декоратор аудита
    /// записал каждую запись; атомарность обеспечивает транзакция [tr].
    func addToGroups(_ clientId: ClientId, _ groupIds: [GroupId], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        for groupId in groupIds {
            try await add(groupId, [clientId], ctx: ctx, tr: tr)
        }
    }
}

/// Записи в группы поверх PostgreSQL.
struct DbEnrollments: Enrollments {
    func add(_ groupId: GroupId, _ clientIds: [ClientId], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        try await verifyGroupOwnership(groupId, ctx: ctx, tr: tr)
        let notFound = DomainError(code: "CLIENT_NOT_FOUND", message: Messages.clientNotFound.localize(ctx))
        for clientId in clientIds {
            try await tr.execute(
                """
                INSERT INTO enrollments (group_id, client_id)
                VALUES (\(groupId.value), \(clientId.value))
                ON CONFLICT (client_id, group_id) WHERE left_at IS NULL DO NOTHING
                """,
                onViolation: ["enrollments_client_id_fkey": notFound]
            )
        }
    }

    func remove(_ groupId: GroupId, _ clientIds: [ClientId], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        try await verifyGroupOwnership(groupId, ctx: ctx, tr: tr)
        for clientId in clientIds {
            try await tr.execute("""
                UPDATE enrollments SET left_at = now()
                WHERE group_id = \(groupId.value) AND client_id = \(clientId.value) AND left_at IS NULL
                """)
        }
    }

    /// Проверяет, что группа [groupId] принадлежит организации из контекста.
    private func verifyGroupOwnership(_ groupId: GroupId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        let found = try await tr.first("SELECT 1 FROM groups WHERE id = \(groupId.value) AND org_id = \(ctx.orgId.value)") { _ in true }
        guard found != nil else {
            throw .domain("GROUP_NOT_FOUND", Messages.groupNotFound.localize(ctx))
        }
    }
}

/// Данные изменения состава группы для журнала аудита.
private struct EnrollmentAuditData: Encodable {
    /// Группа.
    let groupId: GroupId
    /// Затронутые клиенты.
    let clientIds: [ClientId]
}

/// Декоратор записей в группы: пишет в аудит создание и удаление записи для каждого клиента.
struct AuditEnrollments: Enrollments {
    /// Декорируемый репозиторий.
    let delegate: any Enrollments
    /// Журнал аудита.
    let audit: any AuditLog

    func add(_ groupId: GroupId, _ clientIds: [ClientId], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        try await delegate.add(groupId, clientIds, ctx: ctx, tr: tr)
        try await log(.create, groupId, clientIds, ctx: ctx, tr: tr)
    }

    func remove(_ groupId: GroupId, _ clientIds: [ClientId], ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        try await delegate.remove(groupId, clientIds, ctx: ctx, tr: tr)
        try await log(.delete, groupId, clientIds, ctx: ctx, tr: tr)
    }

    /// Пишет событие [action] записи `enrollment` по каждому из [clientIds].
    private func log(
        _ action: AuditActionType, _ groupId: GroupId, _ clientIds: [ClientId], ctx: EmployeeRequestContext, tr: Transaction
    ) async throws(AppError) {
        let data = try jsonString(EnrollmentAuditData(groupId: groupId, clientIds: clientIds))
        for clientId in clientIds {
            try await audit.log(AuditEvent(ctx: ctx, actionType: action, entityType: "enrollment", entityId: clientId.value, data: data), tr: tr)
        }
    }
}
