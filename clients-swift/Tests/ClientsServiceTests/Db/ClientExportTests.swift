import Foundation
import Testing
@testable import ClientsService

/// Тесты CSV-экспорта клиентов.
@Suite struct ClientExportTests {
    @Test func `экспорт: имя первым, стандартные и кастомные колонки, неизвестные ключи пропускаются`() async throws {
        let org = try await TestOrg.make()
        try await org.customField("sport", "Спорт", type: "select", config: #"{"options":["Йога"]}"#)
        try await org.customField("weight", "Вес", type: "number", order: 1)
        let anna = try await org.client("Анна, старшая", birthday: LocalDate(year: 2001, month: 2, day: 3), gender: .FEMALE)
        try await org.sql("""
            UPDATE clients SET custom_fields = '[{"type":"select","fieldKey":"sport","value":"Йога"},{"type":"number","fieldKey":"weight","value":61.0}]'
            WHERE id = \(anna.value)
            """)
        try await org.client("Борис \"Б\"")
        try await org.client("Архивный", archived: true)
        try await org.balance(anna, Money(minorUnits: -120_050, currency: .RUB))
        let group = try await org.group("Бокс")
        try await org.enroll(anna, group)
        try await org.enroll(anna, try await org.group("Айкидо"))

        let csv = try await org.tx { ctx, tr throws(AppError) in
            let page = try await DbClientListView().page(
                ClientListQuery(today: LocalDate(year: 2026, month: 6, day: 20), withMemberships: false, limit: nil), ctx: ctx, tr: tr
            )
            return clientsCsv(
                page.clients, fields: ["gender", "unknown", "sport", "balance", "groups", "weight", "birthday"],
                definitions: try await DbCustomFieldDefinitions().all(.client, ctx: ctx, tr: tr)
            )
        }

        #expect(csv == """
            Имя,Пол,Спорт,Баланс,Группы,Вес,Дата рождения
            "Анна, старшая",Ж,Йога,"\u{2212}1\u{00A0}200,50 ₽",Айкидо; Бокс,61,2001-02-03
            "Борис ""Б\""\",М,,"0,00 ₽",,,

            """)
    }

    @Test func `формат денег как Money.formatted в shared`() {
        #expect(Money(minorUnits: 0, currency: .RUB).formatted == "0,00 ₽")
        #expect(Money(minorUnits: 123_456_789, currency: .USD).formatted == "1\u{00A0}234\u{00A0}567,89 $")
        #expect(Money(minorUnits: -5, currency: .BYN).formatted == "\u{2212}0,05 Br")
    }

    @Test func `значения кастомных полей для таблицы`() throws {
        let key = try CustomFieldKey.from("f")
        #expect(CustomFieldValue.number(key, 5).displayValue == "5")
        #expect(CustomFieldValue.number(key, 5.5).displayValue == "5.5")
        #expect(CustomFieldValue.bool(key, true).displayValue == "✓")
        #expect(CustomFieldValue.bool(key, false).displayValue == "—")
        #expect(CustomFieldValue.date(key, LocalDate(year: 2026, month: 1, day: 2)).displayValue == "2026-01-02")
    }
}
