import Foundation

// Схемы запросов и ответов клиентского API. Форма JSON совпадает с `shared/.../api/schemas/clients`
// (kotlinx.serialization с `encodeDefaults`): веб-клиент декодирует их сгенерированными zod-схемами.
// Запросы декодируются с умолчаниями Kotlin-схем для отсутствующих полей.

/// Состояние клиента: активный либо архивный.
enum ClientState: String, Codable, Sendable {
    case ACTIVE, ARCHIVED
}

/// Контакт клиента в ответе.
struct ClientContactSchema: Codable, Equatable, Sendable {
    /// Идентификатор контакта.
    let id: ClientContactId
    /// Тип контакта.
    let type: ContactType
    /// Значение контакта.
    let value: String
    /// Комментарий; `null` — без комментария.
    @Nullable var label: String?
    /// Основной контакт своего типа.
    let isPrimary: Bool
}

/// Контакт клиента в запросе создания/редактирования (ещё без идентификатора).
struct ClientContactInput: Decodable, Sendable {
    /// Тип контакта.
    let type: ContactType
    /// Значение контакта.
    let value: String
    /// Комментарий; пустой — без комментария.
    let label: String?
    /// Отметка «основной».
    let isPrimary: Bool

    /// Ключи JSON.
    private enum CodingKeys: String, CodingKey { case type, value, label, isPrimary }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = try c.decode(ContactType.self, forKey: .type)
        value = try c.decode(String.self, forKey: .value)
        label = try c.decodeIfPresent(String.self, forKey: .label)
        isPrimary = try c.decodeIfPresent(Bool.self, forKey: .isPrimary) ?? false
    }
}

/// Документ клиента в ответе.
struct ClientDocSchema: Encodable, Sendable {
    /// Идентификатор документа.
    let id: ClientDocId
    /// Загруженный файл.
    let uploadId: UploadId
    /// Название документа.
    let name: String
    /// Момент прикрепления.
    let createdAt: Date
}

/// Полные данные клиента: ответ на детали, создание и редактирование.
struct ClientDetailResponse: Encodable, Sendable {
    /// Идентификатор клиента.
    let id: ClientId
    /// Имя клиента.
    let name: String
    /// Момент появления клиента в системе.
    let createdAt: Date
    /// Аватарка.
    @Nullable var avatarId: UploadId?
    /// День рождения.
    @Nullable var birthday: LocalDate?
    /// Пол.
    let gender: Gender
    /// Группы клиента.
    let groups: [ClientGroup]
    /// Баланс личного счёта (отрицательный — задолженность).
    let balance: Money
    /// Документы клиента.
    let docs: [ClientDocSchema]
    /// Источник клиента.
    @Nullable var leadSourceId: LeadSourceId?
    /// Значения кастомных полей.
    let customFields: [CustomFieldValue]
    /// Контакты клиента.
    let contacts: [ClientContactSchema]
    /// Состояние клиента.
    let state: ClientState
}

/// Запрос деталей клиента по [id] (query-параметр GET).
struct ClientDetailRequest: Decodable, Sendable {
    /// Идентификатор клиента.
    let id: ClientId
}

/// Запрос создания клиента; [id] генерирует веб-клиент (offline-first).
struct CreateClientRequest: Decodable, Sendable {
    /// Идентификатор нового клиента.
    let id: ClientId
    /// Поля клиента.
    let fields: ClientFields
    /// Контакты клиента.
    let contacts: [ClientContactInput]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: ClientFieldsKeys.self)
        id = try c.decode(ClientId.self, forKey: .id)
        fields = try ClientFields(from: c)
        contacts = try c.decodeIfPresent([ClientContactInput].self, forKey: .contacts) ?? []
    }
}

/// Запрос редактирования клиента; контакты полностью заменяют текущий набор.
struct EditClientRequest: Decodable, Sendable {
    /// Идентификатор клиента.
    let id: ClientId
    /// Новые поля клиента.
    let fields: ClientFields
    /// Новые контакты клиента.
    let contacts: [ClientContactInput]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: ClientFieldsKeys.self)
        id = try c.decode(ClientId.self, forKey: .id)
        fields = try ClientFields(from: c)
        contacts = try c.decodeIfPresent([ClientContactInput].self, forKey: .contacts) ?? []
    }
}

/// Ключи JSON запросов создания и редактирования клиента.
private enum ClientFieldsKeys: String, CodingKey {
    case id, name, avatarId, birthday, gender, leadSourceId, customFields, contacts
}

