package org.athletica.crm.core.money

import arrow.core.Either
import kotlinx.serialization.SerializationException
import org.athletica.crm.api.client.appJson
import org.athletica.crm.core.errors.CommonDomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CurrencyTest {
    @Test
    fun `from принимает поддерживаемые валюты`() {
        Currency.entries.forEach { c ->
            val result = Currency.from(c.code)
            assertEquals(c, assertIs<Either.Right<Currency>>(result).value)
        }
    }

    @Test
    fun `from не зависит от регистра`() {
        val result = Currency.from("rub")
        assertEquals(Currency.RUB, assertIs<Either.Right<Currency>>(result).value)
    }

    @Test
    fun `from отвергает неизвестный код`() {
        val result = Currency.from("XXX")
        val left = assertIs<Either.Left<CommonDomainError>>(result).value
        assertEquals("UNKNOWN_CURRENCY", left.code)
    }

    @Test
    fun `from отвергает пустую строку`() {
        val result = Currency.from("")
        assertIs<Either.Left<CommonDomainError>>(result)
    }

    @Test
    fun `String toCurrency эквивалентен Currency from`() {
        assertEquals(Currency.from("RUB"), "RUB".toCurrency())
    }

    @Test
    fun `сериализуется как ISO-код`() {
        val encoded = appJson.encodeToString(Currency.serializer(), Currency.USD)
        assertEquals("\"USD\"", encoded)
    }

    @Test
    fun `десериализует валидный код`() {
        val decoded = appJson.decodeFromString(Currency.serializer(), "\"EUR\"")
        assertEquals(Currency.EUR, decoded)
    }

    @Test
    fun `десериализация неизвестного кода бросает SerializationException`() {
        assertFailsWith<SerializationException> {
            appJson.decodeFromString(Currency.serializer(), "\"XXX\"")
        }
    }
}
