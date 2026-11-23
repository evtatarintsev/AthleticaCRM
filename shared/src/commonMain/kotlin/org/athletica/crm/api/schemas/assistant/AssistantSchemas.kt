package org.athletica.crm.api.schemas.assistant

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.clients.ClientContactInput
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.UploadId
import kotlin.uuid.Uuid

/** Доступность кнопки помощника для текущего сотрудника. */
@Serializable
data class AssistantStatusResponse(val available: Boolean, val enabled: Boolean, val permitted: Boolean)

/** Настройки и использование помощника в организации. */
@Serializable
data class AssistantSettingsResponse(
    val available: Boolean,
    val enabled: Boolean,
    val dailyMessageLimit: Int,
    val acceptedMessages: Int,
)

/** Изменение настроек владельцем организации. */
@Serializable
data class AssistantSettingsRequest(val enabled: Boolean, val dailyMessageLimit: Int)

/** Содержимое карточки; значения совпадают с будущей доменной записью. */
@Serializable
data class AssistantClientPayload(
    val name: String,
    val gender: Gender,
    val birthday: LocalDate? = null,
    val contacts: List<ClientContactInput> = emptyList(),
    val customFields: List<CustomFieldValue> = emptyList(),
    val customFieldLabels: Map<String, String> = emptyMap(),
)

/** Состояние карточки на сервере. */
@Serializable
enum class AssistantDraftStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("executed")
    EXECUTED,

    @SerialName("superseded")
    SUPERSEDED,
}

/** Серверные блоки ответа; текст модели не создаёт карточки или квитанции. */
@Serializable
sealed interface AssistantBlock {
    /** Подготовленные и проверенные данные клиента. */
    @Serializable
    @SerialName("client_draft")
    data class ClientDraft(val clientId: ClientId, val payload: AssistantClientPayload, val status: AssistantDraftStatus) : AssistantBlock

    /** Подтверждённый результат доменной записи. */
    @Serializable
    @SerialName("client_created")
    data class ClientCreated(val clientId: ClientId) : AssistantBlock

    /**
     * Пакетная карточка импорта: счётчики, строки не в статусе `ready` или с предупреждениями
     * (не больше [AssistantImportLimits.PROBLEM_ROWS]) и превью первых готовых строк.
     */
    @Serializable
    @SerialName("import_draft")
    data class ImportDraft(
        val draftId: Uuid,
        val status: AssistantDraftStatus,
        val counts: AssistantImportCounts,
        val problems: List<AssistantImportRow>,
        val preview: List<AssistantImportRow>,
        val customFieldLabels: Map<String, String> = emptyMap(),
    ) : AssistantBlock

    /** Квитанция пакета: число созданных клиентов. */
    @Serializable
    @SerialName("import_completed")
    data class ImportCompleted(val draftId: Uuid, val created: Int) : AssistantBlock
}

/** Пределы размера пакетной карточки в ответе. */
object AssistantImportLimits {
    /** Сколько проблемных строк попадает в карточку. */
    const val PROBLEM_ROWS = 200

    /** Сколько готовых строк показывается в превью. */
    const val PREVIEW_ROWS = 20
}

/** Итог разбора строки источника. */
@Serializable
enum class AssistantImportRowStatus {
    @SerialName("ready")
    READY,

    @SerialName("exists")
    EXISTS,

    @SerialName("duplicate_in_file")
    DUPLICATE_IN_FILE,

    @SerialName("possible_duplicate")
    POSSIBLE_DUPLICATE,

    @SerialName("error")
    ERROR,
}

/** Причина, по которой строка не может быть создана. */
@Serializable
enum class AssistantImportRowError {
    @SerialName("name_required")
    NAME_REQUIRED,

    @SerialName("gender_required")
    GENDER_REQUIRED,

    @SerialName("invalid_date")
    INVALID_DATE,

    @SerialName("invalid_contact")
    INVALID_CONTACT,

