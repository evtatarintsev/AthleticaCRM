import Foundation
import PostgresNIO

/// Активный клиент поверх PostgreSQL. Все запросы скоупятся по `org_id`.
struct DbActiveClient: ActiveClient {
    let data: ClientData
    /// Организация клиента.
    let orgId: OrgId
    /// Определения кастомных полей для проверки новых значений.
    let definitions: any CustomFieldDefinitions

    func save(tr: Transaction) async throws(AppError) {
        let customFields = try jsonString(data.customFields)
        let updated = try await tr.execute("""
            UPDATE clients
            SET name = \(data.name), avatar_id = \(data.avatarId?.value), birthday = \(data.birthday?.description)::date,
                gender = \(data.gender.rawValue)::gender, lead_source_id = \(data.leadSourceId?.value),
                custom_fields = \(customFields)::jsonb
            WHERE id = \(data.id.value) AND org_id = \(orgId.value) AND state = 'ACTIVE'
            RETURNING 1
            """)
        guard updated == 1 else {
            throw ClientErrors.archived
        }
        try await tr.execute("DELETE FROM client_docs WHERE client_id = \(data.id.value)")
        for doc in data.docs {
            try await tr.execute("""
                INSERT INTO client_docs (id, client_id, org_id, upload_id, name, created_at)
                VALUES (\(doc.id.value), \(data.id.value), \(orgId.value), \(doc.uploadId.value), \(doc.name), \(doc.createdAt))
                """)
        }
    }

    func archive(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        let updated = try await tr.execute("""
            UPDATE clients SET state = 'ARCHIVED'
            WHERE id = \(data.id.value) AND org_id = \(ctx.orgId.value) AND state = 'ACTIVE'
            RETURNING 1
            """)
        guard updated == 1 else {
            throw ClientErrors.alreadyArchived
        }
    }

    func attachDoc(_ doc: ClientDoc, ctx: EmployeeRequestContext) throws(AppError) -> any ActiveClient {
        var changed = data
        changed.docs.append(doc)
        return DbActiveClient(data: changed, orgId: orgId, definitions: definitions)
    }

    func deleteDoc(_ docId: ClientDocId, ctx: EmployeeRequestContext) throws(AppError) -> any ActiveClient {
        var changed = data
        changed.docs.removeAll { $0.id == docId }
        return DbActiveClient(data: changed, orgId: orgId, definitions: definitions)
    }

    func withNew(_ fields: ClientFields, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ActiveClient {
        let current = try await definitions.all(.client, ctx: ctx, tr: tr)
        var changed = data
        changed.name = fields.name.value
        changed.avatarId = fields.avatarId
        changed.birthday = fields.birthday
        changed.gender = fields.gender
        changed.leadSourceId = fields.leadSourceId
        changed.customFields = try CustomFieldValues(definitions: current)
            .forReplacement(previous: data.customFields, values: fields.customFields)
        return DbActiveClient(data: changed, orgId: orgId, definitions: definitions)
    }
}

extension ActiveClient {
    /// Блокирует строку клиента до конца транзакции; клиент ушёл в архив — `CLIENT_ARCHIVED`.
    func lockForWrite(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        let active = try await tr.first("""
            SELECT 1 FROM clients WHERE id = \(data.id.value) AND org_id = \(ctx.orgId.value) AND state = 'ACTIVE' FOR UPDATE
            """) { _ in true }
        guard active != nil else {
            throw ClientErrors.archived
        }
    }
}

/// Архивный клиент поверх PostgreSQL; поддерживает только восстановление.
struct DbArchivedClient: ArchivedClient {
    let data: ClientData

    func restore(ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) {
        let updated = try await tr.execute("""
            UPDATE clients SET state = 'ACTIVE'
            WHERE id = \(data.id.value) AND org_id = \(ctx.orgId.value) AND state = 'ARCHIVED'
            RETURNING 1
            """)
        guard updated == 1 else {
            throw ClientErrors.notArchived
        }
    }
}

/// Репозиторий клиентов поверх PostgreSQL.
struct DbClients: Clients {
    /// Определения кастомных полей для проверки значений.
    let definitions: any CustomFieldDefinitions

