package org.athletica.crm.api.schemas.assistant

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.clients.ClientContactInput
import org.athletica.crm.core.Gender
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientId
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
}

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
)

/** Текущий личный диалог. */
@Serializable
data class AssistantDialogResponse(val id: Uuid, val messages: List<AssistantMessage>)

/** Входной текст текущего хода. */
@Serializable
data class AssistantSendRequest(val dialogId: Uuid, val text: String)

/** Детерминированное подтверждение без модели и расхода квоты. */
@Serializable
data class AssistantConfirmRequest(val clientId: ClientId)

/** Квитанция прямого подтверждения карточки. */
@Serializable
data class AssistantConfirmResponse(val clientId: ClientId)
