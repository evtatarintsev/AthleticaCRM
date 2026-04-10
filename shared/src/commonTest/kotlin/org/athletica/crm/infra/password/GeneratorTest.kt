package org.athletica.crm.infra.password

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeneratorTest {
    private val upperOnly = Alphabet(upperCaseChars = true, lowerCaseChars = false, numbers = false, special = false)
    private val lowerOnly = Alphabet(upperCaseChars = false, lowerCaseChars = true, numbers = false, special = false)
    private val numbersOnly = Alphabet(upperCaseChars = false, lowerCaseChars = false, numbers = true, special = false)
    private val specialOnly = Alphabet(upperCaseChars = false, lowerCaseChars = false, numbers = false, special = true)
    private val all = Alphabet(upperCaseChars = true, lowerCaseChars = true, numbers = true, special = true)
    private val none = Alphabet(upperCaseChars = false, lowerCaseChars = false, numbers = false, special = false)

    @Test
    fun passwordHasCorrectLength() {
        assertEquals(12, all.randomPassword(12).length)
        assertEquals(1, all.randomPassword(1).length)
        assertEquals(0, all.randomPassword(0).length)
    }

    @Test
    fun upperOnlyContainsOnlyUppercase() {
        val password = upperOnly.randomPassword(100, seed = 0)
        assertTrue(password.all { it in 'A'..'Z' })
    }

    @Test
    fun lowerOnlyContainsOnlyLowercase() {
        val password = lowerOnly.randomPassword(100, seed = 0)
        assertTrue(password.all { it in 'a'..'z' })
    }

    @Test
    fun numbersOnlyContainsOnlyDigits() {
        val password = numbersOnly.randomPassword(100, seed = 0)
        assertTrue(password.all { it.isDigit() })
    }

    @Test
    fun specialOnlyContainsOnlySpecialChars() {
        val allowed = "!@#\$%^&*()-_=+[]{}|;:,.<>?".toSet()
        val password = specialOnly.randomPassword(100, seed = 0)
        assertTrue(password.all { it in allowed })
    }

    @Test
    fun sameSeedProducesSamePassword() {
        val first = all.randomPassword(20, seed = 42)
        val second = all.randomPassword(20, seed = 42)
        assertEquals(first, second)
    }

    @Test
    fun differentSeedProducesDifferentPassword() {
        val first = all.randomPassword(20, seed = 1)
        val second = all.randomPassword(20, seed = 2)
        assertTrue(first != second)
    }

    @Test
    fun emptyAlphabetThrows() {
        assertFailsWith<IllegalArgumentException> {
            none.randomPassword(10)
        }
    }
}
