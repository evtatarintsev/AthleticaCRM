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

@Serializable(with = NonBlankName.Serializer::class)
@JvmInline
value class NonBlankName private constructor(val value: String) {
    companion object {
        context(raise: Raise<AppError>)
        fun from(value: String): NonBlankName {
            val normalized = value.trim()
            ensure(normalized.isNotEmpty()) { CommonDomainError("NAME_REQUIRED", "Имя или заголовок не может быть пустым") }
            return NonBlankName(normalized)
        }
    }

    object Serializer : KSerializer<NonBlankName> {
        override val descriptor = PrimitiveSerialDescriptor("NonBlankName", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: NonBlankName) = encoder.encodeString(value.value)

        override fun deserialize(decoder: Decoder): NonBlankName = either { from(decoder.decodeString()) }.getOrElse { throw SerializationException(it.message) }
    }
}
