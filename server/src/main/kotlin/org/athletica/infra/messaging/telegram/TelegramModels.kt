package org.athletica.infra.messaging.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Пользователь или бот Telegram (`User`). */
@Serializable
data class TgUser(
    val id: Long,
    @SerialName("is_bot")
    val isBot: Boolean = false,
    @SerialName("first_name")
    val firstName: String = "",
    @SerialName("last_name")
    val lastName: String? = null,
    val username: String? = null,
)

/** Чат Telegram (`Chat`); [type] — `private`, `group`, `supergroup` или `channel`. */
@Serializable
data class TgChat(
    val id: Long,
    val type: String,
)

/** Контакт, присланный в сообщении (`Contact`); [userId] — владелец номера, если он пользователь Telegram. */
@Serializable
data class TgContact(
    @SerialName("phone_number")
    val phoneNumber: String,
    @SerialName("first_name")
    val firstName: String = "",
    @SerialName("user_id")
    val userId: Long? = null,
)

/**
 * Сообщение Telegram (`Message`). Поля вложений хранятся как [JsonElement]: важен только факт их
 * наличия, а не содержимое — медиа в CRM не загружаются.
 */
@Serializable
data class TgMessage(
    @SerialName("message_id")
    val messageId: Long,
    val from: TgUser? = null,
    val chat: TgChat,
    val text: String? = null,
    val caption: String? = null,
    val contact: TgContact? = null,
    val photo: JsonElement? = null,
    val video: JsonElement? = null,
    @SerialName("video_note")
    val videoNote: JsonElement? = null,
    val voice: JsonElement? = null,
    val audio: JsonElement? = null,
    val document: JsonElement? = null,
    val sticker: JsonElement? = null,
    val location: JsonElement? = null,
)

/** Обновление из `getUpdates` (`Update`); нас интересуют только новые сообщения. */
@Serializable
data class TgUpdate(
    @SerialName("update_id")
    val updateId: Long,
    val message: TgMessage? = null,
)

/** Клавиатура бота в ответе (`reply_markup`). */
sealed interface TgReplyMarkup {
    /** Клавиатура с одной кнопкой [buttonText], запрашивающей контакт пользователя. */
    data class RequestContact(val buttonText: String) : TgReplyMarkup

    /** Убрать ранее показанную клавиатуру. */
    data object Remove : TgReplyMarkup
}
