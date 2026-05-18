package org.athletica.crm.core.money

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * Денежная сумма в конкретной валюте.
 *
 * Хранится в минорных единицах ([minorUnits], копейки/центы) как [Long],
 * чтобы исключить ошибки округления, возможные при использовании `Double`.
 * Преобразование к/из основных единиц — через [Currency.fractionDigits].
 *
 * Все операции запрещают смешивать разные валюты: попытка сложить рубли с
 * долларами бросает [IllegalArgumentException], поскольку это всегда
 * программная ошибка — конвертации валют в продукте нет.
 *
 * Этот тип — единственное допустимое представление денег в коде: `Double` /
 * `Float` / «голые» `Long` для денежных значений запрещены (см. CLAUDE.md).
 */
@Serializable
data class Money(
    /** Сумма в минорных единицах ([Currency.fractionDigits] разрядов на основную единицу). Может быть отрицательной. */
    val minorUnits: Long,
    /** Валюта суммы. Меняется только пересозданием [Money] — арифметика разных валют запрещена. */
    val currency: Currency,
) : Comparable<Money> {
    /** Сложение сумм одной валюты. */
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits + other.minorUnits, currency)
    }

    /** Вычитание сумм одной валюты. */
    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits - other.minorUnits, currency)
    }

    /** Унарный минус — сумма с противоположным знаком. */
    operator fun unaryMinus(): Money = Money(-minorUnits, currency)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    /** Истина, если сумма равна нулю (независимо от валюты). */
    val isZero: Boolean get() = minorUnits == 0L

    /** Истина, если сумма строго больше нуля. */
    val isPositive: Boolean get() = minorUnits > 0

    /** Истина, если сумма строго меньше нуля. */
    val isNegative: Boolean get() = minorUnits < 0

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot mix currencies: $currency vs ${other.currency}"
        }
    }

    companion object {
        /** Нулевая сумма в указанной валюте. */
        fun zero(currency: Currency): Money = Money(0L, currency)
    }
}

/**
 * Сумма всех значений в [Money]. Все элементы должны быть в одной валюте,
 * совпадающей с [currency]; пустой Iterable возвращает [Money.zero].
 */
fun Iterable<Money>.sum(currency: Currency): Money = fold(Money.zero(currency)) { acc, it -> acc + it }

fun Double.toMoney(currency: Currency) = Money((this * 10.0.pow(currency.fractionDigits)).toLong(), currency)
