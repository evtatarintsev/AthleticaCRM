/// Баланс личного счёта клиента (аванс минус долг) из главной книги.
protocol ClientBalanceView: Sendable {
    /// Баланс клиента [clientId]; нет такого клиента — `CLIENT_NOT_FOUND`.
    func byClient(_ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> Money
}

/// [ClientBalanceView] одним SQL по счетам аванса и долга клиента.
struct DbClientBalanceView: ClientBalanceView {
    func byClient(_ clientId: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> Money {
        let balance = try await tr.first("""
            SELECT (-COALESCE(SUM(p.amount), 0))::text FROM clients c
            LEFT JOIN ledger_accounts a ON a.client_id = c.id AND a.org_id = c.org_id AND a.kind IN ('advance', 'receivable')
            LEFT JOIN ledger_postings p ON p.account_id = a.id
            WHERE c.id = \(clientId.value) AND c.org_id = \(ctx.orgId.value) GROUP BY c.id
            """) { row in try row.decode(String.self) }
        guard let balance else {
            throw .domain("CLIENT_NOT_FOUND", Messages.clientNotFound.localize(ctx))
        }
        return try Money.fromDb(balance, ctx.currency)
    }
}
