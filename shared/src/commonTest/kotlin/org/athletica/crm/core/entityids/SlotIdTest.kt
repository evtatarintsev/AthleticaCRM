package org.athletica.crm.core.entityids

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Тесты сериализации идентификатора версии слота расписания. */
class SlotIdTest {
    @Test
    fun `сериализуется строкой UUID`() {
        val id = SlotId(Uuid.parse("0195f1a0-0000-7000-8000-000000000001"))

        assertEquals("\"0195f1a0-0000-7000-8000-000000000001\"", Json.encodeToString(SlotId.serializer(), id))
    }

    @Test
    fun `десериализуется обратно в тот же идентификатор`() {
        val id = SlotId.new()

        val json = Json.encodeToString(SlotId.serializer(), id)

        assertEquals(id, Json.decodeFromString(SlotId.serializer(), json))
    }

    @Test
    fun `toSlotId оборачивает исходный Uuid`() {
        val uuid = Uuid.generateV7()

        assertEquals(uuid, uuid.toSlotId().value)
    }
}
