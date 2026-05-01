package org.athletica.crm.core.customfields

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CustomFieldTypeTest {
    // ─── name ─────────────────────────────────────────────────────────────────

    @Test
    fun `Text имеет name text`() {
        assertEquals("text", CustomFieldType.Text.name)
    }

    @Test
    fun `Number имеет name number`() {
        assertEquals("number", CustomFieldType.Number.name)
    }

    @Test
    fun `Date имеет name date`() {
        assertEquals("date", CustomFieldType.Date.name)
    }

    @Test
    fun `Boolean имеет name boolean`() {
        assertEquals("boolean", CustomFieldType.Boolean.name)
    }

    @Test
    fun `Phone имеет name phone`() {
        assertEquals("phone", CustomFieldType.Phone.name)
    }

    @Test
    fun `Email имеет name email`() {
        assertEquals("email", CustomFieldType.Email.name)
    }

    @Test
    fun `Url имеет name url`() {
        assertEquals("url", CustomFieldType.Url.name)
    }

    @Test
    fun `Select имеет name select`() {
        assertEquals("select", CustomFieldType.Select(listOf("A", "B")).name)
    }

    // ─── configJson ───────────────────────────────────────────────────────────

    @Test
    fun `простые типы возвращают пустой объект configJson`() {
        val simpleTypes =
            listOf(
                CustomFieldType.Text,
                CustomFieldType.Number,
                CustomFieldType.Date,
                CustomFieldType.Boolean,
                CustomFieldType.Phone,
                CustomFieldType.Email,
                CustomFieldType.Url,
            )
        simpleTypes.forEach { type ->
            assertEquals("{}", type.configJson(), "configJson для $type должен быть {}")
        }
    }

    @Test
    fun `Select сериализует options в configJson`() {
        val select = CustomFieldType.Select(listOf("adult", "junior", "senior"))
        val json = select.configJson()
        assertEquals("""{"options":["adult","junior","senior"]}""", json)
    }

    @Test
    fun `Select с пустым списком options возвращает пустой массив`() {
        val select = CustomFieldType.Select(emptyList())
        assertEquals("""{"options":[]}""", select.configJson())
    }

    // ─── Select.parseOptions ──────────────────────────────────────────────────

    @Test
    fun `Select parseOptions извлекает варианты из корректного JSON`() {
        val options = CustomFieldType.Select.parseOptions("""{"options":["adult","junior"]}""")
        assertEquals(listOf("adult", "junior"), options)
    }

    @Test
    fun `Select parseOptions возвращает пустой список для пустого массива`() {
        val options = CustomFieldType.Select.parseOptions("""{"options":[]}""")
        assertTrue(options.isEmpty())
    }

    @Test
    fun `Select parseOptions возвращает пустой список если options отсутствует`() {
        val options = CustomFieldType.Select.parseOptions("{}")
        assertTrue(options.isEmpty())
    }

    @Test
    fun `Select parseOptions roundtrip configJson`() {
        val original = CustomFieldType.Select(listOf("A", "B", "C"))
        val parsed = CustomFieldType.Select.parseOptions(original.configJson())
        assertEquals(original.options, parsed)
    }

    // ─── parse ────────────────────────────────────────────────────────────────

    @Test
    fun `parse возвращает Text для строки text`() {
        assertIs<CustomFieldType.Text>(CustomFieldType.parse("text", "{}"))
    }

    @Test
    fun `parse возвращает Number для строки number`() {
        assertIs<CustomFieldType.Number>(CustomFieldType.parse("number", "{}"))
    }

    @Test
    fun `parse возвращает Date для строки date`() {
        assertIs<CustomFieldType.Date>(CustomFieldType.parse("date", "{}"))
    }

    @Test
    fun `parse возвращает Boolean для строки boolean`() {
        assertIs<CustomFieldType.Boolean>(CustomFieldType.parse("boolean", "{}"))
    }

    @Test
    fun `parse возвращает Phone для строки phone`() {
        assertIs<CustomFieldType.Phone>(CustomFieldType.parse("phone", "{}"))
    }

    @Test
    fun `parse возвращает Email для строки email`() {
        assertIs<CustomFieldType.Email>(CustomFieldType.parse("email", "{}"))
    }

    @Test
    fun `parse возвращает Url для строки url`() {
        assertIs<CustomFieldType.Url>(CustomFieldType.parse("url", "{}"))
    }

    @Test
    fun `parse возвращает Select с options`() {
        val result = CustomFieldType.parse("select", """{"options":["adult","junior"]}""")
        val select = assertIs<CustomFieldType.Select>(result)
        assertEquals(listOf("adult", "junior"), select.options)
    }

    @Test
    fun `parse возвращает Text для неизвестного типа`() {
        assertIs<CustomFieldType.Text>(CustomFieldType.parse("unknown_type", "{}"))
    }

    @Test
    fun `parse roundtrip для всех простых типов`() {
        val types =
            listOf(
                CustomFieldType.Text,
                CustomFieldType.Number,
                CustomFieldType.Date,
                CustomFieldType.Boolean,
                CustomFieldType.Phone,
                CustomFieldType.Email,
                CustomFieldType.Url,
            )
        types.forEach { type ->
            val parsed = CustomFieldType.parse(type.name, type.configJson())
            assertEquals(type, parsed, "roundtrip для $type")
        }
    }

    @Test
    fun `parse roundtrip для Select`() {
        val original = CustomFieldType.Select(listOf("X", "Y", "Z"))
        val parsed = CustomFieldType.parse(original.name, original.configJson())
        assertEquals(original, parsed)
    }
}
