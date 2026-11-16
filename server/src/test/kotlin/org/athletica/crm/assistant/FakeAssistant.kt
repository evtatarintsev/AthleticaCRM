package org.athletica.crm.assistant

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import org.athletica.crm.assistant.provider.AssistantConnection
import kotlin.time.Clock

/** Подмена только внешней модели: Koog и настоящая БД остаются в сценарии. */
internal class FakeAssistant(var respond: suspend (Prompt) -> Message.Assistant = { answer("Only new clients are supported") }) : PromptExecutor() {
    val prompts = mutableListOf<Prompt>()
    val connection = AssistantConnection(this, LLModel(LLMProvider.OpenAI, "fake"))
    var closed = false

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        prompts.add(prompt)
        return respond(prompt)
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> = error("Not used")

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult = error("Not used")

    override fun close() {
        closed = true
    }

    companion object {
        /** Текстовый ответ без tools. */
        fun answer(text: String) = Message.Assistant(text, ResponseMetaInfo(Clock.System.now()))

        /** Типизированный протокол tool call Koog с заданным JSON. */
        fun call(name: String, args: String) = Message.Assistant(MessagePart.Tool.Call("call", name, args), ResponseMetaInfo(Clock.System.now()))
    }
}