extension ClientFields {
    /// Поля клиента из JSON-объекта запроса [c]; отсутствующие необязательные поля — умолчания схемы.
    fileprivate init(from c: KeyedDecodingContainer<ClientFieldsKeys>) throws {
        self.init(
            name: try c.decode(NonBlankName.self, forKey: .name),
            avatarId: try c.decodeIfPresent(UploadId.self, forKey: .avatarId),
            birthday: try c.decodeIfPresent(LocalDate.self, forKey: .birthday),
            gender: try c.decode(Gender.self, forKey: .gender),
            leadSourceId: try c.decodeIfPresent(LeadSourceId.self, forKey: .leadSourceId),
            customFields: try c.decodeIfPresent([CustomFieldValue].self, forKey: .customFields) ?? []
        )
    }
}

/// Запрос добавления клиентов [clientIds] в группу [groupId].
struct AddClientsToGroupRequest: Decodable, Sendable {
    /// Клиенты.
    let clientIds: [ClientId]
    /// Группа.
    let groupId: GroupId
}

/// Запрос записи клиента [clientId] сразу в несколько групп: во все или ни в одну.
struct AddClientToGroupsRequest: Decodable, Sendable {
    /// Клиент.
    let clientId: ClientId
    /// Группы.
    let groupIds: [GroupId]
}

/// Запрос удаления клиентов [clientIds] из группы [groupId].
struct RemoveClientFromGroupRequest: Decodable, Sendable {
    /// Клиенты.
    let clientIds: [ClientId]
    /// Группа.
    let groupId: GroupId
}

/// Запрос прикрепления загруженного файла к клиенту как документа.
struct AttachClientDocRequest: Decodable, Sendable {
    /// Идентификатор нового документа.
    let docId: ClientDocId
    /// Клиент.
    let clientId: ClientId
    /// Загруженный файл.
    let uploadId: UploadId
    /// Название документа.
    let name: String
}

/// Запрос удаления документа клиента.
struct DeleteClientDocRequest: Decodable, Sendable {
    /// Клиент.
    let clientId: ClientId
    /// Документ.
    let docId: ClientDocId
}

/// Запрос архивирования или восстановления клиентов.
struct ClientIdsRequest: Decodable, Sendable {
    /// Клиенты.
    let clientIds: [ClientId]
}

/// Поле сортировки списка клиентов.
enum ClientSortField: String, Codable, Sendable {
    case NAME, BALANCE, BIRTHDAY, NEXT_BIRTHDAY
}

/// Направление сортировки.
enum SortDirectionSchema: String, Codable, Sendable {
    case Asc, Desc
}

/// Один критерий сортировки списка клиентов.
struct ClientSort: Decodable, Sendable {
    /// Поле сортировки.
    let field: ClientSortField
    /// Направление; по умолчанию — по возрастанию.
    let direction: SortDirectionSchema

    /// Ключи JSON.
    private enum CodingKeys: String, CodingKey { case field, direction }

    init(field: ClientSortField, direction: SortDirectionSchema) {
        self.field = field
        self.direction = direction
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        field = try c.decode(ClientSortField.self, forKey: .field)
        direction = try c.decodeIfPresent(SortDirectionSchema.self, forKey: .direction) ?? .Asc
    }
}

/// Запрос страницы списка клиентов; все поля необязательны.
struct ClientListRequest: Decodable, Sendable {
    /// Поиск по имени или телефону.
    var name: String? = nil
    /// Архивные вместо активных.
    var archived = false
    /// Размер страницы.
    var limit = 50
    /// Смещение.
    var offset = 0
    /// Поле сортировки (если [sort] пуст).
    var sortField = ClientSortField.NAME
    /// Направление сортировки (если [sort] пуст).
    var sortDirection = SortDirectionSchema.Asc
    /// Фильтр по полу.
    var gender: Gender? = nil
    /// Только должники.
    var hasDebt = false
    /// Только без групп.
    var noGroup = false
    /// Только участники группы.
    var groupId: GroupId? = nil
    /// Фильтр по дню рождения.
    var birthday: DateRange? = nil
    /// Критерии сортировки по приоритету.
    var sort: [ClientSort]? = nil

    /// Ключи JSON.
    private enum CodingKeys: String, CodingKey {
        case name, archived, limit, offset, sortField, sortDirection, gender, hasDebt, noGroup, groupId, birthday, sort
    }

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decodeIfPresent(String.self, forKey: .name)
        archived = try c.decodeIfPresent(Bool.self, forKey: .archived) ?? false
        limit = try c.decodeIfPresent(Int.self, forKey: .limit) ?? 50
        offset = try c.decodeIfPresent(Int.self, forKey: .offset) ?? 0
        sortField = try c.decodeIfPresent(ClientSortField.self, forKey: .sortField) ?? .NAME
        sortDirection = try c.decodeIfPresent(SortDirectionSchema.self, forKey: .sortDirection) ?? .Asc
        gender = try c.decodeIfPresent(Gender.self, forKey: .gender)
        hasDebt = try c.decodeIfPresent(Bool.self, forKey: .hasDebt) ?? false
        noGroup = try c.decodeIfPresent(Bool.self, forKey: .noGroup) ?? false
        groupId = try c.decodeIfPresent(GroupId.self, forKey: .groupId)
        birthday = try c.decodeIfPresent(DateRange.self, forKey: .birthday)
        sort = try c.decodeIfPresent([ClientSort].self, forKey: .sort)
    }
}

