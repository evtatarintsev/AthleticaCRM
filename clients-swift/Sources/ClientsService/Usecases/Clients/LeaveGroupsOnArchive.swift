/// Обработчик `ClientArchived`: архивный клиент выходит из всех групп.
/// Каждое событие — в своей транзакции; ошибка откатывает её, и воркер повторит обработку.
struct LeaveGroupsOnArchive: Sendable {
    /// База данных.
    let db: Database
    /// Записи в группы.
    let enrollments: any Enrollments

    /// Завершает участия клиента из [event] организации [orgId].
    func clientArchived(_ orgId: OrgId, _ event: ClientArchived) async throws(AppError) {
        try await db.transaction { tr throws(AppError) in
            try await enrollments.leaveAllIfArchived(event.clientId, orgId: orgId, tr: tr)
        }
    }
}
