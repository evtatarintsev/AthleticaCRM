package org.athletica.crm.core.clientnotes

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import kotlin.jvm.JvmInline

/**
 * Текст заметки о клиенте.
 * Допустима только непустая строка после trim длиной не более [MAX_LENGTH] символов.
 * Конструктор приватный, единственная точка создания — [from] / [String.toClientNoteText].
 * Это гарантирует, что [ClientNoteText] не существует в невалидном состоянии
 * (паттерн Smart Constructor / Parse, Don't Validate).
 */
@Serializable(with = ClientNoteText.Serializer::class)
@JvmInline
value class ClientNoteText private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 2000
        private const val EMPTY_MESSAGE = "Текст заметки не может быть пустым"
        private const val TOO_LONG_MESSAGE = "Текст заметки не может быть длиннее $MAX_LENGTH символов"

        /**
         * Парсит [value] в [ClientNoteText], предварительно обрезая пробелы по краям.
         * Возвращает [CommonDomainError] с кодом `EMPTY_CLIENT_NOTE_TEXT` для пустой строки
         * либо `CLIENT_NOTE_TEXT_TOO_LONG`, если длина превышает [MAX_LENGTH].
         */
        fun from(value: String): Either<DomainError, ClientNoteText> {
            val trimmed = value.trim()
            return when {
                trimmed.isEmpty() -> CommonDomainError("EMPTY_CLIENT_NOTE_TEXT", EMPTY_MESSAGE).left()
                trimmed.length > MAX_LENGTH -> CommonDomainError("CLIENT_NOTE_TEXT_TOO_LONG", TOO_LONG_MESSAGE).left()
                else -> ClientNoteText(trimmed).right()
            }
        }
    }

    /**
     * Сериализует [ClientNoteText] как обычную строку JSON.
     * При десериализации применяет [from] и бросает [SerializationException]
     * на невалидном вводе, чтобы инвариант поддерживался уже на JSON-границе.
     */
    object Serializer : KSerializer<ClientNoteText> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ClientNoteText", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: ClientNoteText) {
            encoder.encodeString(value.value)
        }

        override fun deserialize(decoder: Decoder): ClientNoteText {
            val raw = decoder.decodeString()
            return from(raw).getOrElse { err ->
                throw SerializationException(err.message)
            }
        }
    }
}

/** Удобный синоним для [ClientNoteText.from]. */
fun String.toClientNoteText(): Either<DomainError, ClientNoteText> = ClientNoteText.from(this)
