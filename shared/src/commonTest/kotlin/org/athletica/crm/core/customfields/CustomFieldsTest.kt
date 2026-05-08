package org.athletica.crm.core.customfields

import arrow.core.Either
import org.athletica.crm.api.client.appJson
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CustomFieldsTest {
    // ─── CustomFieldValues.with ───────────────────────────────────────────────

    @Test
    fun `with возвращает UNKNOWN_CUSTOM_FIELD для неизвестного ключа`() {
        val cv = CustomFieldValues(listOf(CustomFieldDefinition.Text(fieldKey = "name", label = "name")))
        val result = cv.with(CustomFieldValue.Text(fieldKey = "ghost", value = "x"))
        val left = assertIs<Either.Left<DomainError>>(result)
        assertEquals("UNKNOWN_CUSTOM_FIELD", (left.value as CommonDomainError).code)
    }

    @Test
    fun `with возвращает CUSTOM_FIELD_TYPE_MISMATCH при несовпадении типа`() {
        val cv = CustomFieldValues(listOf(CustomFieldDefinition.Number(fieldKey = "age", label = "age")))
        val result = cv.with(CustomFieldValue.Text(fieldKey = "age", value = "not a number"))
        val left = assertIs<Either.Left<DomainError>>(result)
        assertEquals("CUSTOM_FIELD_TYPE_MISMATCH", (left.value as CommonDomainError).code)
    }

    @Test
    fun `with принимает Text для текстовых подтипов phone email url`() {
        val defs =
            listOf(
                CustomFieldDefinition.Phone(fieldKey = "p", label = "p"),
                CustomFieldDefinition.Email(fieldKey = "e", label = "e"),
                CustomFieldDefinition.Url(fieldKey = "u", label = "u"),
            )
        val cv = CustomFieldValues(defs)
        val result =
            cv.with(
                listOf(
                    CustomFieldValue.Text("p", "+7..."),
                    CustomFieldValue.Text("e", "a@b"),
                    CustomFieldValue.Text("u", "https://x"),
                ),
            )
        assertIs<Either.Right<CustomFieldValues>>(result)
    }

    @Test
    fun `with перезаписывает существующее значение по ключу`() {
        val cv = CustomFieldValues(listOf(CustomFieldDefinition.Text(fieldKey = "name", label = "name")))
        val first = assertIs<Either.Right<CustomFieldValues>>(cv.with(CustomFieldValue.Text("name", "first"))).value
        val second = assertIs<Either.Right<CustomFieldValues>>(first.with(CustomFieldValue.Text("name", "second"))).value
        assertEquals(1, second.toList().size)
        assertEquals("second", (second["name"] as CustomFieldValue.Text).value)
    }

    // ─── round-trip через appJson ─────────────────────────────────────────────

    @Test
    fun `CustomFieldDefinition Select сериализуется и десериализуется через appJson`() {
        val original: CustomFieldDefinition =
            CustomFieldDefinition.Select(
                fieldKey = "level",
                label = "Уровень",
                options = listOf("beginner", "intermediate", "advanced"),
                isRequired = true,
            )
        val encoded = appJson.encodeToString(CustomFieldDefinition.serializer(), original)
        val decoded = appJson.decodeFromString(CustomFieldDefinition.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `CustomFieldDefinition Number с границами round-trip`() {
        val original: CustomFieldDefinition =
            CustomFieldDefinition.Number(
                fieldKey = "age",
                label = "age",
                minValue = 0,
                maxValue = 120,
            )
        val encoded = appJson.encodeToString(CustomFieldDefinition.serializer(), original)
        val decoded = appJson.decodeFromString(CustomFieldDefinition.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `CustomFieldValue Select round-trip через appJson`() {
        val original: CustomFieldValue = CustomFieldValue.Select(fieldKey = "level", value = "beginner")
        val encoded = appJson.encodeToString(CustomFieldValue.serializer(), original)
        val decoded = appJson.decodeFromString(CustomFieldValue.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
