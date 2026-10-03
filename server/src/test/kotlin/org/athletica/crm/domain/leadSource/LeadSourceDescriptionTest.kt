package org.athletica.crm.domain.leadSource

import arrow.core.Either
import org.athletica.crm.core.Lang
import org.athletica.crm.core.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Тесты смарт-конструктора пояснения источника: обрезка пробелов и предел длины. */
class LeadSourceDescriptionTest {
    /** Пояснение из [raw]; ошибка роняет тест. */
    private fun parsed(raw: String): String = assertIs<Either.Right<LeadSourceDescription>>(LeadSourceDescription.from(raw, Lang.RU)).value.value

    @Test
    fun `пробелы по краям отбрасываются`() {
        assertEquals("Листовки у зала", parsed("  Листовки у зала \n"))
    }

    @Test
    fun `пояснение из пробелов становится пустым`() {
        assertEquals(LeadSourceDescription.EMPTY.value, parsed("   \t "))
    }

    @Test
    fun `пустая строка допустима`() {
        assertEquals("", parsed(""))
    }

    @Test
    fun `пояснение ровно из 500 символов допустимо`() {
        assertEquals(500, parsed("а".repeat(500)).length)
    }

    @Test
    fun `пояснение из 501 символа отклоняется`() {
        val result = LeadSourceDescription.from("а".repeat(501), Lang.EN)

        val error = assertIs<Either.Left<DomainError>>(result).value
        assertEquals("LEAD_SOURCE_DESCRIPTION_TOO_LONG", error.code)
        assertEquals("Description cannot be longer than 500 characters", error.message)
    }

    @Test
    fun `длина считается после отбрасывания пробелов`() {
        assertEquals(500, parsed("  " + "а".repeat(500) + "  ").length)
    }
}
