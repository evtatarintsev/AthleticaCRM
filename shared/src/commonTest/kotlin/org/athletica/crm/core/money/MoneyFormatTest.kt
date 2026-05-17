package org.athletica.crm.core.money

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    private val nbsp = ' '

    @Test
    fun `положительная сумма в RUB`() {
        assertEquals("1${nbsp}200,50 ₽", Money(120_050, Currency.RUB).formatted)
    }

    @Test
    fun `отрицательная сумма со знаком минус U+2212`() {
        assertEquals("−1${nbsp}200,50 ₽", Money(-120_050, Currency.RUB).formatted)
    }

    @Test
    fun `ноль форматируется с дробной частью`() {
        assertEquals("0,00 ₽", Money(0, Currency.RUB).formatted)
    }

    @Test
    fun `группировка тысяч для миллионов`() {
        assertEquals("1${nbsp}234${nbsp}567,89 ₽", Money(123_456_789, Currency.RUB).formatted)
    }

    @Test
    fun `сумма меньше единицы основной валюты`() {
        assertEquals("0,05 ₽", Money(5, Currency.RUB).formatted)
        assertEquals("0,50 ₽", Money(50, Currency.RUB).formatted)
    }

    @Test
    fun `другая валюта использует свой символ`() {
        assertEquals("500,00 \$", Money(50_000, Currency.USD).formatted)
        assertEquals("500,00 €", Money(50_000, Currency.EUR).formatted)
        assertEquals("500,00 ₸", Money(50_000, Currency.KZT).formatted)
        assertEquals("500,00 Br", Money(50_000, Currency.BYN).formatted)
        assertEquals("500,00 ₴", Money(50_000, Currency.UAH).formatted)
    }

    @Test
    fun `трёхзначная сумма не получает разделителя`() {
        assertEquals("999,99 ₽", Money(99_999, Currency.RUB).formatted)
    }
}