    @SerialName("invalid_field")
    INVALID_FIELD,

    @SerialName("field_required")
    FIELD_REQUIRED,
}

/** Предупреждение у создаваемой строки. */
@Serializable
enum class AssistantImportWarning {
    /** Часть значений распознана неуверенно. */
    @SerialName("uncertain")
    UNCERTAIN,

    /** Неуверенно распознанный контакт не будет сохранён. */
    @SerialName("contact_dropped")
    CONTACT_DROPPED,

    /** Возможный дубль, который пользователь решил создать. */
    @SerialName("possible_duplicate")
    POSSIBLE_DUPLICATE,
}

/** Строка пакетной карточки с позицией в файле. */
@Serializable
data class AssistantImportRow(
    /** Сквозной номер строки источника, по нему модель вносит исправления. */
    val row: Int,
    /** Лист таблицы; `null` для фото. */
    val sheet: String? = null,
    /** Номер фото с 1; `null` для таблицы. */
    val photo: Int? = null,
    /** Номер строки на листе или фото. */
    val line: Int,
    val status: AssistantImportRowStatus,
    /** Имя из строки, даже если строку не удалось разобрать. */
    val name: String? = null,
    /** Данные будущего клиента; `null` у ошибочной строки. */
    val client: AssistantClientPayload? = null,
    val error: AssistantImportRowError? = null,
    /** Поле или ключ доп. поля, к которому относится [error]. */
    val errorField: String? = null,
    val warnings: List<AssistantImportWarning> = emptyList(),
    /** Поля с неуверенно распознанными значениями: `name`, `birthday`, `gender` или ключ доп. поля. */
    val uncertainFields: List<String> = emptyList(),
    /** Клиент организации, с которым совпала строка. */
    val existingClientId: ClientId? = null,
)

/** Счётчики строк пакетной карточки. */
@Serializable
data class AssistantImportCounts(
    val total: Int,
    val ready: Int,
    val exists: Int,
    val duplicateInFile: Int,
    val possibleDuplicate: Int,
    val error: Int,
    /** Готовые строки с предупреждениями. */
    val warnings: Int,
)

/** Состояние обработки сообщения. */
@Serializable
enum class AssistantMessageStatus {
    @SerialName("processing")
    PROCESSING,

    @SerialName("completed")
    COMPLETED,

    @SerialName("failed")
    FAILED,
}

/** Роль сообщения в интерфейсе. */
@Serializable
enum class AssistantMessageRole {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,
}

/** Сообщение с независимо сформированными серверными блоками. */
@Serializable
data class AssistantMessage(
    val id: Uuid,
    val role: AssistantMessageRole,
    val text: String,
    val status: AssistantMessageStatus,
    val blocks: List<AssistantBlock> = emptyList(),
    val errorCode: String? = null,
    /** Имена приложенных файлов; содержимое после получения строк не хранится. */
    val attachments: List<String> = emptyList(),
)

/** Текущий личный диалог. */
@Serializable
data class AssistantDialogResponse(val id: Uuid, val messages: List<AssistantMessage>)

/** Входной текст текущего хода; при наличии [attachments] текст может быть пустым. */
@Serializable
data class AssistantSendRequest(val dialogId: Uuid, val text: String, val attachments: List<UploadId> = emptyList())

/** Детерминированное подтверждение без модели и расхода квоты. */
@Serializable
data class AssistantConfirmRequest(val clientId: ClientId)

/** Квитанция прямого подтверждения карточки. */
@Serializable
data class AssistantConfirmResponse(val clientId: ClientId)

/** Подтверждение пакетной карточки кнопкой, без модели и расхода квоты. */
@Serializable
data class AssistantImportConfirmRequest(val draftId: Uuid)

/** Квитанция пакета: число созданных клиентов. */
@Serializable
data class AssistantImportConfirmResponse(val created: Int)
