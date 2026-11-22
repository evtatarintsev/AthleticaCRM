package org.athletica.crm.core.widgets

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

/** Горизонт показа виджета расписания: на сколько дней вперёд от сегодня видны занятия, от 7 до 90. */
@Serializable(with = WidgetHorizon.Serializer::class)
@JvmInline
value class WidgetHorizon private constructor(val days: Int) {
    companion object {
        /** Наименьший допустимый горизонт в днях. */
        const val MIN_DAYS = 7

        /** Наибольший допустимый горизонт в днях. */
        const val MAX_DAYS = 90

        /** Горизонт нового виджета — месяц. */
        val DEFAULT = WidgetHorizon(30)

        /** Создаёт горизонт из [days]; ошибка `INVALID_WIDGET_HORIZON` вне диапазона [MIN_DAYS]..[MAX_DAYS]. */
        context(raise: Raise<AppError>)
        fun from(days: Int): WidgetHorizon {
            ensure(days in MIN_DAYS..MAX_DAYS) {
                CommonDomainError("INVALID_WIDGET_HORIZON", "Горизонт виджета должен быть от $MIN_DAYS до $MAX_DAYS дней")
            }
            return WidgetHorizon(days)
        }
    }

    /** Сериализует горизонт числом дней и проверяет диапазон при чтении. */
    object Serializer : KSerializer<WidgetHorizon> {
        override val descriptor = PrimitiveSerialDescriptor("WidgetHorizon", PrimitiveKind.INT)

        override fun serialize(encoder: Encoder, value: WidgetHorizon) = encoder.encodeInt(value.days)

        override fun deserialize(decoder: Decoder): WidgetHorizon = either { from(decoder.decodeInt()) }.getOrElse { throw SerializationException(it.message) }
    }
}
