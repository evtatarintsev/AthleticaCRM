package org.athletica.crm.core.customfields

import arrow.core.Either
import arrow.core.raise.context.either
import kotlinx.serialization.SerializationException
import org.athletica.crm.api.appJson
import org.athletica.crm.core.errors.CommonDomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CustomFieldKeyTest {
    // ─── from ────────────────────────────────────────────────────────────────

    @Test
    fun `from принимает строчные латинские буквы`() {
        val result = either { CustomFieldKey.from("first_name") }
        assertEquals("first_name", assertIs<Either.Right<CustomFieldKey>>(result).value.value)
    }

    @Test
    fun `from принимает одиночный символ`() {
        val result = either { CustomFieldKey.from("a") }
        assertEquals("a", assertIs<Either.Right<CustomFieldKey>>(result).value.value)
    }

    @Test
    fun `from принимает только подчёркивания`() {
        val result = either { CustomFieldKey.from("___") }
        assertEquals("___", assertIs<Either.Right<CustomFieldKey>>(result).value.value)
    }

    @Test
    fun `from отвергает заглавные буквы`() {
        val result = either { CustomFieldKey.from("FirstName") }
        val left = assertIs<Either.Left<CommonDomainError>>(result).value
        assertEquals("INVALID_FIELD_KEY", left.code)
    }

    @Test
    fun `from отвергает цифры`() {
        val result = either { CustomFieldKey.from("field1") }
        assertIs<Either.Left<CommonDomainError>>(result)
    }

    @Test
    fun `from отвергает дефис`() {
        val result = either { CustomFieldKey.from("with-dash") }
        assertIs<Either.Left<CommonDomainError>>(result)
    }

    @Test
    fun `from отвергает пустую строку`() {
        val result = either { CustomFieldKey.from("") }
        assertIs<Either.Left<CommonDomainError>>(result)
    }

    @Test
    fun `from отвергает пробелы`() {
        val result = either { CustomFieldKey.from("first name") }
        assertIs<Either.Left<CommonDomainError>>(result)
    }

    @Test
    fun `String toFieldKey эквивалентен from`() {
        val viaExtension = either { "first_name".toFieldKey() }
        val viaCompanion = either { CustomFieldKey.from("first_name") }
        assertEquals(viaCompanion, viaExtension)
    }

    @Test
    fun `toString возвращает значение ключа`() {
        val k = assertIs<Either.Right<CustomFieldKey>>(either { CustomFieldKey.from("custom_key") }).value
        assertEquals("custom_key", k.toString())
    }

    // ─── сериализация ─────────────────────────────────────────────────────────

    @Test
    fun `сериализуется как обычная JSON-строка`() {
        val k = assertIs<Either.Right<CustomFieldKey>>(either { CustomFieldKey.from("first_name") }).value
        val encoded = appJson.encodeToString(CustomFieldKey.serializer(), k)
        assertEquals("\"first_name\"", encoded)
    }

    @Test
    fun `десериализует валидную строку`() {
        val decoded = appJson.decodeFromString(CustomFieldKey.serializer(), "\"first_name\"")
        assertEquals("first_name", decoded.value)
    }

    @Test
    fun `десериализация невалидной строки бросает SerializationException`() {
        assertFailsWith<SerializationException> {
            appJson.decodeFromString(CustomFieldKey.serializer(), "\"BadKey\"")
        }
    }

    @Test
    fun `десериализация JSON с цифрами бросает SerializationException`() {
        assertFailsWith<SerializationException> {
            appJson.decodeFromString(CustomFieldKey.serializer(), "\"field1\"")
        }
    }

    @Test
    fun `round-trip через сериализацию сохраняет значение`() {
        val original = assertIs<Either.Right<CustomFieldKey>>(either { CustomFieldKey.from("abc_def") }).value
        val encoded = appJson.encodeToString(CustomFieldKey.serializer(), original)
        val decoded = appJson.decodeFromString(CustomFieldKey.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `десериализация невалидного fieldKey внутри CustomFieldDefinition бросает SerializationException`() {
        val json = """{"fieldType":"text","fieldKey":"BadKey","label":"x","isRequired":false,"isSearchable":false,"isSortable":false}"""
        assertFailsWith<SerializationException> {
            appJson.decodeFromString(CustomFieldDefinition.serializer(), json)
        }
    }
}
