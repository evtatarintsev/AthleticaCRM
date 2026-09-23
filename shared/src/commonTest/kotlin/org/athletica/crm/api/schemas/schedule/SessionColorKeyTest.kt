package org.athletica.crm.api.schemas.schedule

import org.athletica.crm.api.client.appJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SessionColorKeyTest {
    @Test
    fun `ключ сериализуется строкой с именем значения`() {
        assertEquals("\"ORANGE\"", appJson.encodeToString(SessionColorKey.serializer(), SessionColorKey.ORANGE))
    }

    @Test
    fun `каждый ключ палитры переживает сериализацию туда и обратно`() {
        SessionColorKey.palette.forEach { key ->
            val json = appJson.encodeToString(SessionColorKey.serializer(), key)
            assertEquals(key, appJson.decodeFromString(SessionColorKey.serializer(), json))
        }
    }

    @Test
    fun `неизвестная строка десериализуется в UNKNOWN`() {
        assertEquals(SessionColorKey.UNKNOWN, appJson.decodeFromString(SessionColorKey.serializer(), "\"MAGENTA\""))
    }

    @Test
    fun `палитра состоит из восьми ключей без UNKNOWN`() {
        assertEquals(8, SessionColorKey.palette.size)
        assertFalse(SessionColorKey.UNKNOWN in SessionColorKey.palette)
    }
}
