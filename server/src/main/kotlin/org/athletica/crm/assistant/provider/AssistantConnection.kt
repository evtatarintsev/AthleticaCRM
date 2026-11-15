package org.athletica.crm.assistant.provider

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.ktor.server.config.ApplicationConfig

/** Общее подключение сервера; секрет остаётся только внутри клиента Koog. */
class AssistantConnection internal constructor(
    internal val executor: PromptExecutor?,
    internal val model: LLModel?,
) : AutoCloseable {
    val available: Boolean get() = executor != null && model != null

    /** Освобождает HTTP-ресурсы выбранного провайдера. */
    override fun close() {
        executor?.close()
    }

    companion object {
        /** Возвращает отключённое подключение без HTTP-клиентов. */
        fun unavailable(): AssistantConnection = AssistantConnection(null, null)

        /** Читает необязательные серверные параметры, не сохраняя конфигурацию с секретом. */
        fun from(config: ApplicationConfig): AssistantConnection {
            val provider = config.propertyOrNull("assistant.provider")?.getString().orEmpty()
            val model = config.propertyOrNull("assistant.model")?.getString().orEmpty()
            val key = config.propertyOrNull("assistant.apiKey")?.getString().orEmpty()
            val connection = create(provider, model, key)
            if (!connection.available && listOf(provider, model, key).any { it.isNotBlank() }) {
                org.slf4j.LoggerFactory.getLogger(AssistantConnection::class.java).warn("Assistant connection unavailable: incomplete configuration or unsupported provider")
            }
            return connection
        }

        /** Неполные и неизвестные настройки оставляют CRM работоспособной без AI. */
        internal fun create(provider: String, model: String, apiKey: String): AssistantConnection {
            if (model.isBlank() || apiKey.isBlank()) {
                return unavailable()
            }
            val name = provider.trim().lowercase()
            val selected =
                when (name) {
                    "openai" -> LLMProvider.OpenAI
                    "anthropic" -> LLMProvider.Anthropic
                    "openrouter" -> LLMProvider.OpenRouter
                    else -> return unavailable()
                }
            val languageModel =
                LLModel(
                    selected,
                    model.trim(),
                    listOf(LLMCapability.Completion, LLMCapability.Tools) +
                        if (selected == LLMProvider.OpenAI) listOf(LLMCapability.OpenAIEndpoint.Completions) else emptyList(),
                    maxOutputTokens = 2048,
                )
            val client =
                when (name) {
                    "openai" -> OpenAILLMClient(apiKey)
                    "anthropic" -> AnthropicLLMClient(apiKey, AnthropicClientSettings(modelVersionsMap = mapOf(languageModel to languageModel.id)))
                    "openrouter" -> OpenRouterLLMClient(apiKey)
                    else -> return unavailable()
                }
            return AssistantConnection(
                MultiLLMPromptExecutor(client.llmProvider() to client),
                languageModel,
            )
        }
    }
}
