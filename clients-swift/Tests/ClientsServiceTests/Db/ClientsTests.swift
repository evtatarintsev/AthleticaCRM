import Foundation
import Testing
@testable import ClientsService

/// Поля клиента для создания и редактирования.
func fields(
    _ raw: String, birthday: LocalDate? = nil, gender: Gender = .MALE, avatarId: UploadId? = nil, customFields: [CustomFieldValue] = []
) -> ClientFields {
    ClientFields(name: name(raw), avatarId: avatarId, birthday: birthday, gender: gender, leadSourceId: nil, customFields: customFields)
}

/// Репозиторий клиентов без аудита, как `DbClients` в Kotlin-тестах.
let plainClients = DbClients(definitions: DbCustomFieldDefinitions(), events: DomainEventBus())

/// Активный клиент [id] организации [org].
func active(_ org: TestOrg, _ id: ClientId, clients: any Clients = plainClients) async throws(AppError) -> any ActiveClient {
    try await org.tx { ctx, tr throws(AppError) in try await clients.activeById(id, ctx: ctx, tr: tr) }
}

/// DB-тесты репозитория клиентов — порт `DbClientsTest.kt`.
@Suite struct ClientsTests {
    private let clients = plainClients

    /// Клиент [id] в любом состоянии.
    private func byId(_ org: TestOrg, _ id: ClientId) async throws(AppError) -> ClientInState {
        try await org.tx { ctx, tr throws(AppError) in try await clients.byId(id, ctx: ctx, tr: tr) }
    }

    @Test func `new создаёт клиента с переданными полями`() async throws {
        let org = try await TestOrg.make()
        let avatar = try await org.upload()
        let id = ClientId.new()

        let created = try await org.tx { ctx, tr throws(AppError) in
            try await clients.new(id, fields("  Анна ", birthday: LocalDate(year: 2010, month: 5, day: 3), gender: .FEMALE, avatarId: avatar), ctx: ctx, tr: tr)
        }

        #expect(created.data.id == id && created.data.name == "Анна")
        let stored = try await byId(org, id).data
        #expect(stored.name == "Анна" && stored.gender == .FEMALE && stored.avatarId == avatar)
        #expect(stored.birthday == LocalDate(year: 2010, month: 5, day: 3))
        #expect(try await org.int("SELECT COUNT(*) FROM clients WHERE id = \(id.value) AND org_id = \(org.orgId.value)") == 1)
    }

    @Test func `new с занятым id — CLIENT_ALREADY_EXISTS`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")

