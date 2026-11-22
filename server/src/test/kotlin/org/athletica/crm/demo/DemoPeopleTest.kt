package org.athletica.crm.demo

import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Генераторы людей и контактов наполнения: детерминизм и вымышленные контакты. */
class DemoPeopleTest {
    /** Сто вымышленных людей с контактами из генератора с зерном [seed]. */
    private fun sample(seed: Int): List<List<Any>> {
        val random = Random(seed)
        return List(100) {
            val person = random.person()
            listOf(person, random.phone(), random.email(person), random.birthday(LocalDate(2026, 11, 22), 6, 55), random.note())
        }
    }

    @Test
    fun `одно зерно даёт одну и ту же последовательность`() {
        assertEquals(sample(42), sample(42))
    }

    @Test
    fun `телефоны начинаются с +7 000, почта в домене example com`() {
        val random = Random(1)
        repeat(500) {
            val phone = random.phone()
            val email = random.email(random.person())
            assertTrue(phone.startsWith("+7 000"), phone)
            assertTrue(email.endsWith("@example.com"), email)
        }
    }
}
