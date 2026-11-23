/// Снимок изменяемых полей клиента для журнала аудита.
private struct ClientAuditData: Encodable {
    /// Имя клиента.
    let name: String
    /// Аватарка.
    @Nullable var avatarId: UploadId?
    /// День рождения.
    @Nullable var birthday: LocalDate?
    /// Пол.
    let gender: Gender
    /// Источник клиента.
    @Nullable var leadSourceId: LeadSourceId?
}

/// Снимок документа клиента для журнала аудита.
private struct ClientDocAuditData: Encodable {
    /// Загруженный файл документа.
    let uploadId: UploadId
    /// Название документа.
    let name: String
}

/// Декоратор активного клиента: копит события аудита изменений и пишет их после [save].
struct AuditActiveClient: ActiveClient {
    /// Декорируемый клиент.
    let client: any ActiveClient
    /// Журнал аудита.
    let audit: any AuditLog
    /// Накопленные события, записываемые при сохранении.
    var auditEvents: [AuditEvent] = []

    var data: ClientData { client.data }

    func save(tr: Transaction) async throws(AppError) {
        try await client.save(tr: tr)
        for event in auditEvents {
            try await audit.log(event, tr: tr)
        }
    }

    func archive(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        try await client.archive(ctx: ctx, tr: tr)
        try await audit.log(AuditEvent(ctx: ctx, actionType: .update, entityType: "client", entityId: data.id.value, data: #"{"state":"ARCHIVED"}"#), tr: tr)
    }

    func attachDoc(_ doc: ClientDoc, ctx: EmployeeRequestContext) throws(AppError) -> any ActiveClient {
        let payload = try jsonString(ClientDocAuditData(uploadId: doc.uploadId, name: doc.name))
        let event = AuditEvent(ctx: ctx, actionType: .create, entityType: "client_doc", entityId: doc.id.value, data: payload)
        return with(try client.attachDoc(doc, ctx: ctx), event)
    }

    func deleteDoc(_ docId: ClientDocId, ctx: EmployeeRequestContext) throws(AppError) -> any ActiveClient {
        guard let doc = client.data.docs.first(where: { $0.id == docId }) else {
            throw .domain("DOC_NOT_FOUND", Messages.uploadNotFound.localize(ctx))
        }
        let payload = try jsonString(ClientDocAuditData(uploadId: doc.uploadId, name: doc.name))
        let event = AuditEvent(ctx: ctx, actionType: .delete, entityType: "client_doc", entityId: doc.id.value, data: payload)
        return with(try client.deleteDoc(docId, ctx: ctx), event)
    }

    func withNew(_ fields: ClientFields, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ActiveClient {
        let payload = try jsonString(ClientAuditData(
            name: fields.name.value,
            avatarId: fields.avatarId,
            birthday: fields.birthday,
            gender: fields.gender,
            leadSourceId: fields.leadSourceId
        ))
        let event = AuditEvent(ctx: ctx, actionType: .update, entityType: "client", entityId: data.id.value, data: payload)
        return with(try await client.withNew(fields, ctx: ctx, tr: tr), event)
    }

    /// Декоратор над [changed] с добавленным событием [event].
    private func with(_ changed: any ActiveClient, _ event: AuditEvent) -> AuditActiveClient {
        AuditActiveClient(client: changed, audit: audit, auditEvents: auditEvents + [event])
    }
}

/// Декоратор архивного клиента: пишет в аудит восстановление.
struct AuditArchivedClient: ArchivedClient {
    /// Декорируемый клиент.
    let client: any ArchivedClient
    /// Журнал аудита.
    let audit: any AuditLog

    var data: ClientData { client.data }

    func restore(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        try await client.restore(ctx: ctx, tr: tr)
        try await audit.log(AuditEvent(ctx: ctx, actionType: .update, entityType: "client", entityId: data.id.value, data: #"{"state":"ACTIVE"}"#), tr: tr)
    }
}

/// Декоратор репозитория: выдаёт клиентов в аудит-обёртках и пишет в аудит создание.
struct AuditClients: Clients {
    /// Декорируемый репозиторий.
    let delegate: any Clients
    /// Журнал аудита.
    let audit: any AuditLog

    func byId(_ id: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> ClientInState {
        switch try await delegate.byId(id, ctx: ctx, tr: tr) {
        case let .active(client): .active(AuditActiveClient(client: client, audit: audit))
        case let .archived(client): .archived(AuditArchivedClient(client: client, audit: audit))
        }
    }

    func new(_ id: ClientId, _ fields: ClientFields, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ActiveClient {
        let client = try await delegate.new(id, fields, ctx: ctx, tr: tr)
        let payload = try jsonString(["id": id.description, "name": fields.name.value])
        try await audit.log(AuditEvent(ctx: ctx, actionType: .create, entityType: "client", entityId: id.value, data: payload), tr: tr)
        return AuditActiveClient(client: client, audit: audit)
    }
}
