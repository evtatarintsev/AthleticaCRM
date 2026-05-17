package org.athletica.crm.core.money

import arrow.core.getOrElse
import arrow.core.raise.context.Raise
import arrow.core.raise.context.either
import arrow.core.raise.context.raise
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

/**
 * Валюта, в которой ведутся денежные операции организации.
 *
 * Поддерживаемый набор фиксирован: добавление новой валюты требует осознанного
 * расширения этого enum, поскольку каждая валюта несёт метаданные о символе и
 * количестве дробных разрядов (минорных единиц на основную единицу).
 *
 * Конвертация между валютами в продукте не предусмотрена: валюта организации
 * фиксируется на регистрации.
 */
@Serializable(with = Currency.Serializer::class)
enum class Currency(
    /** Код валюты в формате ISO 4217. */
    val code: String,
    /** Символ для отображения после суммы (например, «₽», «$»). */
    val symbol: String,
    /** Количество дробных разрядов — сколько минорных единиц в одной основной. */
    val fractionDigits: Int,
) {
    RUB("RUB", "₽", 2),
    USD("USD", "$", 2),
    EUR("EUR", "€", 2),
    KZT("KZT", "₸", 2),
    BYN("BYN", "Br", 2),
    UAH("UAH", "₴", 2),
    ;

    companion object {
        private val BY_CODE: Map<String, Currency> = entries.associateBy { it.code }
        private const val UNKNOWN_CURRENCY_MESSAGE = "Неизвестная валюта"

        /**
         * Парсит [code] в [Currency]. Регистр кода значения не имеет.
         * Возвращает [CommonDomainError] с кодом `UNKNOWN_CURRENCY`, если код
         * не соответствует ни одной поддерживаемой валюте.
         */
        context(raise: Raise<DomainError>)
        fun from(code: String): Currency {
            val normalized = code.uppercase()
            val currency = BY_CODE[normalized]
            return currency ?: raise(CommonDomainError("UNKNOWN_CURRENCY", "$UNKNOWN_CURRENCY_MESSAGE: $code"))
        }
    }

    /**
     * Сериализует [Currency] как строку — ISO-код. При десериализации применяет
     * [from] и бросает [SerializationException] на неизвестном коде, чтобы
     * инвариант поддерживался уже на JSON-границе.
     */
    object Serializer : KSerializer<Currency> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("Currency", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Currency) {
            encoder.encodeString(value.code)
        }

        override fun deserialize(decoder: Decoder): Currency {
            either {
                return decoder.decodeString().toCurrency()
            }.getOrElse { err ->
                throw SerializationException(err.message)
            }
        }
    }
}

/** Удобный синоним для [Currency.from]. */
context(raise: Raise<DomainError>)
fun String.toCurrency(): Currency = Currency.from(this)
