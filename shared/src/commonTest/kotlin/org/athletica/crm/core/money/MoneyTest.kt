package org.athletica.crm.core.money

import org.athletica.crm.api.client.appJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyTest {
    private val rub100 = Money(10_000, Currency.RUB)
    private val rub50 = Money(5_000, Currency.RUB)
    private val usd100 = Money(10_000, Currency.USD)

    @Test
    fun `plus складывает суммы одной валюты`() {
        assertEquals(Money(15_000, Currency.RUB), rub100 + rub50)
    }

    @Test
    fun `minus вычитает суммы одной валюты`() {
        assertEquals(Money(5_000, Currency.RUB), rub100 - rub50)
    }

    @Test
    fun `minus может дать отрицательный результат`() {
        assertEquals(Money(-5_000, Currency.RUB), rub50 - rub100)
    }

    @Test
    fun `unaryMinus меняет знак`() {
        assertEquals(Money(-10_000, Currency.RUB), -rub100)
        assertEquals(rub100, -(-rub100))
    }

    @Test
    fun `plus разных валют — IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { rub100 + usd100 }
    }

    @Test
    fun `minus разных валют — IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { rub100 - usd100 }
    }

    @Test
    fun `compareTo разных валют — IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { rub100 < usd100 }
    }

    @Test
    fun `compareTo сравнивает по величине минорных единиц`() {
        assertTrue(rub50 < rub100)
        assertTrue(rub100 > rub50)
        assertTrue(rub100 == Money(10_000, Currency.RUB))
    }

    @Test
    fun `isZero isPositive isNegative`() {
        val zero = Money.zero(Currency.RUB)
        assertTrue(zero.isZero)
        assertFalse(zero.isPositive)
        assertFalse(zero.isNegative)

        assertTrue(rub100.isPositive)
        assertFalse(rub100.isZero)

        assertTrue((-rub100).isNegative)
    }

    @Test
    fun `zero возвращает ноль в указанной валюте`() {
        assertEquals(Money(0, Currency.USD), Money.zero(Currency.USD))
    }

    @Test
    fun `sum складывает все элементы одной валюты`() {
        val items = listOf(rub100, rub50, -rub50)
        assertEquals(rub100, items.sum(Currency.RUB))
    }

    @Test
    fun `sum пустого Iterable возвращает ноль`() {
        assertEquals(Money.zero(Currency.RUB), emptyList<Money>().sum(Currency.RUB))
    }

    @Test
    fun `JSON round-trip`() {
        val original = Money(120_050, Currency.RUB)
        val encoded = appJson.encodeToString(Money.serializer(), original)
        val decoded = appJson.decodeFromString(Money.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `JSON-структура — объект minorUnits + currency`() {
        val encoded = appJson.encodeToString(Money.serializer(), Money(120_050, Currency.RUB))
        assertEquals("""{"minorUnits":120050,"currency":"RUB"}""", encoded)
    }
}
