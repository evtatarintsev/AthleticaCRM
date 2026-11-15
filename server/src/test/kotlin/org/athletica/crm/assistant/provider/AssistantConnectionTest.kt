package org.athletica.crm.assistant.provider

import ai.koog.prompt.llm.LLMProvider
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Проверяет конфигурацию без сетевых запросов и настоящих ключей. */
class AssistantConnectionTest {
    /** Отсутствующие, неполные и неизвестные настройки отключают помощника. */
    @Test
    fun unavailableWithoutCompleteConfiguration() {
        assertFalse(AssistantConnection.from(MapApplicationConfig()).available)
        listOf(
            Triple("", "model", "private-key"),
            Triple("unknown", "model", "private-key"),
            Triple("openai", "", "private-key"),
            Triple("openai", "model", ""),
        ).forEach { (provider, model, key) ->
            AssistantConnection.create(provider, model, key).use { connection ->
                assertFalse(connection.available)
                assertFalse(connection.toString().contains(key.ifEmpty { "private-key" }))
            }
        }
    }

    /** Каждый поддерживаемый провайдер создаёт ровно одно закрываемое подключение. */
    @Test
    fun selectsConfiguredProvider() {
        mapOf("openai" to LLMProvider.OpenAI, "anthropic" to LLMProvider.Anthropic, "openrouter" to LLMProvider.OpenRouter, "deepseek" to LLMProvider.DeepSeek)
            .forEach { (provider, expected) ->
                AssistantConnection.create(provider, "chosen-model", "test-key").use { connection ->
                    assertTrue(connection.available)
                    assertEquals(expected, connection.model?.provider)
                    assertEquals("chosen-model", connection.model?.id)
                    assertFalse(connection.toString().contains("test-key"))
                }
            }
    }
}
