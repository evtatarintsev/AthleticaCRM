package org.athletica.crm.core.money

import kotlin.math.absoluteValue

/**
 * Человекочитаемое представление денежной суммы.
 *
 * Правила:
 * - разделитель тысяч — неразрывный пробел U+00A0 (чтобы число не разрывалось переносом строки);
 * - разделитель целой и дробной части — запятая;
 * - знак минуса — типографский «−» (U+2212), а не дефис-минус;
 * - количество дробных разрядов — [Currency.fractionDigits];
 * - символ валюты ставится через обычный пробел в конце.
 *
 * Примеры (RUB): `1 200,50 ₽`, `−1 200,50 ₽`, `0,00 ₽`, `1 234 567,89 ₽`.
 */
val Money.formatted: String
    get() = formatMinorUnits(minorUnits, currency)

private const val NBSP: Char = ' '
private const val MINUS: Char = '−'

private fun formatMinorUnits(minorUnits: Long, currency: Currency): String {
    val scale = pow10(currency.fractionDigits)
    val abs = if (minorUnits == Long.MIN_VALUE) Long.MAX_VALUE else minorUnits.absoluteValue
    val majorPart = abs / scale
    val minorPart = abs % scale

    val majorStr = majorPart.toString()
    val grouped =
        buildString {
            val len = majorStr.length
            majorStr.forEachIndexed { i, c ->
                if (i > 0 && (len - i) % 3 == 0) {
                    append(NBSP)
                }
                append(c)
            }
        }

    return buildString {
        if (minorUnits < 0) {
            append(MINUS)
        }
        append(grouped)
        if (currency.fractionDigits > 0) {
            append(',')
            append(minorPart.toString().padStart(currency.fractionDigits, '0'))
        }
        append(' ')
        append(currency.symbol)
    }
}

private fun pow10(digits: Int): Long {
    var result = 1L
    repeat(digits) { result *= 10 }
    return result
}