        await expectDomainError("CLIENT_ALREADY_EXISTS") { () throws(AppError) in
            _ = try await org.tx { ctx, tr throws(AppError) in try await clients.new(id, fields("Дубль"), ctx: ctx, tr: tr) }
        }
    }

    @Test func `byId: нет клиента и клиент другой организации — CLIENT_NOT_FOUND`() async throws {
        let org = try await TestOrg.make()
        let other = try await TestOrg.make()
        let foreign = try await other.client("Чужой")

        for id in [ClientId.new(), foreign] {
            await expectDomainError("CLIENT_NOT_FOUND") { () throws(AppError) in _ = try await byId(org, id) }
        }
    }

    @Test func `byId загружает группы и документы клиента`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")
        let left = try await org.group("Ушла")
        try await org.enroll(id, try await org.group("Бокс"))
        try await org.enroll(id, try await org.group("Айкидо"))
        try await org.enroll(id, left)
        try await org.sql("UPDATE enrollments SET left_at = now() WHERE group_id = \(left.value)")
        let upload = try await org.upload()
        try await org.sql("""
            INSERT INTO client_docs (client_id, org_id, upload_id, name) VALUES (\(id.value), \(org.orgId.value), \(upload.value), 'Справка')
            """)

        let data = try await byId(org, id).data

        #expect(Set(data.groups.map(\.name)) == ["Айкидо", "Бокс"])
        #expect(data.docs.map(\.name) == ["Справка"] && data.docs.map(\.uploadId) == [upload])
    }

    @Test func `save обновляет поля и не трогает другого клиента`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")
        let neighbour = try await org.client("Борис")

        try await org.tx { ctx, tr throws(AppError) in
            try await clients.activeById(id, ctx: ctx, tr: tr)
                .withNew(fields("Анна Петровна", birthday: LocalDate(year: 2001, month: 2, day: 3), gender: .FEMALE), ctx: ctx, tr: tr)
                .save(tr: tr)
        }

        let data = try await byId(org, id).data
        #expect(data.name == "Анна Петровна" && data.gender == .FEMALE && data.birthday == LocalDate(year: 2001, month: 2, day: 3))
        #expect(try await byId(org, neighbour).data.name == "Борис")
    }

    @Test func `save синхронизирует документы: добавление, удаление, несколько`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")
        let (first, second, third) = (try await org.upload(), try await org.upload(), try await org.upload())
        let docA = ClientDoc.new(uploadId: first, name: "A", id: .new())
        let docB = ClientDoc.new(uploadId: second, name: "B", id: .new())
        let docC = ClientDoc.new(uploadId: third, name: "C", id: .new())

        try await org.tx { ctx, tr throws(AppError) in
            try await clients.activeById(id, ctx: ctx, tr: tr)
                .attachDoc(docA, ctx: ctx).attachDoc(docB, ctx: ctx).attachDoc(docC, ctx: ctx).save(tr: tr)
        }
        #expect(Set(try await byId(org, id).data.docs.map(\.name)) == ["A", "B", "C"])

        try await org.tx { ctx, tr throws(AppError) in
            try await clients.activeById(id, ctx: ctx, tr: tr).deleteDoc(docB.id, ctx: ctx).save(tr: tr)
        }
        #expect(Set(try await byId(org, id).data.docs.map(\.name)) == ["A", "C"])
    }

    @Test func `редактирование сохраняет старый вариант выбора, но требует новое обязательное поле`() async throws {
        let org = try await TestOrg.make()
        let sport = try CustomFieldKey.from("sport")
        let yoga = CustomFieldValue.select(sport, "Йога")
        try await org.customField("sport", "Спорт", type: "select", required: true, config: #"{"options":["Йога","Танцы"]}"#)
        let first = ClientId.new()
        let second = ClientId.new()
        try await org.tx { ctx, tr throws(AppError) in
            _ = try await clients.new(first, fields("Первый", customFields: [yoga]), ctx: ctx, tr: tr)
            _ = try await clients.new(second, fields("Второй", customFields: [.select(sport, "Танцы")]), ctx: ctx, tr: tr)
        }
        try await org.customField("sport", "Спорт", type: "select", required: true, config: #"{"options":["Танцы"]}"#)

        try await org.tx { ctx, tr throws(AppError) in
            try await clients.activeById(first, ctx: ctx, tr: tr).withNew(fields("Первый обновлён", customFields: [yoga]), ctx: ctx, tr: tr).save(tr: tr)
        }
        #expect(try await byId(org, first).data.customFields == [yoga])

        await expectDomainError("CUSTOM_FIELD_REQUIRED") { () throws(AppError) in
            try await org.tx { ctx, tr throws(AppError) in
                try await clients.activeById(first, ctx: ctx, tr: tr).withNew(fields("Первый"), ctx: ctx, tr: tr).save(tr: tr)
            }
        }
        await expectDomainError("CUSTOM_FIELD_INVALID_OPTION") { () throws(AppError) in
            try await org.tx { ctx, tr throws(AppError) in
                try await clients.activeById(second, ctx: ctx, tr: tr).withNew(fields("Второй", customFields: [yoga]), ctx: ctx, tr: tr).save(tr: tr)
            }
        }
    }

    @Test func `archive переводит в архив, restore возвращает, ошибки состояния`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")
        guard case .active = try await byId(org, id) else {
            Issue.record("Новый клиент должен быть активным")
            return
        }

        try await org.tx { ctx, tr throws(AppError) in try await clients.archive(id, ctx: ctx, tr: tr) }
        guard case .archived = try await byId(org, id) else {
            Issue.record("Клиент должен быть в архиве")
            return
        }
        await expectDomainError("CLIENT_ALREADY_ARCHIVED") { () throws(AppError) in
            try await org.tx { ctx, tr throws(AppError) in try await clients.archive(id, ctx: ctx, tr: tr) }
        }
        await expectDomainError("CLIENT_ARCHIVED") { () throws(AppError) in _ = try await active(org, id) }

        try await org.tx { ctx, tr throws(AppError) in try await clients.restore(id, ctx: ctx, tr: tr) }
        guard case .active = try await byId(org, id) else {
            Issue.record("Клиент должен вернуться из архива")
            return
        }
        await expectDomainError("CLIENT_NOT_ARCHIVED") { () throws(AppError) in
            try await org.tx { ctx, tr throws(AppError) in try await clients.restore(id, ctx: ctx, tr: tr) }
        }
    }

    @Test func `запись профиля из снимка до архивации не проходит после неё`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")
        let stale = try await active(org, id)

        try await org.tx { ctx, tr throws(AppError) in try await clients.archive(id, ctx: ctx, tr: tr) }

        await expectDomainError("CLIENT_ARCHIVED") { () throws(AppError) in
            try await org.tx { ctx, tr throws(AppError) in try await stale.withNew(fields("Новое имя"), ctx: ctx, tr: tr).save(tr: tr) }
        }
        #expect(try await byId(org, id).data.name == "Анна")
    }

    @Test func `параллельная архивация одного клиента: успешна ровно одна`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")
        let audited = AuditClients(delegate: clients, audit: PostgresAuditLog())

        let results = await withTaskGroup(of: Bool.self) { group in
            for _ in 0..<2 {
                group.addTask {
                    do {
                        try await org.tx { ctx, tr throws(AppError) in try await audited.archive(id, ctx: ctx, tr: tr) }
                        return true
                    } catch {
                        return false
                    }
                }
            }
            return await group.reduce(into: []) { $0.append($1) }
        }

        #expect(results.filter { $0 }.count == 1)
        #expect(try await org.audit(entityId: id.value) == ["update:client"])
    }
}

