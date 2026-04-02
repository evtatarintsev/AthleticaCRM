package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * День недели в расписании группы (ISO-8601: Пн=0, ..., Вс=6).
 * Сериализуется как целое число для совместимости с API.
 */
@Serializable(with = DayOfWeek.Serializer::class)
enum class DayOfWeek(val displayName: String) {
    MONDAY("Пн"),
    TUESDAY("Вт"),
    WEDNESDAY("Ср"),
    THURSDAY("Чт"),
    FRIDAY("Пт"),
    SATURDAY("Сб"),
    SUNDAY("Вс");

    object Serializer : KSerializer<DayOfWeek> {
        override val descriptor = PrimitiveSerialDescriptor("DayOfWeek", PrimitiveKind.INT)
        override fun serialize(encoder: Encoder, value: DayOfWeek) = encoder.encodeInt(value.ordinal)
        override fun deserialize(decoder: Decoder): DayOfWeek = entries[decoder.decodeInt()]
    }
}
