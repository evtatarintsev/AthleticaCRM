import Foundation

/// Read-проекция неоплаченных занятий клиента: проведённые занятия с отметкой «пришёл»
/// без действующего списания абонемента.
protocol UnpaidSessionsView: Sendable {
    /// Все неоплаченные посещения клиента [clientId] в группах текущего филиала; нет клиента — `CLIENT_NOT_FOUND`.
    func byClient(_ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> UnpaidSessionsResponse
}

/// [UnpaidSessionsView] одним SQL по журналу и списаниям; SQL перенесён из `DbUnpaidSessionsView.kt`.
struct DbUnpaidSessionsView: UnpaidSessionsView {
    func byClient(_ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> UnpaidSessionsResponse {
        let exists = try await tr.first("SELECT 1 FROM clients WHERE id = \(clientId.value) AND org_id = \(ctx.orgId.value)") { _ in true }
        guard exists != nil else {
            throw .domain("CLIENT_NOT_FOUND", Messages.clientNotFound.localize(ctx))
        }
        let rows = try await tr.list("""
            SELECT s.id, s.date::text, s.start_time::text, g.id AS group_id, g.name AS group_name
            FROM session_attendance a
            JOIN sessions s ON s.id = a.session_id
            JOIN groups g ON g.id = s.group_id AND g.branch_id = \(ctx.branchId.value)
            WHERE a.client_id = \(clientId.value) AND a.presence = 'present'
              AND s.org_id = \(ctx.orgId.value) AND s.status = 'completed'
              AND NOT EXISTS (SELECT 1 FROM membership_debits d
                              WHERE d.session_id = s.id AND d.client_id = a.client_id
                                AND d.reversed_at IS NULL)
            ORDER BY s.date, s.start_time, s.id
            """) { row in try row.decode((UUID, String, String, UUID, String).self) }
        var sessions: [UnpaidSessionSchema] = []
        for (id, date, startTime, groupId, groupName) in rows {
            guard let date = LocalDate.parse(date), let startTime = LocalTime.parse(startTime) else {
                throw .infrastructure("Некорректные дата или время занятия \(id)")
            }
            sessions.append(UnpaidSessionSchema(
                sessionId: SessionId(id), date: date, startTime: startTime, groupId: GroupId(groupId), groupName: groupName
            ))
        }
        return UnpaidSessionsResponse(sessions: sessions)
    }
}
