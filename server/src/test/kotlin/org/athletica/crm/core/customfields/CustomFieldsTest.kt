package org.athletica.crm.core.customfields

import org.athletica.crm.core.entityids.OrgId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CustomFieldsTest {
    private val orgId = OrgId.new()

    private fun def(
        key: String,
        type: CustomFieldType,
        required: Boolean = false,
    ) = CustomFieldDefinition(
        orgId = orgId,
        entityType = "client",
        fieldKey = key,
        label = key,
        fieldType = type,
        isRequired = required,
        isSearchable = false,
        isSortable = false,
    )

    // ─── validate ─────────────────────────────────────────────────────────────

    @Test
    fun `validate возвращает пустой список для корректных значений`() {
        val fields =
            CustomFields(
                listOf(
                    def("name", CustomFieldType.Text),
                    def("age", CustomFieldType.Number),
                ),
            )
        val errors = fields.validate(mapOf("name" to "Иван", "age" to "25"))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate возвращает REQUIRED для обязательного пустого поля`() {
        val fields = CustomFields(listOf(def("name", CustomFieldType.Text, required = true)))
        val errors = fields.validate(mapOf("name" to null))
        assertEquals(1, errors.size)
        assertEquals("name", errors.first().fieldKey)
        assertEquals("REQUIRED", errors.first().code)
    }

    @Test
    fun `validate не требует необязательное пустое поле`() {
        val fields = CustomFields(listOf(def("name", CustomFieldType.Text, required = false)))
        val errors = fields.validate(mapOf("name" to null))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validate возвращает INVALID_TYPE для нечислового значения в Number`() {
        val fields = CustomFields(listOf(def("age", CustomFieldType.Number)))
        val errors = fields.validate(mapOf("age" to "не число"))
        assertEquals(1, errors.size)
        assertEquals("INVALID_TYPE", errors.first().code)
    }

    @Test
    fun `validate принимает числовое значение в Number`() {
        val fields = CustomFields(listOf(def("age", CustomFieldType.Number)))
        assertTrue(fields.validate(mapOf("age" to "42")).isEmpty())
        assertTrue(fields.validate(mapOf("age" to "3.14")).isEmpty())
        assertTrue(fields.validate(mapOf("age" to "-5")).isEmpty())
    }

    @Test
    fun `validate возвращает INVALID_TYPE для неверной даты`() {
        val fields = CustomFields(listOf(def("birthday", CustomFieldType.Date)))
        val errors = fields.validate(mapOf("birthday" to "32-13-2000"))
        assertEquals(1, errors.size)
        assertEquals("INVALID_TYPE", errors.first().code)
    }

    @Test
    fun `validate принимает корректную дату ISO`() {
        val fields = CustomFields(listOf(def("birthday", CustomFieldType.Date)))
        assertTrue(fields.validate(mapOf("birthday" to "2000-05-15")).isEmpty())
    }

    @Test
    fun `validate возвращает INVALID_TYPE для Boolean с неверным значением`() {
        val fields = CustomFields(listOf(def("active", CustomFieldType.Boolean)))
        val errors = fields.validate(mapOf("active" to "yes"))
        assertEquals(1, errors.size)
        assertEquals("INVALID_TYPE", errors.first().code)
    }

    @Test
    fun `validate принимает true и false для Boolean`() {
        val fields = CustomFields(listOf(def("active", CustomFieldType.Boolean)))
        assertTrue(fields.validate(mapOf("active" to "true")).isEmpty())
        assertTrue(fields.validate(mapOf("active" to "false")).isEmpty())
    }

    @Test
    fun `validate возвращает UNKNOWN_OPTION для значения вне списка Select`() {
        val fields =
            CustomFields(listOf(def("level", CustomFieldType.Select(listOf("beginner", "advanced")))))
        val errors = fields.validate(mapOf("level" to "expert"))
        assertEquals(1, errors.size)
        assertEquals("UNKNOWN_OPTION", errors.first().code)
    }

    @Test
    fun `validate принимает допустимый вариант Select`() {
        val fields =
            CustomFields(listOf(def("level", CustomFieldType.Select(listOf("beginner", "advanced")))))
        assertTrue(fields.validate(mapOf("level" to "beginner")).isEmpty())
    }

    @Test
    fun `validate собирает несколько ошибок одновременно`() {
        val fields =
            CustomFields(
                listOf(
                    def("name", CustomFieldType.Text, required = true),
                    def("age", CustomFieldType.Number),
                ),
            )
        val errors = fields.validate(mapOf("name" to null, "age" to "abc"))
        assertEquals(2, errors.size)
    }

    // ─── deserialize ──────────────────────────────────────────────────────────

    @Test
    fun `deserialize возвращает TextValue для Text поля`() {
        val fields = CustomFields(listOf(def("name", CustomFieldType.Text)))
        val result = fields.deserialize(mapOf("name" to "Иван"))
        assertIs<CustomFieldValue.TextValue>(result["name"]).also {
            assertEquals("Иван", it.value)
        }
    }

    @Test
    fun `deserialize возвращает NumberValue для Number поля`() {
        val fields = CustomFields(listOf(def("age", CustomFieldType.Number)))
        val result = fields.deserialize(mapOf("age" to "25"))
        assertIs<CustomFieldValue.NumberValue>(result["age"]).also {
            assertEquals(25.0, it.value)
        }
    }

    @Test
    fun `deserialize возвращает BooleanValue для Boolean поля`() {
        val fields = CustomFields(listOf(def("active", CustomFieldType.Boolean)))
        assertIs<CustomFieldValue.BooleanValue>(fields.deserialize(mapOf("active" to "true"))["active"])
            .also { assertTrue(it.value) }
        assertIs<CustomFieldValue.BooleanValue>(fields.deserialize(mapOf("active" to "false"))["active"])
            .also { assertTrue(!it.value) }
    }

    @Test
    fun `deserialize возвращает SelectValue для допустимого варианта`() {
        val fields =
            CustomFields(listOf(def("level", CustomFieldType.Select(listOf("beginner", "advanced")))))
        val result = fields.deserialize(mapOf("level" to "beginner"))
        assertIs<CustomFieldValue.SelectValue>(result["level"]).also {
            assertEquals("beginner", it.value)
        }
    }

    @Test
    fun `deserialize возвращает Empty для недопустимого варианта Select`() {
        val fields =
            CustomFields(listOf(def("level", CustomFieldType.Select(listOf("beginner", "advanced")))))
        assertIs<CustomFieldValue.Empty>(fields.deserialize(mapOf("level" to "expert"))["level"])
    }

    @Test
    fun `deserialize возвращает Empty для null значения`() {
        val fields = CustomFields(listOf(def("name", CustomFieldType.Text)))
        assertIs<CustomFieldValue.Empty>(fields.deserialize(mapOf("name" to null))["name"])
    }

    @Test
    fun `deserialize молча отбрасывает неизвестные ключи`() {
        val fields = CustomFields(listOf(def("name", CustomFieldType.Text)))
        val result = fields.deserialize(mapOf("name" to "Иван", "unknown_key" to "value"))
        assertEquals(setOf("name"), result.keys)
    }

    // ─── serialize ────────────────────────────────────────────────────────────

    @Test
    fun `serialize возвращает строку для TextValue`() {
        val fields = CustomFields(listOf(def("name", CustomFieldType.Text)))
        val serialized = fields.serialize(mapOf("name" to CustomFieldValue.TextValue("Иван")))
        assertEquals("Иван", serialized["name"])
    }

    @Test
    fun `serialize возвращает null для Empty`() {
        val fields = CustomFields(listOf(def("name", CustomFieldType.Text)))
        val serialized = fields.serialize(mapOf("name" to CustomFieldValue.Empty))
        assertEquals(null, serialized["name"])
    }

    @Test
    fun `serialize возвращает строку для NumberValue`() {
        val fields = CustomFields(listOf(def("age", CustomFieldType.Number)))
        val serialized = fields.serialize(mapOf("age" to CustomFieldValue.NumberValue(42.0)))
        assertEquals("42.0", serialized["age"])
    }
}