/// Режим активации абонемента.
enum ActivationMode: String, Codable, Sendable {
    case IMMEDIATE, ON_DATE, ON_FIRST_VISIT
}

/// Положение даты относительно срока абонемента.
enum TermPosition: String, Codable, Sendable {
    case NOT_STARTED, IN_TERM, EXPIRED
}

/// Причина признака «заканчивается».
enum EndingSoonReason: String, Codable, Sendable, Comparable {
    case BY_DATE, BY_SESSIONS

    /// Порядок объявления — порядок сортировки, как у `ordinal` в Kotlin.
    private var order: Int {
        switch self {
        case .BY_DATE: 0
        case .BY_SESSIONS: 1
        }
    }

    static func < (lhs: EndingSoonReason, rhs: EndingSoonReason) -> Bool { lhs.order < rhs.order }
}

/// Состояние абонемента в строке списка (дискриминатор `type`).
enum ClientListMembershipState: Encodable, Sendable {
    /// Неактивированный абонемент: режим и дата активации.
    case inactive(activationMode: ActivationMode, activateOn: LocalDate?)
    /// Активный абонемент с признаками на сегодня.
    case active(startDate: LocalDate, endDate: LocalDate, termPosition: TermPosition, endingSoon: [EndingSoonReason], closePending: Bool)

    /// Ключи JSON.
    private enum CodingKeys: String, CodingKey {
        case type, activationMode, activateOn, startDate, endDate, termPosition, endingSoon, closePending
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .inactive(activationMode, activateOn):
            try c.encode("inactive", forKey: .type)
            try c.encode(activationMode, forKey: .activationMode)
            try c.encode(activateOn, forKey: .activateOn)
        case let .active(startDate, endDate, termPosition, endingSoon, closePending):
            try c.encode("active", forKey: .type)
            try c.encode(startDate, forKey: .startDate)
            try c.encode(endDate, forKey: .endDate)
            try c.encode(termPosition, forKey: .termPosition)
            try c.encode(endingSoon, forKey: .endingSoon)
            try c.encode(closePending, forKey: .closePending)
        }
    }
}

/// Абонемент клиента в строке списка.
struct ClientListMembership: Encodable, Sendable {
    /// Идентификатор абонемента.
    let id: MembershipId
    /// Название абонемента.
    let name: String
    /// Всего занятий; `null` — безлимит.
    @Nullable var sessionsTotal: Int?
    /// Остаток занятий; `null` — безлимит.
    @Nullable var sessionsRemaining: Int?
    /// Состояние и признаки на сегодня.
    let state: ClientListMembershipState
}

/// Краткая запись клиента в списке.
struct ClientListItem: Encodable, Sendable {
    /// Идентификатор клиента.
    let id: ClientId
    /// Имя клиента.
    let name: String
    /// Аватарка.
    @Nullable var avatarId: UploadId?
    /// День рождения.
    @Nullable var birthday: LocalDate?
    /// Пол.
    let gender: Gender
    /// Группы клиента.
    let groups: [ClientGroup]
    /// Баланс личного счёта.
    let balance: Money
    /// Значения кастомных полей.
    let customFields: [CustomFieldValue]
    /// Контакты клиента.
    let contacts: [ClientContactSchema]
    /// Состояние клиента.
    let state: ClientState
    /// Активные и неактивированные абонементы.
    let memberships: [ClientListMembership]
    /// Число неоплаченных занятий в группах текущего филиала.
    let unpaidSessions: Int
}

/// Страница списка клиентов.
struct ClientListResponse: Encodable, Sendable {
    /// Клиенты страницы.
    let clients: [ClientListItem]
    /// Общее число клиентов под фильтром.
    let total: Int
}

/// Ответ на ошибку: машиночитаемый код и сообщение.
struct ErrorResponse: Encodable, Sendable {
    /// Код ошибки.
    let code: String
    /// Сообщение об ошибке.
    let message: String
    /// Ошибки полей формы; клиентское API их не заполняет.
    @Nullable var fields: [String]? = nil
}
