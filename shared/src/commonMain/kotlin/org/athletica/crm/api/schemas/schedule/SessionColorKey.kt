package org.athletica.crm.api.schemas.schedule

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Ключ палитры карточки занятия. Сервер выбирает ключ, клиент сопоставляет его
 * собственной паре цветов темы — контракт не привязан к конкретным оттенкам.
 *
 * Сериализуется строкой с именем значения. Строка, которой нет среди значений
 * (сервер новее клиента), десериализуется в [UNKNOWN], а не роняет разбор ответа.
 */
@Serializable(with = SessionColorKey.Serializer::class)
enum class SessionColorKey {
    /** Оранжевый. */
    ORANGE,

    /** Фиолетовый. */
    PURPLE,

    /** Стальной. */
    STEEL,

    /** Бирюзовый. */
    CYAN,

    /** Зелёный. */
    GREEN,

    /** Салатовый. */
    LIME,

    /** Сиреневый. */
    LILAC,

    /** Серый. */
    GREY,

    /** Ключ, неизвестный этой версии клиента; сервер его не выбирает и рисуется нейтральным цветом. */
    UNKNOWN,
    ;

    companion object {
        /** Ключи, из которых сервер выбирает цвет карточки, в фиксированном порядке. */
        val palette: List<SessionColorKey> = entries - UNKNOWN
    }

    /** Сериализатор ключа палитры строкой с терпимостью к неизвестным значениям. */
    object Serializer : KSerializer<SessionColorKey> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("org.athletica.crm.api.schemas.schedule.SessionColorKey", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: SessionColorKey,
        ) {
            encoder.encodeString(value.name)
        }

        override fun deserialize(decoder: Decoder): SessionColorKey {
            val raw = decoder.decodeString()
            return entries.firstOrNull { it.name == raw } ?: UNKNOWN
        }
    }
}
