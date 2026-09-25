package org.athletica.crm

import kotlinx.serialization.encodeToString
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.toFieldKey
import kotlin.test.Test
import kotlin.test.assertContains

/** Тесты JSON сервера. */
class ServerJsonTest {
    @Test
    fun `поле со значением по умолчанию попадает в ответ`() {
        val definition: CustomFieldDefinition =
            CustomFieldDefinition.Text(
                fieldKey = "nickname".toFieldKey().getOrNull() ?: error("ключ должен быть валидным"),
                label = "Прозвище",
            )

        val json = serverJson.encodeToString(definition)

        assertContains(json, "\"isRequired\":false")
        assertContains(json, "\"minLength\":null")
    }
}