/// DB-тесты декоратора аудита клиентов — порт `AuditClientsTest.kt` и `AuditDbClientsDocsTest.kt`.
@Suite struct AuditClientsTests {
    private let clients = AuditClients(delegate: plainClients, audit: PostgresAuditLog())

    @Test func `new пишет create, save после withNew — update`() async throws {
        let org = try await TestOrg.make()
        let id = ClientId.new()

        try await org.tx { ctx, tr throws(AppError) in
            _ = try await clients.new(id, fields("Анна"), ctx: ctx, tr: tr)
        }
        try await org.tx { ctx, tr throws(AppError) in
            try await clients.activeById(id, ctx: ctx, tr: tr).withNew(fields("Анна Петровна"), ctx: ctx, tr: tr).save(tr: tr)
        }

        #expect(try await org.audit(entityId: id.value) == ["create:client", "update:client"])
    }

    @Test func `byId без изменений не пишет аудит`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")

        try await org.tx { ctx, tr throws(AppError) in try await clients.activeById(id, ctx: ctx, tr: tr).save(tr: tr) }

        #expect(try await org.audit(entityId: id.value).isEmpty)
    }

    @Test func `attachDoc и deleteDoc пишут аудит документа`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")
        let doc = ClientDoc.new(uploadId: try await org.upload(), name: "Справка", id: .new())

        try await org.tx { ctx, tr throws(AppError) in try await clients.activeById(id, ctx: ctx, tr: tr).attachDoc(doc, ctx: ctx).save(tr: tr) }
        try await org.tx { ctx, tr throws(AppError) in try await clients.activeById(id, ctx: ctx, tr: tr).deleteDoc(doc.id, ctx: ctx).save(tr: tr) }

        #expect(try await org.audit(entityId: doc.id.value) == ["create:client_doc", "delete:client_doc"])
        #expect(try await org.int("SELECT COUNT(*) FROM client_docs WHERE client_id = \(id.value)") == 0)
    }

    @Test func `удаление несуществующего документа — DOC_NOT_FOUND`() async throws {
        let org = try await TestOrg.make()
        let id = try await org.client("Анна")

        await expectDomainError("DOC_NOT_FOUND") { () throws(AppError) in
            _ = try await org.tx { ctx, tr throws(AppError) in try await clients.activeById(id, ctx: ctx, tr: tr).deleteDoc(.new(), ctx: ctx) }
        }
    }
}

