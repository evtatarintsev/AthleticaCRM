package org.athletica.crm.core.time

import arrow.core.Either
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Тесты инварианта периода действия и его границ. */
class ValidityTest {
    private val march1 = LocalDate(2026, 3, 1)
    private val june1 = LocalDate(2026, 6, 1)

    @Test
    fun `период с окончанием позже начала создаётся`() {
        val result = Validity.of(march1, june1)

        val validity = assertIs<Either.Right<Validity>>(result).value
        assertEquals(march1, validity.from)
        assertEquals(june1, validity.to)
    }

    @Test
    fun `бессрочный период создаётся`() {
        val result = Validity.of(march1)

        assertEquals(null, assertIs<Either.Right<Validity>>(result).value.to)
    }

    @Test
    fun `вырожденный период отклоняется`() {
        assertIs<Either.Left<*>>(Validity.of(march1, march1))
    }

    @Test
    fun `период с окончанием раньше начала отклоняется`() {
        val result = Validity.of(june1, march1)

        assertEquals("INVALID_VALIDITY", assertIs<Either.Left<org.athletica.crm.core.errors.DomainError>>(result).value.code)
    }

    @Test
    fun `начало периода входит в него, окончание — нет`() {
        val validity = assertIs<Either.Right<Validity>>(Validity.of(march1, june1)).value

        assertTrue(validity.contains(march1))
        assertTrue(validity.contains(LocalDate(2026, 5, 31)))
        assertFalse(validity.contains(june1))
        assertFalse(validity.contains(LocalDate(2026, 2, 28)))
    }

    @Test
    fun `бессрочный период содержит любую дату не раньше начала`() {
        val validity = assertIs<Either.Right<Validity>>(Validity.of(march1)).value

        assertTrue(validity.contains(LocalDate(2099, 1, 1)))
        assertFalse(validity.contains(LocalDate(2026, 2, 28)))
    }

    @Test
    fun `closedAt закрывает период указанной датой`() {
        val validity = assertIs<Either.Right<Validity>>(Validity.of(march1)).value

        val closed = assertIs<Either.Right<Validity>>(validity.closedAt(june1)).value

        assertEquals(march1, closed.from)
        assertEquals(june1, closed.to)
    }

    @Test
    fun `closedAt датой начала отклоняется`() {
        val validity = assertIs<Either.Right<Validity>>(Validity.of(march1)).value

        assertIs<Either.Left<*>>(validity.closedAt(march1))
    }

    @Test
    fun `сериализуется и десериализуется без потери границ`() {
        val validity = assertIs<Either.Right<Validity>>(Validity.of(march1, june1)).value

        val json = Json.encodeToString(Validity.serializer(), validity)

        assertEquals(validity, Json.decodeFromString(Validity.serializer(), json))
    }

    @Test
    fun `десериализация вырожденного периода падает`() {
        val json = """{"from":"2026-03-01","to":"2026-03-01"}"""

        val error = runCatching { Json.decodeFromString(Validity.serializer(), json) }.exceptionOrNull()

        assertTrue(error is kotlinx.serialization.SerializationException)
    }
}
