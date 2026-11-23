import PostgresNIO

/// Тип сущности, к которой относятся кастомные поля.
enum CustomFieldEntityType: String, Sendable {
    /// Клиент.
    case client = "CLIENT"
}

/// Определения кастомных полей организации (только чтение: настройка полей остаётся на Kotlin-сервере).
protocol CustomFieldDefinitions: Sendable {
    /// Все определения полей сущности [entityType] в порядке отображения.
    func all(_ entityType: CustomFieldEntityType, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> [CustomFieldDefinition]
}

/// Конфигурация определения поля из JSONB-колонки `config`.
private struct DefinitionConfig: Decodable {
    /// Минимальная длина текста.
    let minLength: Int?
    /// Максимальная длина текста.
    let maxLength: Int?
    /// Минимальное число.
    let minValue: Int64?
    /// Максимальное число.
    let maxValue: Int64?
    /// Варианты выбора.
    let options: [String]?
}

/// Определения кастомных полей поверх PostgreSQL.
struct DbCustomFieldDefinitions: CustomFieldDefinitions {
    func all(_ entityType: CustomFieldEntityType, ctx: EmployeeRequestContext, tr: Transaction) async throws(AppError) -> [CustomFieldDefinition] {
        let rows = try await tr.list("""
            SELECT field_key, field_type::text, config::text, is_required
            FROM custom_field_definitions
            WHERE org_id = \(ctx.orgId.value) AND entity_type = \(entityType.rawValue)
            ORDER BY display_order
            """) { row in try row.decode((String, String, String, Bool).self) }
        var result: [CustomFieldDefinition] = []
        for (key, type, configJson, isRequired) in rows {
            let config = try decodeJson(DefinitionConfig.self, configJson)
            let kind: CustomFieldDefinition.Kind = switch type {
            case "text": .text(minLength: config.minLength, maxLength: config.maxLength)
            case "number": .number(minValue: config.minValue, maxValue: config.maxValue)
            case "date": .date
            case "boolean": .bool
            case "select": .select(options: config.options ?? [])
            default: .textLike
            }
            result.append(CustomFieldDefinition(fieldKey: try CustomFieldKey.from(key), isRequired: isRequired, kind: kind))
        }
        return result
    }
}
