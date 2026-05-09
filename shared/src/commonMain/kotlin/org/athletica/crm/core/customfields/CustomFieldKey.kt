package org.athletica.crm.core.customfields

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
 * Машинный ключ кастомного поля. Допустим только набор символов `[a-z_]+`.
 * Конструктор приватный, единственная точка создания — [from] / [String.toFieldKey].
 * Это гарантирует, что [CustomFieldKey] не существует в невалидном состоянии
 * (паттерн Smart Constructor / Parse, Don't Validate).
 */
@Serializable(with = CustomFieldKey.Serializer::class)
@JvmInline
value class CustomFieldKey private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        private val REGEX = Regex("[a-z_]+")
        private const val INVALID_FIELD_KEY_MESSAGE =
            "Ключ поля должен содержать только строчные латинские буквы и символ подчёркивания"

        /**
         * Парсит [value] в [CustomFieldKey].
         * Возвращает [CommonDomainError] с кодом `INVALID_FIELD_KEY`, если строка нарушает инвариант.
         */
        fun from(value: String): Either<DomainError, CustomFieldKey> =
            if (value.matches(REGEX)) {
                CustomFieldKey(value).right()
            } else {
                CommonDomainError("INVALID_FIELD_KEY", INVALID_FIELD_KEY_MESSAGE).left()
            }
    }

    /**
     * Сериализует [CustomFieldKey] как обычную строку JSON.
     * При десериализации применяет [from] и бросает [SerializationException]
     * на невалидном вводе, чтобы инвариант поддерживался уже на JSON-границе.
     */
    object Serializer : KSerializer<CustomFieldKey> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("CustomFieldKey", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: CustomFieldKey) {
            encoder.encodeString(value.value)
        }

        override fun deserialize(decoder: Decoder): CustomFieldKey {
            val raw = decoder.decodeString()
            return from(raw).getOrElse { err ->
                throw SerializationException(err.message)
            }
        }
    }
}

/** Удобный синоним для [CustomFieldKey.from]. */
fun String.toFieldKey(): Either<DomainError, CustomFieldKey> = CustomFieldKey.from(this)
