package org.athletica.crm.assistant.provider

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.serialization.typeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Проверяет настоящий цикл Koog с типизированным tool без внешнего подключения. */
class KoogCompatibilityTest {
    /** Аргументы тестового инструмента. */
    @Serializable
    data class Arguments(val name: String)

    /** Типизированный результат тестового инструмента. */
    @Serializable
    data class Result(val greeting: String)

    /** Выполняет обмен модель → инструмент → модель через стандартную стратегию Koog. */
    @Test
    fun typedToolRoundTrip() =
        runBlocking {
            var toolCalls = 0
            val tool =
                object : Tool<Arguments, Result>(
                    argsType = typeToken<Arguments>(),
                    resultType = typeToken<Result>(),
                    name = "greet",
                    description = "Приветствует по имени",
                ) {
                    override suspend fun execute(args: Arguments): Result {
                        toolCalls++
                        return Result("Привет, ${args.name}")
                    }
                }
            var modelCalls = 0
            val executor =
                object : PromptExecutor() {
                    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
                        modelCalls++
                        assertEquals(listOf("greet"), tools.map { it.name })
                        return if (modelCalls == 1) {
                            Message.Assistant(
                                MessagePart.Tool.Call("call-1", "greet", """{"name":"Иван"}"""),
                                ResponseMetaInfo(Clock.System.now()),
                            )
                        } else {
                            val result = prompt.messages.flatMap { it.parts }.filterIsInstance<MessagePart.Tool.Result>().single()
                            assertEquals("call-1", result.id)
                            assertTrue(result.output.contains("Привет, Иван"))
                            Message.Assistant("Готово", ResponseMetaInfo(Clock.System.now()))
                        }
                    }

                    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = error("Потоковая выдача не используется")

                    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("Модерация не используется")

                    override fun close() = Unit
                }
            val agent =
                AIAgent(
                    promptExecutor = executor,
                    llmModel = LLModel(LLMProvider.OpenAI, "fake"),
                    toolRegistry = ToolRegistry { tool(tool) },
                    maxIterations = 10,
                )
            try {
                assertEquals("Готово", agent.run("Приветствуй Ивана"))
                assertEquals(1, toolCalls)
                assertEquals(2, modelCalls)
            } finally {
                agent.close()
                executor.close()
            }
        }
}
