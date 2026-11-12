package org.athletica.crm.core

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.ensure
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import kotlin.jvm.JvmInline

@Serializable(with = PositiveInt.Serializer::class)
@JvmInline
value class PositiveInt private constructor(val value: Int) {
    companion object {
        context(raise: Raise<AppError>)
        fun from(value: Int): PositiveInt {
            ensure(value > 0) { CommonDomainError("POSITIVE_VALUE_REQUIRED", "Значение должно быть положительным") }
            return PositiveInt(value)
        }
    }

    object Serializer : KSerializer<PositiveInt> {
        override val descriptor = PrimitiveSerialDescriptor("PositiveInt", PrimitiveKind.INT)

        override fun serialize(encoder: Encoder, value: PositiveInt) = encoder.encodeInt(value.value)

        override fun deserialize(decoder: Decoder): PositiveInt = either { from(decoder.decodeInt()) }.getOrElse { throw SerializationException(it.message) }
    }
}
