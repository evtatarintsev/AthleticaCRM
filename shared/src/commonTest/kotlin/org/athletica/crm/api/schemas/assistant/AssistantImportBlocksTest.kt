package org.athletica.crm.api.schemas.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.athletica.crm.core.Gender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Блоки импорта сериализуются с дискриминатором `type`, который разбирает веб. */
class AssistantImportBlocksTest {
    @Test
    fun `блоки импорта сохраняют тип и поля при сериализации`() {
        val row =
            AssistantImportRow(
                row = 12,
                photo = 2,
                line = 3,
                status = AssistantImportRowStatus.READY,
                name = "Петров Иван",
                client = AssistantClientPayload("Петров Иван", Gender.MALE),
                warnings = listOf(AssistantImportWarning.UNCERTAIN),
                uncertainFields = listOf("name"),
            )
        val blocks: List<AssistantBlock> =
            listOf(
                AssistantBlock.ImportDraft(Uuid.random(), AssistantDraftStatus.PENDING, AssistantImportCounts(1, 1, 0, 0, 0, 0, 1), listOf(row), listOf(row)),
                AssistantBlock.ImportCompleted(Uuid.random(), 205),
            )
        val json = Json.encodeToString(blocks)
        assertEquals(blocks, Json.decodeFromString<List<AssistantBlock>>(json))
        assertEquals(listOf("import_draft", "import_completed"), Json.parseToJsonElement(json).let { list -> (list as kotlinx.serialization.json.JsonArray).map { it.jsonObject["type"]!!.jsonPrimitive.content } })
        assertEquals("\"duplicate_in_file\"", Json.encodeToString(AssistantImportRowStatus.DUPLICATE_IN_FILE))
    }
}
