package org.athletica.crm.core.money

import arrow.core.getOrElse
import arrow.core.raise.context.either
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
            either {
                Currency.from(c.code)
            }.getOrElse { error(it.message) }
        }
    }

    @Test
    fun `from не зависит от регистра`() {
        either {
            Currency.from("rub")
        }.getOrElse { error(it.message) }
    }

    @Test
    fun `from отвергает неизвестный код`() {
        either {
            Currency.from("XXX")
        }.getOrElse {
            assertEquals("UNKNOWN_CURRENCY", it.code)
        }
    }

    @Test
    fun `from отвергает пустую строку`() {
        either {
            Currency.from("")
            error("empty string should be an error")
        }.getOrElse {
            assertIs<CommonDomainError>(it)
        }
    }

    @Test
    fun `String toCurrency эквивалентен Currency from`() =
        either {
            assertEquals(Currency.from("RUB"), "RUB".toCurrency())
        }.getOrElse { error(it.message) }

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
