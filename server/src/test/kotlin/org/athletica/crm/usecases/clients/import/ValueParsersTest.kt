package org.athletica.crm.usecases.clients.import

import arrow.core.Either
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.Gender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValueParsersTest {
    @Test
    fun `parseName trim и проверка непустоты`() {
        assertEquals("Иванов И", (ValueParsers.parseName("  Иванов И  ") as Either.Right).value)
        assertTrue(ValueParsers.parseName("   ") is Either.Left)
        assertTrue(ValueParsers.parseName("") is Either.Left)
        assertTrue(ValueParsers.parseName(null) is Either.Left)
    }

    @Test
    fun `parseDate пустая строка → null`() {
        assertNull((ValueParsers.parseDate("", null) as Either.Right).value)
        assertNull((ValueParsers.parseDate("   ", null) as Either.Right).value)
        assertNull((ValueParsers.parseDate(null, null) as Either.Right).value)
    }

    @Test
    fun `parseDate распознаёт распространённые форматы`() {
        val expected = LocalDate(2019, 11, 15)
        assertEquals(expected, (ValueParsers.parseDate("15.11.2019", null) as Either.Right).value)
        assertEquals(expected, (ValueParsers.parseDate("2019-11-15", null) as Either.Right).value)
        assertEquals(expected, (ValueParsers.parseDate("15/11/2019", null) as Either.Right).value)
        assertEquals(expected, (ValueParsers.parseDate("15-11-2019", null) as Either.Right).value)
    }

    @Test
    fun `parseDate использует кастомный pattern если задан`() {
        val expected = LocalDate(2019, 11, 15)
        assertEquals(expected, (ValueParsers.parseDate("11/15/2019", "MM/dd/yyyy") as Either.Right).value)
    }

    @Test
    fun `parseDate возвращает ошибку при невалидной дате`() {
        assertIs<Either.Left<String>>(ValueParsers.parseDate("не дата", null))
        assertIs<Either.Left<String>>(ValueParsers.parseDate("11/15/2019", "dd.MM.yyyy"))
    }

    @Test
    fun `parseDecimal с разными форматами`() {
        assertEquals(3056.0, (ValueParsers.parseDecimal("3056.00") as Either.Right).value)
        assertEquals(3056.5, (ValueParsers.parseDecimal("3056,50") as Either.Right).value)
        assertEquals(1234.5, (ValueParsers.parseDecimal("1 234,5") as Either.Right).value)
        assertEquals(-266.0, (ValueParsers.parseDecimal("-266.00") as Either.Right).value)
    }

    @Test
    fun `parseDecimal пусто → null, нечисло → ошибка`() {
        assertNull((ValueParsers.parseDecimal("") as Either.Right).value)
        assertNull((ValueParsers.parseDecimal(null) as Either.Right).value)
        assertIs<Either.Left<String>>(ValueParsers.parseDecimal("abc"))
    }

    @Test
    fun `parseGender пусто → null без обращения к маппингу`() {
        assertNull((ValueParsers.parseGender("", emptyMap()) as Either.Right).value)
        assertNull((ValueParsers.parseGender(null, emptyMap()) as Either.Right).value)
        assertNull((ValueParsers.parseGender("   ", emptyMap()) as Either.Right).value)
    }

    @Test
    fun `parseGender возвращает Gender из пользовательского маппинга`() {
        val mapping = mapOf("Мужской" to Gender.MALE, "Женский" to Gender.FEMALE)
        assertEquals(Gender.MALE, (ValueParsers.parseGender("Мужской", mapping) as Either.Right).value)
        assertEquals(Gender.FEMALE, (ValueParsers.parseGender(" Женский ", mapping) as Either.Right).value)
    }

    @Test
    fun `parseGender ошибка если значение не покрыто маппингом`() {
        assertIs<Either.Left<String>>(ValueParsers.parseGender("男", mapOf("Мужской" to Gender.MALE)))
    }
}
