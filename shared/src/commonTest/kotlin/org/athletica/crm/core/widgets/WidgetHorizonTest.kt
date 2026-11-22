package org.athletica.crm.core.widgets

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WidgetHorizonTest {
    @Test
    fun `горизонт принимает значения от 7 до 90 включительно`() {
        listOf(6, 91).forEach {
            assertIs<Either.Left<*>>(either { WidgetHorizon.from(it) })
            assertFailsWith<SerializationException> { Json.decodeFromString<WidgetHorizon>(it.toString()) }
        }
        listOf(7, 90).forEach {
            assertEquals(it, Json.decodeFromString<WidgetHorizon>(it.toString()).days)
        }
    }

    @Test
    fun `горизонт по умолчанию — 30 дней`() {
        assertEquals(30, WidgetHorizon.DEFAULT.days)
    }
}
