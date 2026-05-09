package org.athletica.crm.core.customfields

import arrow.core.Either
import arrow.core.getOrElse
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
        val cv = CustomFieldValues(listOf(CustomFieldDefinition.Text(fieldKey = key("name"), label = "name")))
        val result = cv.with(CustomFieldValue.Text(fieldKey = key("ghost"), value = "x"))
        val left = assertIs<Either.Left<DomainError>>(result)
        assertEquals("UNKNOWN_CUSTOM_FIELD", (left.value as CommonDomainError).code)
    }

    @Test
    fun `with возвращает CUSTOM_FIELD_TYPE_MISMATCH при несовпадении типа`() {
        val cv = CustomFieldValues(listOf(CustomFieldDefinition.Number(fieldKey = key("age"), label = "age")))
        val result = cv.with(CustomFieldValue.Text(fieldKey = key("age"), value = "not a number"))
        val left = assertIs<Either.Left<DomainError>>(result)
        assertEquals("CUSTOM_FIELD_TYPE_MISMATCH", (left.value as CommonDomainError).code)
    }

    @Test
    fun `with принимает Text для текстовых подтипов phone email url`() {
        val defs =
            listOf(
                CustomFieldDefinition.Phone(fieldKey = key("p"), label = "p"),
                CustomFieldDefinition.Email(fieldKey = key("e"), label = "e"),
                CustomFieldDefinition.Url(fieldKey = key("u"), label = "u"),
            )
        val cv = CustomFieldValues(defs)
        val result =
            cv.with(
                listOf(
                    CustomFieldValue.Text(key("p"), "+7..."),
                    CustomFieldValue.Text(key("e"), "a@b"),
                    CustomFieldValue.Text(key("u"), "https://x"),
                ),
            )
        assertIs<Either.Right<CustomFieldValues>>(result)
    }

    @Test
    fun `with перезаписывает существующее значение по ключу`() {
        val cv = CustomFieldValues(listOf(CustomFieldDefinition.Text(fieldKey = key("name"), label = "name")))
        val first = assertIs<Either.Right<CustomFieldValues>>(cv.with(CustomFieldValue.Text(key("name"), "first"))).value
        val second = assertIs<Either.Right<CustomFieldValues>>(first.with(CustomFieldValue.Text(key("name"), "second"))).value
        assertEquals(1, second.toList().size)
        assertEquals("second", (second[key("name")] as CustomFieldValue.Text).value)
    }

    // ─── round-trip через appJson ─────────────────────────────────────────────

    @Test
    fun `CustomFieldDefinition Select сериализуется и десериализуется через appJson`() {
        val original: CustomFieldDefinition =
            CustomFieldDefinition.Select(
                fieldKey = key("level"),
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
                fieldKey = key("age"),
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
        val original: CustomFieldValue = CustomFieldValue.Select(fieldKey = key("level"), value = "beginner")
        val encoded = appJson.encodeToString(CustomFieldValue.serializer(), original)
        val decoded = appJson.decodeFromString(CustomFieldValue.serializer(), encoded)
        assertEquals(original, decoded)
    }

    private fun key(value: String): CustomFieldKey = value.toFieldKey().getOrElse { error("Невалидный тестовый CustomFieldKey: $value") }
}
