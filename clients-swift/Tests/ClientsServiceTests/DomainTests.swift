import Foundation
import Testing
@testable import ClientsService

/// Дата из строки `YYYY-MM-DD` для тестов.
private func date(_ text: String) -> LocalDate {
    LocalDate.parse(text) ?? LocalDate(year: 0, month: 0, day: 0)
}

/// Тестовый контекст сотрудника.
private let ctx = EmployeeRequestContext(
    userId: .new(), orgId: .new(), branchId: .new(), employeeId: .new(),
    username: "test", lang: .ru, clientIp: nil, currency: .RUB
)

@Suite struct ActiveStatusTests {
    @Test func `в сроке за неделю до конца и с последним занятием — заканчивается по обеим причинам`() {
        let status = ActiveStatus.of(startDate: date("2026-11-01"), endDate: date("2026-11-30"), sessionsRemaining: 1, date: date("2026-11-23"))
        #expect(status.termPosition == .IN_TERM)
        #expect(status.endingSoon == [.BY_DATE, .BY_SESSIONS])
        #expect(!status.closePending)
    }

    @Test func `срок вышел — к закрытию, признака «заканчивается» нет`() {
        let status = ActiveStatus.of(startDate: date("2026-10-01"), endDate: date("2026-10-31"), sessionsRemaining: 5, date: date("2026-11-23"))
        #expect(status.termPosition == .EXPIRED)
        #expect(status.endingSoon.isEmpty)
        #expect(status.closePending)
    }

    @Test func `остаток ноль — к закрытию, безлимит — нет`() {
        let range = (date("2026-11-01"), date("2026-12-31"))
        #expect(ActiveStatus.of(startDate: range.0, endDate: range.1, sessionsRemaining: 0, date: date("2026-11-23")).closePending)
        #expect(!ActiveStatus.of(startDate: range.0, endDate: range.1, sessionsRemaining: nil, date: date("2026-11-23")).closePending)
    }
}

@Suite struct ClientContactsTests {
    /// Контакт типа [type] со значением [value].
    private func contact(_ type: ContactType, _ value: String, primary: Bool = false, label: String? = nil) -> ClientContact {
        ClientContact(id: .new(), clientId: ClientId(UUID()), type: type, value: value, label: label, isPrimary: primary)
    }

    @Test func `контакты группируются по типу, основной — первым, комментарий обрезается`() throws {
        let stored = try [
            contact(.PHONE, "1"),
            contact(.EMAIL, "a@b"),
            contact(.PHONE, "2", primary: true, label: "  мама "),
            contact(.EMAIL, "c@d", label: "   "),
        ].normalized()
        #expect(stored.map(\.value) == ["2", "1", "a@b", "c@d"])
        #expect(stored.map(\.isPrimary) == [true, false, true, false])
        #expect(stored[0].label == "мама")
        #expect(stored[3].label == nil)
    }

    @Test func `два основных одного типа — ошибка`() {
        #expect(throws: AppError.domain("CONTACT_PRIMARY_DUPLICATE", "У клиента может быть только один основной контакт каждого типа")) {
            try [contact(.PHONE, "1", primary: true), contact(.PHONE, "2", primary: true)].normalized()
        }
    }
}

@Suite struct CustomFieldValuesTests {
    /// Ключ поля из заведомо валидной строки [raw].
    private func key(_ raw: String) -> CustomFieldKey {
        try! CustomFieldKey.from(raw)
    }

    private var values: CustomFieldValues {
        CustomFieldValues(definitions: [
            CustomFieldDefinition(fieldKey: key("size"), isRequired: true, kind: .select(options: ["S", "M"])),
            CustomFieldDefinition(fieldKey: key("note"), isRequired: false, kind: .text(minLength: nil, maxLength: 3)),
        ])
    }

