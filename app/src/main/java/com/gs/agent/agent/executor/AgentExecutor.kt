package com.gs.agent.agent.executor

import android.content.Context
import com.gs.agent.agent.tools.ToolRegistry
import com.gs.agent.data.models.AppSettings
import com.gs.agent.data.models.ChatMessage
import com.gs.agent.data.models.ProviderConfig
import com.gs.agent.data.models.Role
import com.gs.agent.data.models.ToolInvocation
import com.gs.agent.data.models.ToolStatus
import com.gs.agent.data.providers.AiClient
import com.gs.agent.data.providers.StreamEvent
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Drives the agent loop:
 *  - calls the model
 *  - parses any ```tool ... ``` blocks
 *  - executes the tool
 *  - feeds the result back as a new turn
 *  - repeats until the model produces no more tool calls or maxSteps reached.
 */
class AgentExecutor(
    private val context: Context,
    private val client: AiClient,
    private val maxSteps: Int = 12
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed class Event {
        data class AssistantDelta(val text: String) : Event()
        data class AssistantMessage(val message: ChatMessage) : Event()
        data class ToolStart(val invocation: ToolInvocation) : Event()
        data class ToolFinish(val invocation: ToolInvocation) : Event()
        data class ConsoleLine(val text: String, val kind: ConsoleKind) : Event()
        data class Error(val message: String) : Event()
        object Done : Event()
    }

    enum class ConsoleKind { INFO, TOOL, OUTPUT, ERROR }

    suspend fun run(
        history: List<ChatMessage>,
        userInput: String,
        config: ProviderConfig,
        settings: AppSettings,
        emit: suspend (Event) -> Unit
    ) {
        val workingHistory = history.toMutableList()
        // Add user message
        workingHistory.add(ChatMessage(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            content = userInput
        ))

        val systemPrompt = buildSystemPrompt(settings)
        var step = 0

        while (step < maxSteps) {
            step++
            emit(Event.ConsoleLine("⟶ Calling model (step $step)…", ConsoleKind.INFO))

            val assistantId = UUID.randomUUID().toString()
            val accumulator = StringBuilder()
            var errored = false

            client.streamChat(AiClient.ChatRequest(
                config = config,
                messages = workingHistory,
                systemPrompt = systemPrompt,
                temperature = settings.temperature,
                maxTokens = settings.maxTokens,
                stream = settings.streamResponses
            )).collect { ev ->
                when (ev) {
                    is StreamEvent.Delta -> {
                        accumulator.append(ev.text)
                        emit(Event.AssistantDelta(ev.text))
                    }
                    is StreamEvent.Error -> {
                        errored = true
                        emit(Event.Error(ev.message))
                    }
                    StreamEvent.Done -> { /* end of stream */ }
                }
            }

            val finalText = accumulator.toString()
            val assistantMsg = ChatMessage(
                id = assistantId,
                role = Role.ASSISTANT,
                content = finalText
            )
            workingHistory.add(assistantMsg)
            emit(Event.AssistantMessage(assistantMsg))

            if (errored) {
                emit(Event.Done); return
            }

            // Extract tool calls
            val toolCalls = if (settings.enableTools) extractToolCalls(finalText) else emptyList()
            if (toolCalls.isEmpty()) {
                emit(Event.ConsoleLine("✓ Finished (no further tool calls).", ConsoleKind.INFO))
                emit(Event.Done)
                return
            }

            // Execute each tool sequentially
            val toolOutputs = StringBuilder()
            for (call in toolCalls) {
                val tool = ToolRegistry.get(call.name)
                if (tool == null) {
                    val msg = "Unknown tool: ${call.name}"
                    emit(Event.ConsoleLine(msg, ConsoleKind.ERROR))
                    toolOutputs.append("Tool ${call.name} → ERROR: $msg\n\n")
                    continue
                }

                val inv = ToolInvocation(name = call.name, arguments = call.argumentsRaw, status = ToolStatus.RUNNING)
                emit(Event.ToolStart(inv))
                emit(Event.ConsoleLine("▶ ${call.name}  ${call.argumentsRaw.take(160)}", ConsoleKind.TOOL))

                val result = runCatching {
                    tool.execute(context, call.arguments)
                }.getOrElse {
                    com.gs.agent.agent.tools.ToolResult(false, "Tool threw: ${it.message}")
                }

                val finishedInv = inv.copy(
                    status = if (result.success) ToolStatus.SUCCESS else ToolStatus.ERROR,
                    result = result.output,
                    finishedAt = System.currentTimeMillis()
                )
                emit(Event.ToolFinish(finishedInv))
                val truncatedNote = if (result.truncated) "  (truncated)" else ""
                emit(Event.ConsoleLine(
                    "← ${call.name} ${if (result.success) "OK" else "FAIL"}$truncatedNote\n${result.output.take(2000)}",
                    if (result.success) ConsoleKind.OUTPUT else ConsoleKind.ERROR
                ))

                toolOutputs.append("Tool: ${call.name}\nStatus: ${if (result.success) "success" else "error"}\nOutput:\n").append(result.output.take(8000)).append("\n\n")
            }

            // Add tool result as next user-role turn so the model can keep working
            workingHistory.add(ChatMessage(
                id = UUID.randomUUID().toString(),
                role = Role.USER,
                content = "Tool results:\n\n$toolOutputs\nContinue with the task, or give the final answer if done."
            ))
        }

        emit(Event.ConsoleLine("⚠ Reached max steps ($maxSteps).", ConsoleKind.INFO))
        emit(Event.Done)
    }

    private fun buildSystemPrompt(settings: AppSettings): String = buildString {
        append(settings.systemPrompt)
        append("\n\n")
        append(ToolRegistry.describeAllForPrompt())
    }

    private data class ParsedToolCall(val name: String, val arguments: JsonObject, val argumentsRaw: String)

    private fun extractToolCalls(text: String): List<ParsedToolCall> {
        val out = mutableListOf<ParsedToolCall>()
        // Match ```tool ... ``` blocks (also accept "json" as a fallback when content has name+arguments)
        val pattern = Regex("```(?:tool|json)\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        for (m in pattern.findAll(text)) {
            val body = m.groupValues[1].trim()
            try {
                val obj = json.parseToJsonElement(body).jsonObject
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val argsEl = obj["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                out += ParsedToolCall(name = name, arguments = argsEl, argumentsRaw = argsEl.toString())
            } catch (_: Throwable) { /* not a tool block */ }
        }
        return out
    }
}
