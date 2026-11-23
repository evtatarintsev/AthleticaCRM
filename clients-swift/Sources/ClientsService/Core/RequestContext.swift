/// Язык ответа, выбранный по заголовку `Accept-Language`.
enum Lang: String, Sendable {
    case en, ru

    /// Язык по умолчанию — русский.
    static let `default` = Lang.ru

    /// Язык из заголовка [header] (RFC 7231): первый тег, сопоставленный с поддерживаемыми.
    static func from(acceptLanguage header: String?) -> Lang {
        let tag = header?
            .split(separator: ",").first?
            .split(separator: ";").first?
            .split(separator: "-").first?
            .trimmingCharacters(in: .whitespaces)
            .lowercased()
        return tag.flatMap(Lang.init(rawValue:)) ?? .default
    }
}

/// Контекст запроса сотрудника: кто действует, в какой организации и филиале.
/// Аналог `EmployeeRequestContext`; передаётся явным параметром `ctx` вместо context parameters Kotlin.
struct EmployeeRequestContext: Sendable {
    /// Пользователь, выполняющий запрос.
    let userId: UserId
    /// Организация пользователя — скоуп каждого запроса к БД.
    let orgId: OrgId
    /// Филиал, в котором авторизован пользователь.
    let branchId: BranchId
    /// Сотрудник пользователя в организации.
    let employeeId: EmployeeId
    /// Имя пользователя для журнала аудита.
    let username: String
    /// Язык ответа.
    let lang: Lang
    /// IP-адрес клиента для журнала аудита.
    let clientIp: String?
    /// Валюта организации.
    let currency: Currency
}

/// Локализуемый текст сообщения об ошибке.
struct Message: Sendable {
    /// Текст на русском.
    let ru: String
    /// Текст на английском.
    let en: String

    /// Текст на языке запроса [ctx].
    func localize(_ ctx: EmployeeRequestContext) -> String {
        switch ctx.lang {
        case .ru: ru
        case .en: en
        }
    }
}

/// Сообщения об ошибках клиентского API; тексты совпадают с `i18n/Messages.kt`.
enum Messages {
    /// Клиент не найден.
    static let clientNotFound = Message(ru: "Клиент не найден", en: "Client not found")
    /// Клиент с таким идентификатором уже существует.
    static let clientAlreadyExists = Message(ru: "Клиент с таким идентификатором уже существует", en: "Client with this ID already exists")
    /// Источник клиента не найден.
    static let leadSourceNotFound = Message(ru: "Источник не найден", en: "Lead source not found")
    /// Загрузка (документ) не найдена.
    static let uploadNotFound = Message(ru: "Загрузка не найдена", en: "Upload not found")
    /// Группа не найдена.
    static let groupNotFound = Message(ru: "Группа не найдена", en: "Group not found")
    /// Недопустимая сортировка списка клиентов.
    static let invalidClientSort = Message(
        ru: "Некорректная сортировка: поля не должны повторяться, а сортировка по ближайшему дню рождения требует фильтр по дню рождения",
        en: "Invalid sort: fields must not repeat, and sorting by next birthday requires a birthday filter"
    )
}