    @Test func `создание без обязательного поля — ошибка`() {
        #expect(throws: AppError.domain("CUSTOM_FIELD_REQUIRED", "Заполните обязательное поле size")) {
            try values.forCreation([.text(key("note"), "ok")])
        }
    }

    @Test func `вариант вне списка и длинный текст отклоняются`() {
        #expect(throws: AppError.self) { try values.forCreation([.select(key("size"), "XL")]) }
        #expect(throws: AppError.self) { try values.forCreation([.select(key("size"), "S"), .text(key("note"), "long")]) }
    }

    @Test func `неизменённое значение не перепроверяется при замене`() throws {
        let previous: [CustomFieldValue] = [.select(key("size"), "S"), .text(key("note"), "legacy long")]
        let replaced = try values.forReplacement(previous: previous, values: previous)
        #expect(replaced == previous)
    }

    @Test func `невалидный ключ поля отклоняется`() {
        #expect(throws: AppError.self) { try CustomFieldKey.from("Bad-Key") }
    }
}

@Suite struct ClientListRequestTests {
    @Test func `пагинация ограничивается, пустой поиск — без поиска`() throws {
        var request = ClientListRequest()
        request.limit = 1000
        request.offset = -5
        request.name = "   "
        let query = try request.query(today: date("2026-11-23"), ctx: ctx)
        #expect(query.limit == 100)
        #expect(query.offset == 0)
        #expect(query.search == nil)
    }

    @Test func `NEXT_BIRTHDAY без фильтра по дню рождения и повтор поля — ошибка`() {
        var nextBirthday = ClientListRequest()
        nextBirthday.sortField = .NEXT_BIRTHDAY
        #expect(throws: AppError.self) { try nextBirthday.query(today: date("2026-11-23"), ctx: ctx) }

        var repeated = ClientListRequest()
        repeated.sort = [ClientSort(field: .NAME, direction: .Asc), ClientSort(field: .NAME, direction: .Desc)]
        #expect(throws: AppError.self) { try repeated.query(today: date("2026-11-23"), ctx: ctx) }
    }

    @Test func `пустое тело запроса списка декодируется с умолчаниями`() throws {
        let request = try JSONDecoder().decode(ClientListRequest.self, from: Data("{}".utf8))
        #expect(request.limit == 50 && request.sortField == .NAME && !request.archived)
    }

    @Test func `цифры телефона для поиска: 8 → 7, меньше трёх цифр — не телефон`() {
        #expect(DbClientListView.phoneDigits("8 (900) 123-45-67") == "79001234567")
        #expect(DbClientListView.phoneDigits("Анна 12") == nil)
    }
}

@Suite struct ContractTests {
    @Test func `nullable-поля кодируются явным null, UUID — в нижнем регистре`() throws {
        let id = ClientContactId(UUID(uuidString: "0192F0A0-0000-7000-8000-000000000001") ?? UUID())
        let json = String(decoding: try apiJson.encode(ClientContactSchema(id: id, type: .PHONE, value: "1", label: nil, isPrimary: true)), as: UTF8.self)
        #expect(json.contains(#""label":null"#))
        #expect(json.contains("0192f0a0-0000-7000-8000-000000000001"))
    }

    @Test func `пустое имя клиента не десериализуется`() {
        #expect(throws: (any Error).self) {
            try JSONDecoder().decode(CreateClientRequest.self, from: Data(#"{"id":"0192f0a0-0000-7000-8000-000000000001","name":"  ","gender":"MALE"}"#.utf8))
        }
    }

    @Test func `именованные параметры: повтор переиспользует номер, приведение типа не трогается`() throws {
        let query = try NamedQuery("SELECT :a::int, :b, :a").bind("a", 1).bind("b", nil as String?).compiled()
        #expect(query.sql == "SELECT $1::int, $2, $1")
        #expect(query.binds.count == 2)
    }

    @Test func `сумма из NUMERIC переводится в минорные единицы`() throws {
        #expect(try Money.fromDb("-1200.50", .RUB) == Money(minorUnits: -120_050, currency: .RUB))
    }
}
