package org.athletica.crm.domain.clientcontacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Тесты нормализации телефона [PhoneDigits]. */
class PhoneDigitsTest {
    @Test
    fun `форматирование и код 8 приводятся к одному виду`() {
        assertEquals("79001234567", PhoneDigits.from("8 (900) 123-45-67")?.value)
        assertEquals("79001234567", PhoneDigits.from("+7 900 123 45 67")?.value)
        assertEquals("79001234567", PhoneDigits.from("79001234567")?.value)
    }

    @Test
    fun `номера другой длины не меняют ведущую восьмёрку`() {
        assertEquals("8123456", PhoneDigits.from("812-34-56")?.value)
        assertEquals("4915112345678", PhoneDigits.from("+49 151 1234 5678")?.value)
    }

    @Test
    fun `строка без цифр не даёт номера`() {
        assertNull(PhoneDigits.from("нет номера"))
    }
}
