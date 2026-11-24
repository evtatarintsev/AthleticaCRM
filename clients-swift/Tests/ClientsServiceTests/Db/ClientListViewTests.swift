import Foundation
import Testing
@testable import ClientsService

/// Дата для тестов.
private func day(_ year: Int, _ month: Int, _ day: Int) -> LocalDate { LocalDate(year: year, month: month, day: day) }

/// «Сегодня» организации для проекции.
private let today = day(2026, 6, 20)

/// Сортировка по колонке [column].
private func by(_ column: ClientSortColumn, ascending: Bool = true) -> ClientSortCriterion {
    ClientSortCriterion(column: column, ascending: ascending)
}

/// DB-тесты проекции списка клиентов — порт `DbClientListViewTest.kt`.
@Suite struct ClientListViewTests {
    private let view = DbClientListView()

    /// Страница проекции [query] глазами владельца [org].
    private func page(_ org: TestOrg, _ query: ClientListQuery = ClientListQuery(today: today)) async throws -> ClientListResponse {
        try await org.tx { ctx, tr throws(AppError) in try await view.page(query, ctx: ctx, tr: tr) }
    }

    /// Имена клиентов страницы [query].
    private func names(_ org: TestOrg, _ query: ClientListQuery) async throws -> [String] {
        try await page(org, query).clients.map(\.name)
    }

    @Test func `пустая организация — пустая выборка`() async throws {
        let response = try await page(try await TestOrg.make())
        #expect(response.clients.isEmpty && response.total == 0)
    }

    @Test func `клиенты организации с total и изоляцией`() async throws {
        let org = try await TestOrg.make()
        let other = try await TestOrg.make()
        try await org.client("Борис")
        try await org.client("Анна")
        try await other.client("Чужой")

        let response = try await page(org)

        #expect(response.clients.map(\.name) == ["Анна", "Борис"] && response.total == 2)
    }

    @Test func `фильтры по полу, долгу, группе, отсутствию группы и архиву`() async throws {
        let org = try await TestOrg.make()
        let group = try await org.group("Бокс")
        let anna = try await org.client("Анна", gender: .FEMALE)
        let boris = try await org.client("Борис")
        try await org.client("Вера", gender: .FEMALE, archived: true)
        try await org.enroll(anna, group)
        try await org.balance(boris, Money(minorUnits: -500, currency: .RUB))
        try await org.balance(anna, Money(minorUnits: 300, currency: .RUB))

        #expect(try await names(org, ClientListQuery(today: today, gender: .FEMALE)) == ["Анна"])
        #expect(try await names(org, ClientListQuery(today: today, hasDebt: true)) == ["Борис"])
        #expect(try await names(org, ClientListQuery(today: today, groupId: group)) == ["Анна"])
        #expect(try await names(org, ClientListQuery(today: today, noGroup: true)) == ["Борис"])
        #expect(try await names(org, ClientListQuery(today: today, archived: true)) == ["Вера"])
    }

    @Test func `баланс по последней проводке, без журнала — ноль, сортировка по балансу`() async throws {
        let org = try await TestOrg.make()
        let anna = try await org.client("Анна")
        let boris = try await org.client("Борис")
        try await org.client("Вера")
        try await org.balance(anna, Money(minorUnits: 100, currency: .RUB))
        try await org.balance(anna, Money(minorUnits: -200, currency: .RUB))
        try await org.balance(boris, Money(minorUnits: 50, currency: .RUB))

        let response = try await page(org, ClientListQuery(today: today, sort: [by(.balance)]))

        #expect(response.clients.map(\.name) == ["Анна", "Вера", "Борис"])
        #expect(response.clients.map(\.balance.minorUnits) == [-200, 0, 50])
    }

    @Test func `сортировки: имя по убыванию, дата рождения с null в конце, составная`() async throws {
        let org = try await TestOrg.make()
        try await org.client("Анна", birthday: day(2000, 5, 1))
        try await org.client("Борис")
        try await org.client("Вера", birthday: day(1990, 5, 1))
        try await org.client("Анна", birthday: day(1980, 5, 1))

        #expect(try await names(org, ClientListQuery(today: today, sort: [by(.name, ascending: false)])) == ["Вера", "Борис", "Анна", "Анна"])
        #expect(try await names(org, ClientListQuery(today: today, sort: [by(.birthday)])) == ["Анна", "Вера", "Анна", "Борис"])
        let composite = try await page(org, ClientListQuery(today: today, sort: [by(.name), by(.birthday, ascending: false)]))
        #expect(composite.clients.map(\.birthday) == [day(2000, 5, 1), day(1980, 5, 1), nil, day(1990, 5, 1)])
    }

    @Test func `ближайшие дни рождения: переход года, стабильный порядок, страницы`() async throws {
        let org = try await TestOrg.make()
        let yana = try await org.client("Яна", birthday: day(2000, 12, 30))
        let anna1 = try await org.client("Анна", birthday: day(2010, 12, 31))
        let anna2 = try await org.client("Анна", birthday: day(1990, 12, 31))
        let boris = try await org.client("Борис", birthday: day(1980, 12, 31))
        let january = try await org.client("Александр", birthday: day(1970, 1, 1))
        try await org.client("Без даты")
        let annas = [anna1, anna2].sorted { $0.description < $1.description }
        var query = ClientListQuery(today: today, birthday: DateRange(from: day(2026, 12, 30), to: day(2027, 1, 6)), sort: [by(.nextBirthday), by(.name)], limit: 2)

        let first = try await page(org, query)
        #expect(first.clients.map(\.id) == [yana, annas[0]] && first.total == 5)
        var all: [ClientId] = []
        for offset in stride(from: 0, through: 4, by: 2) {
            query.offset = offset
            all += try await page(org, query).clients.map(\.id)
        }
        #expect(all == [yana] + annas + [boris, january])

        query.offset = 0
        query.limit = nil
        query.sort = [by(.nextBirthday, ascending: false), by(.name)]
        #expect(try await page(org, query).clients.map(\.id) == [january] + annas + [boris, yana])
    }