/// DB-тесты записей в группы — порт `AddClientsToGroupTest.kt`.
@Suite struct EnrollmentsTests {
    private let enrollments = AuditEnrollments(delegate: DbEnrollments(), audit: PostgresAuditLog())

    @Test func `add записывает нескольких клиентов и идемпотентен`() async throws {
        let org = try await TestOrg.make()
        let group = try await org.group("Бокс")
        let (anna, boris) = (try await org.client("Анна"), try await org.client("Борис"))

        try await org.tx { ctx, tr throws(AppError) in try await enrollments.add(group, [anna, boris], ctx: ctx, tr: tr) }
        try await org.tx { ctx, tr throws(AppError) in try await enrollments.add(group, [anna], ctx: ctx, tr: tr) }

        #expect(try await org.int("SELECT COUNT(*) FROM enrollments WHERE group_id = \(group.value)") == 2)
        #expect(try await org.audit(entityId: anna.value) == ["create:enrollment", "create:enrollment"])
    }

    @Test func `remove закрывает участие, повторная запись создаёт новый период`() async throws {
        let org = try await TestOrg.make()
        let group = try await org.group("Бокс")
        let anna = try await org.client("Анна")

        try await org.tx { ctx, tr throws(AppError) in try await enrollments.add(group, [anna], ctx: ctx, tr: tr) }
        try await org.tx { ctx, tr throws(AppError) in try await enrollments.remove(group, [anna], ctx: ctx, tr: tr) }
        #expect(try await org.activeEnrollments(anna) == 0)

        try await org.tx { ctx, tr throws(AppError) in try await enrollments.add(group, [anna], ctx: ctx, tr: tr) }
        #expect(try await org.activeEnrollments(anna) == 1)
        #expect(try await org.int("SELECT COUNT(*) FROM enrollments WHERE client_id = \(anna.value)") == 2)
    }

    @Test func `группа не найдена или чужая — GROUP_NOT_FOUND, несуществующий клиент — CLIENT_NOT_FOUND`() async throws {
        let org = try await TestOrg.make()
        let other = try await TestOrg.make()
        let anna = try await org.client("Анна")
        let foreignGroup = try await other.group("Чужая")

        for group in [GroupId.new(), foreignGroup] {
            await expectDomainError("GROUP_NOT_FOUND") { () throws(AppError) in
                try await org.tx { ctx, tr throws(AppError) in try await enrollments.add(group, [anna], ctx: ctx, tr: tr) }
            }
        }
        let group = try await org.group("Своя")
        await expectDomainError("CLIENT_NOT_FOUND") { () throws(AppError) in
            try await org.tx { ctx, tr throws(AppError) in try await enrollments.add(group, [.new()], ctx: ctx, tr: tr) }
        }
    }

    @Test func `addToGroups атомарна: ошибка на одной группе не записывает ни в одну`() async throws {
        let org = try await TestOrg.make()
        let anna = try await org.client("Анна")
        let (first, second) = (try await org.group("Первая"), try await org.group("Вторая"))

        try await org.tx { ctx, tr throws(AppError) in try await enrollments.addToGroups(anna, [first, second], ctx: ctx, tr: tr) }
        #expect(try await org.activeEnrollments(anna) == 2)

        let boris = try await org.client("Борис")
        await expectDomainError("GROUP_NOT_FOUND") { () throws(AppError) in
            try await org.tx { ctx, tr throws(AppError) in try await enrollments.addToGroups(boris, [first, .new()], ctx: ctx, tr: tr) }
        }
        #expect(try await org.activeEnrollments(boris) == 0)
    }
}
