package org.athletica.crm.core.time

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError

/**
 * Период действия правила: полуоткрытый интервал дат `[from, to)`.
 * [from] входит в период, [to] — нет; `to == null` означает бессрочность.
 *
 * Конструктор приватный, единственная точка создания — [of]: состояние
 * `to <= from` не выражается ни в коде, ни при десериализации
 * (паттерн Smart Constructor / Parse, Don't Validate).
 */
@Serializable(with = Validity.Serializer::class)
data class Validity private constructor(
    /** Дата начала действия, включительно. */
    val from: LocalDate,
    /** Дата окончания действия, исключительно; `null` — бессрочно. */
    val to: LocalDate?,
) {
    /** Действует ли правило на [date]. */
    fun contains(date: LocalDate): Boolean = date >= from && (to == null || date < to)

    /**
     * Период, закрытый датой [date]: правило перестаёт действовать с неё.
     * Возвращает ошибку `INVALID_VALIDITY`, если [date] не позже [from].
     */
    fun closedAt(date: LocalDate): Either<DomainError, Validity> = of(from, date)

    override fun toString(): String = "[$from, ${to ?: "∞"})"

    companion object {
        private const val INVALID_VALIDITY_MESSAGE =
            "Дата окончания периода действия должна быть строго позже даты начала"

        /**
         * Создаёт период `[from, to)`.
         * Возвращает [CommonDomainError] с кодом `INVALID_VALIDITY`, если [to] не позже [from].
         */
        fun of(from: LocalDate, to: LocalDate? = null): Either<DomainError, Validity> =
            if (to == null || to > from) {
                Validity(from, to).right()
            } else {
                CommonDomainError("INVALID_VALIDITY", INVALID_VALIDITY_MESSAGE).left()
            }
    }

    /**
     * Сериализует период объектом `{"from": ..., "to": ...}`.
     * При десериализации применяет [of] и бросает [SerializationException]
     * на невалидном вводе, чтобы инвариант поддерживался уже на JSON-границе.
     */
    object Serializer : KSerializer<Validity> {
        @Serializable
        private data class Surrogate(val from: LocalDate, val to: LocalDate? = null)

        override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

        override fun serialize(encoder: Encoder, value: Validity) {
            encoder.encodeSerializableValue(Surrogate.serializer(), Surrogate(value.from, value.to))
        }

        override fun deserialize(decoder: Decoder): Validity {
            val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())
            return of(surrogate.from, surrogate.to).getOrElse { err ->
                throw SerializationException(err.message)
            }
        }
    }
}