    func byId(_ id: ClientId, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> ClientInState {
        let scalar = try await tr.first("""
            SELECT name, created_at, avatar_id, birthday::text, gender::text, lead_source_id, custom_fields::text, state::text
            FROM clients
            WHERE id = \(id.value) AND org_id = \(ctx.orgId.value)
            """) { row in
            try row.decode((String, Date, UUID?, String?, String, UUID?, String, String).self)
        }
        guard let (name, createdAt, avatarId, birthday, gender, leadSourceId, customFields, state) = scalar else {
            throw .domain("CLIENT_NOT_FOUND", Messages.clientNotFound.localize(ctx))
        }
        let groups = try await tr.list("""
            SELECT g.id, g.name
            FROM enrollments e JOIN groups g ON g.id = e.group_id
            WHERE e.client_id = \(id.value) AND e.left_at IS NULL
            """) { row in
            let (groupId, groupName) = try row.decode((UUID, String).self)
            return ClientGroup(id: GroupId(groupId), name: groupName)
        }
        let docs = try await tr.list("""
            SELECT id, upload_id, name, created_at
            FROM client_docs
            WHERE client_id = \(id.value)
            ORDER BY created_at DESC
            """) { row in
            let (docId, uploadId, docName, docCreatedAt) = try row.decode((UUID, UUID, String, Date).self)
            return ClientDoc(id: ClientDocId(docId), uploadId: UploadId(uploadId), name: docName, createdAt: docCreatedAt)
        }
        let data = ClientData(
            id: id,
            name: name,
            createdAt: createdAt,
            avatarId: avatarId.map(UploadId.init),
            birthday: birthday.flatMap(LocalDate.parse),
            gender: Gender(rawValue: gender) ?? .MALE,
            groups: groups,
            docs: docs,
            leadSourceId: leadSourceId.map(LeadSourceId.init),
            customFields: try decodeJson([CustomFieldValue].self, customFields)
        )
        return state == "ARCHIVED"
            ? .archived(DbArchivedClient(data: data))
            : .active(DbActiveClient(data: data, orgId: ctx.orgId, definitions: definitions))
    }

    func new(_ id: ClientId, _ fields: ClientFields, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> any ActiveClient {
        let validated = try CustomFieldValues(definitions: try await definitions.all(.client, ctx: ctx, tr: tr))
            .forCreation(fields.customFields)
        let customFields = try jsonString(validated)
        let alreadyExists = DomainError(code: "CLIENT_ALREADY_EXISTS", message: Messages.clientAlreadyExists.localize(ctx))
        let createdAt = try await tr.first(
            """
            INSERT INTO clients (id, org_id, name, avatar_id, birthday, gender, lead_source_id, custom_fields)
            VALUES (\(id.value), \(ctx.orgId.value), \(fields.name.value), \(fields.avatarId?.value),
                    \(fields.birthday?.description)::date, \(fields.gender.rawValue)::gender,
                    \(fields.leadSourceId?.value), \(customFields)::jsonb)
            RETURNING created_at
            """,
            onViolation: [
                "clients_pkey": alreadyExists,
                "clients_lead_source_id_fkey": DomainError(code: "LEAD_SOURCE_NOT_FOUND", message: Messages.leadSourceNotFound.localize(ctx)),
            ]
        ) { row in try row.decode(Date.self) }
        guard let createdAt else {
            throw .domain(alreadyExists)
        }
        let data = ClientData(
            id: id,
            name: fields.name.value,
            createdAt: createdAt,
            avatarId: fields.avatarId,
            birthday: fields.birthday,
            gender: fields.gender,
            groups: [],
            docs: [],
            leadSourceId: fields.leadSourceId,
            customFields: validated
        )
        return DbActiveClient(data: data, orgId: ctx.orgId, definitions: definitions)
    }
}
