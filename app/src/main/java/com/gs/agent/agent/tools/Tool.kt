package com.gs.agent.agent.tools

import android.content.Context
import kotlinx.serialization.json.JsonObject

/**
 * A tool that the agent can invoke. Each tool has a unique name,
 * a human-readable description, an argument schema (informational),
 * and an `execute` function that runs the actual operation.
 */
interface Tool {
    val name: String
    val description: String
    val argumentsSchema: String // JSON schema-like description shown to the model

    suspend fun execute(context: Context, args: JsonObject): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val output: String,
    val truncated: Boolean = false
)

object ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) { tools[tool.name] = tool }

    fun get(name: String): Tool? = tools[name]
    fun all(): List<Tool> = tools.values.sortedBy { it.name }

    fun describeAllForPrompt(): String = buildString {
        append("Available tools:\n")
        for (t in all()) {
            append("• ").append(t.name).append(" — ").append(t.description).append("\n")
            append("  args: ").append(t.argumentsSchema).append("\n")
        }
    }
}