    @Test func `29 февраля сохраняет точный месяц и день`() async throws {
        let org = try await TestOrg.make()
        try await org.client("Последний", birthday: day(1990, 3, 1))
        try await org.client("Первый", birthday: day(1990, 2, 28))
        try await org.client("Второй", birthday: day(2000, 2, 29))
        var query = ClientListQuery(today: today, birthday: DateRange(from: day(2028, 2, 28), to: day(2028, 3, 1)), sort: [by(.nextBirthday)])

        #expect(try await names(org, query) == ["Первый", "Второй", "Последний"])
        query.birthday = DateRange(from: day(2027, 3, 1), to: day(2027, 3, 1))
        #expect(try await names(org, query) == ["Последний"])
    }

    @Test func `фильтр по дню рождения: неделя, без даты, переход через год`() async throws {
        let org = try await TestOrg.make()
        try await org.client("Начало недели", birthday: day(1990, 6, 19))
        try await org.client("Конец недели", birthday: day(1990, 6, 25))
        try await org.client("За пределами", birthday: day(1990, 6, 26))
        try await org.client("Без ДР")
        try await org.client("31 декабря", birthday: day(1990, 12, 31))
        try await org.client("3 января", birthday: day(1990, 1, 3))

        let week = try await page(org, ClientListQuery(today: today, birthday: DateRange(from: day(2026, 6, 19), to: day(2026, 6, 25))))
        #expect(Set(week.clients.map(\.name)) == ["Начало недели", "Конец недели"] && week.total == 2)
        let newYear = try await names(org, ClientListQuery(today: today, birthday: DateRange(from: day(2026, 12, 29), to: day(2026, 1, 5))))
        #expect(Set(newYear) == ["31 декабря", "3 января"])
        #expect(try await names(org, ClientListQuery(today: today, birthday: DateRange(from: day(2026, 6, 20), to: day(2026, 6, 20)))).isEmpty)
    }

    @Test func `пагинация: срез, offset за концом, limit nil`() async throws {
        let org = try await TestOrg.make()
        for name in ["А", "Б", "В", "Г", "Д"] {
            try await org.client(name)
        }

        let middle = try await page(org, ClientListQuery(today: today, limit: 2, offset: 2))
        #expect(middle.clients.map(\.name) == ["В", "Г"] && middle.total == 5)
        let beyond = try await page(org, ClientListQuery(today: today, limit: 2, offset: 10))
        #expect(beyond.clients.isEmpty && beyond.total == 5)
        #expect(try await page(org, ClientListQuery(today: today, limit: nil)).clients.count == 5)
    }

    @Test func `поиск: подстрока имени без регистра, телефон без форматирования, вместе с фильтром`() async throws {
        let org = try await TestOrg.make()
        let petr = try await org.client("Пётр Иванов")
        let anna = try await org.client("Анна Смирнова", gender: .FEMALE)
        try await org.client("Иван Петров")
        try await org.contact(petr, .PHONE, "8 (900) 123-45-67")
        try await org.contact(anna, .EMAIL, "79001234567@example.com")

        #expect(try await names(org, ClientListQuery(today: today, search: "иВАН")) == ["Иван Петров", "Пётр Иванов"])
        #expect(try await names(org, ClientListQuery(today: today, search: "+79001234567")) == ["Пётр Иванов"])
        #expect(try await names(org, ClientListQuery(today: today, search: "123-45")) == ["Пётр Иванов"])
        #expect(try await names(org, ClientListQuery(today: today, search: "а", gender: .FEMALE)) == ["Анна Смирнова"])
    }

    @Test func `группы и контакты собираются по клиенту, чужие группы не попадают`() async throws {
        let org = try await TestOrg.make()
        let other = try await TestOrg.make()
        let a = try await org.client("A")
        try await org.client("B")
        try await org.enroll(a, try await org.group("Самбо"))
        _ = try await other.group("Чужая")
        try await org.contact(a, .PHONE, "+79991112233", label: "мама", primary: true, position: 0)
        try await org.contact(a, .PHONE, "+79994445566", label: "папа", primary: false, position: 1)

        let clients = try await page(org).clients
        let itemA = try #require(clients.first { $0.name == "A" })
        let itemB = try #require(clients.first { $0.name == "B" })
        #expect(itemA.groups.map(\.name) == ["Самбо"])
        #expect(itemA.contacts.map { "\($0.value):\($0.label ?? ""):\($0.isPrimary)" } == ["+79991112233:мама:true", "+79994445566:папа:false"])
        #expect(itemB.groups.isEmpty && itemB.contacts.isEmpty)
    }

    @Test func `без абонементов для экспорта: пустые абонементы и ноль неоплаченных`() async throws {
        let org = try await TestOrg.make()
        let client = try await org.client("Анна")
        let session = try await org.session(try await org.group("Бокс"), "2026-06-01")
        try await org.attend(session, client)

        #expect(try await page(org).clients.first?.unpaidSessions == 1)
        let export = try await page(org, ClientListQuery(today: today, withMemberships: false, limit: nil))
        #expect(export.clients.first?.unpaidSessions == 0 && export.clients.first?.memberships.isEmpty == true)
    }
}
