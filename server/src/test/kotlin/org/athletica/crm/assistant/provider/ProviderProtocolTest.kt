package org.athletica.crm.assistant.provider

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.message.MessagePart
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Настоящие HTTP-клиенты Koog обращаются только к локальной подмене API. */
class ProviderProtocolTest {
    /** Проверяет возможности конфигурируемой модели, сериализацию tools и разбор tool call. */
    @Test
    fun allProvidersExchangeToolCalls(): Unit =
        runBlocking {
            listOf("openai", "anthropic", "openrouter").forEach { provider ->
                val request = CompletableDeferred<String>()
                val response =
                    if (provider == "anthropic") {
                        """{"id":"msg_1","type":"message","role":"assistant","model":"fake","content":[{"type":"tool_use","id":"call_1","name":"prepare_client","input":{}}],"stop_reason":"tool_use","usage":{"input_tokens":1,"output_tokens":1}}"""
                    } else {
                        """{"id":"chatcmpl_1","object":"chat.completion","created":0,"model":"fake","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"prepare_client","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
                    }
                val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
                server.createContext("/") { exchange ->
                    request.complete(exchange.requestBody.bufferedReader().use { it.readText() })
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    val bytes = response.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                server.start()
                try {
                    val baseUrl = "http://127.0.0.1:${server.address.port}"
                    AssistantConnection.create(provider, "fake", "test-key").use { connection ->
                        val model = checkNotNull(connection.model)
                        val client: LLMClient =
                            when (provider) {
                                "openai" -> OpenAILLMClient("test-key", OpenAIClientSettings(baseUrl = baseUrl))
                                "anthropic" -> AnthropicLLMClient("test-key", AnthropicClientSettings(baseUrl = baseUrl, modelVersionsMap = mapOf(model to model.id)))
                                "openrouter" -> OpenRouterLLMClient("test-key", OpenRouterClientSettings(baseUrl = baseUrl))
                                else -> error("Unsupported test provider")
                            }
                        client.use {
                            val answer =
                                client.execute(
                                    Prompt.build("test") { user("Добавь клиента") },
                                    model,
                                    listOf(ToolDescriptor("prepare_client", "Подготовить карточку")),
                                )
                            val call = answer.parts.filterIsInstance<MessagePart.Tool.Call>().single()
                            assertEquals("prepare_client", call.tool, provider)
                            assertEquals("{}", call.args, provider)
                            val body = request.await()
                            assertTrue(body.contains("prepare_client"), provider)
                            assertTrue(body.contains("fake"), provider)
                        }
                    }
                } finally {
                    server.stop(0)
                }
            }
        }
}
