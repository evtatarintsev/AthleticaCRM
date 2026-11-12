package org.athletica.crm.core

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WriteValuesTest {
    @Test
    fun `имя обрезается а пустая строка отклоняется`() {
        for (raw in listOf("", " \t ")) {
            assertIs<Either.Left<*>>(either { NonBlankName.from(raw) })
            assertFailsWith<SerializationException> {
                Json.decodeFromString<NonBlankName>(Json.encodeToString(raw))
            }
        }
        assertEquals("Йога", Json.decodeFromString<NonBlankName>("\" Йога \"").value)
    }

    @Test
    fun `положительное число сохраняет числовой формат JSON`() {
        for (raw in listOf(0, -1)) {
            assertIs<Either.Left<*>>(either { PositiveInt.from(raw) })
            assertFailsWith<SerializationException> { Json.decodeFromString<PositiveInt>(raw.toString()) }
        }
        assertEquals("1200", Json.encodeToString(Json.decodeFromString<PositiveInt>("1200")))
    }
}
