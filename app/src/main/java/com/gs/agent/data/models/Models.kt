package com.gs.agent.data.models

import kotlinx.serialization.Serializable

/**
 * AI Provider type - defines API protocol/format
 */
enum class ProviderType {
    OPENAI_COMPATIBLE, // Most providers use OpenAI-compatible Chat Completions API
    ANTHROPIC,         // Native Anthropic Messages API
    GOOGLE_GEMINI      // Native Google Generative Language API
}

/**
 * Built-in provider with default endpoint and suggested models.
 * Users can override base URL and add custom model IDs.
 */
@Serializable
data class ProviderPreset(
    val id: String,
    val displayName: String,
    val type: ProviderType,
    val defaultBaseUrl: String,
    val docsUrl: String = "",
    val apiKeyHint: String = "",
    val suggestedModels: List<String> = emptyList()
)

object Providers {
    val ALL: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "openai",
            displayName = "OpenAI",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.openai.com/v1",
            docsUrl = "https://platform.openai.com/docs/api-reference",
            apiKeyHint = "sk-...",
            suggestedModels = listOf(
                "gpt-5", "gpt-5-mini", "gpt-5-nano",
                "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
                "gpt-4o", "gpt-4o-mini",
                "o3", "o3-mini", "o1", "o1-mini",
                "chatgpt-4o-latest"
            )
        ),
        ProviderPreset(
            id = "anthropic",
            displayName = "Anthropic Claude",
            type = ProviderType.ANTHROPIC,
            defaultBaseUrl = "https://api.anthropic.com/v1",
            docsUrl = "https://docs.anthropic.com/",
            apiKeyHint = "sk-ant-...",
            suggestedModels = listOf(
                "claude-opus-4-5",
                "claude-sonnet-4-5",
                "claude-opus-4-1-20250805",
                "claude-sonnet-4-20250514",
                "claude-3-7-sonnet-latest",
                "claude-3-5-sonnet-latest",
                "claude-3-5-haiku-latest",
                "claude-3-opus-20240229"
            )
        ),
        ProviderPreset(
            id = "google",
            displayName = "Google Gemini",
            type = ProviderType.GOOGLE_GEMINI,
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            docsUrl = "https://ai.google.dev/gemini-api/docs",
            apiKeyHint = "AIza...",
            suggestedModels = listOf(
                "gemini-2.5-pro",
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite",
                "gemini-2.0-flash",
                "gemini-2.0-flash-lite",
                "gemini-1.5-pro-latest",
                "gemini-1.5-flash-latest"
            )
        ),
        ProviderPreset(
            id = "openrouter",
            displayName = "OpenRouter",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            docsUrl = "https://openrouter.ai/docs",
            apiKeyHint = "sk-or-...",
            suggestedModels = listOf(
                "openai/gpt-5",
                "anthropic/claude-opus-4-5",
                "anthropic/claude-sonnet-4-5",
                "google/gemini-2.5-pro",
                "meta-llama/llama-3.3-70b-instruct",
                "deepseek/deepseek-chat",
                "x-ai/grok-4",
                "mistralai/mistral-large"
            )
        ),
        ProviderPreset(
            id = "groq",
            displayName = "Groq",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.groq.com/openai/v1",
            docsUrl = "https://console.groq.com/docs",
            apiKeyHint = "gsk_...",
            suggestedModels = listOf(
                "llama-3.3-70b-versatile",
                "llama-3.1-70b-versatile",
                "llama-3.1-8b-instant",
                "mixtral-8x7b-32768",
                "gemma2-9b-it",
                "deepseek-r1-distill-llama-70b"
            )
        ),
        ProviderPreset(
            id = "xai",
            displayName = "xAI Grok",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.x.ai/v1",
            docsUrl = "https://docs.x.ai/",
            apiKeyHint = "xai-...",
            suggestedModels = listOf(
                "grok-4",
                "grok-4-fast",
                "grok-3",
                "grok-3-mini",
                "grok-2-1212",
                "grok-2-vision-1212",
                "grok-beta"
            )
        ),
        ProviderPreset(
            id = "deepseek",
            displayName = "DeepSeek",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.deepseek.com/v1",
            docsUrl = "https://api-docs.deepseek.com/",
            apiKeyHint = "sk-...",
            suggestedModels = listOf(
                "deepseek-chat",
                "deepseek-reasoner",
                "deepseek-coder"
            )
        ),
        ProviderPreset(
            id = "mistral",
            displayName = "Mistral AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.mistral.ai/v1",
            docsUrl = "https://docs.mistral.ai/",
            apiKeyHint = "",
            suggestedModels = listOf(
                "mistral-large-latest",
                "mistral-medium-latest",
                "mistral-small-latest",
                "codestral-latest",
                "open-mistral-nemo",
                "pixtral-large-latest",
                "ministral-8b-latest",
                "ministral-3b-latest"
            )
        ),
        ProviderPreset(
            id = "together",
            displayName = "Together AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.together.xyz/v1",
            docsUrl = "https://docs.together.ai/",
            apiKeyHint = "",
            suggestedModels = listOf(
                "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo",
                "Qwen/Qwen2.5-72B-Instruct-Turbo",
                "deepseek-ai/DeepSeek-V3",
                "mistralai/Mixtral-8x22B-Instruct-v0.1"
            )
        ),
        ProviderPreset(
            id = "fireworks",
            displayName = "Fireworks AI",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.fireworks.ai/inference/v1",
            docsUrl = "https://docs.fireworks.ai/",
            apiKeyHint = "fw_...",
            suggestedModels = listOf(
                "accounts/fireworks/models/llama-v3p3-70b-instruct",
                "accounts/fireworks/models/llama-v3p1-405b-instruct",
                "accounts/fireworks/models/deepseek-v3",
                "accounts/fireworks/models/qwen2p5-72b-instruct"
            )
        ),
        ProviderPreset(
            id = "cohere",
            displayName = "Cohere",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.cohere.ai/compatibility/v1",
            docsUrl = "https://docs.cohere.com/",
            apiKeyHint = "",
            suggestedModels = listOf(
                "command-r-plus",
                "command-r",
                "command-a-03-2025",
                "command-light"
            )
        ),
        ProviderPreset(
            id = "perplexity",
            displayName = "Perplexity",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://api.perplexity.ai",
            docsUrl = "https://docs.perplexity.ai/",
            apiKeyHint = "pplx-...",
            suggestedModels = listOf(
                "llama-3.1-sonar-large-128k-online",
                "llama-3.1-sonar-small-128k-online",
                "llama-3.1-sonar-huge-128k-online",
                "sonar-pro",
                "sonar",
                "sonar-reasoning"
            )
        ),
        ProviderPreset(
            id = "ollama",
            displayName = "Ollama (Local)",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "http://localhost:11434/v1",
            docsUrl = "https://github.com/ollama/ollama/blob/main/docs/api.md",
            apiKeyHint = "ollama (any string)",
            suggestedModels = listOf(
                "llama3.3",
                "llama3.2",
                "llama3.1",
                "qwen2.5",
                "mistral",
                "deepseek-r1",
                "phi4",
                "gemma2"
            )
        ),
        ProviderPreset(
            id = "lmstudio",
            displayName = "LM Studio (Local)",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "http://localhost:1234/v1",
            docsUrl = "https://lmstudio.ai/docs/api/openai-api",
            apiKeyHint = "lm-studio (any string)",
            suggestedModels = listOf("local-model")
        ),
        ProviderPreset(
            id = "custom",
            displayName = "Custom (OpenAI compatible)",
            type = ProviderType.OPENAI_COMPATIBLE,
            defaultBaseUrl = "https://your-endpoint.com/v1",
            docsUrl = "",
            apiKeyHint = "Any API key",
            suggestedModels = emptyList()
        )
    )

    fun byId(id: String): ProviderPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

/**
 * User-configured provider instance
 */
@Serializable
data class ProviderConfig(
    val providerId: String,            // matches ProviderPreset.id
    val baseUrl: String,               // custom or default
    val apiKey: String,
    val selectedModel: String,         // currently selected model id
    val customModels: List<String> = emptyList(),
    val extraHeaders: Map<String, String> = emptyMap()
)

@Serializable
data class AppSettings(
    val activeProviderId: String = "openai",
    val providers: Map<String, ProviderConfig> = emptyMap(),
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val enableTools: Boolean = true,
    val autoApproveTools: Boolean = false,
    val streamResponses: Boolean = true,
    val showConsole: Boolean = true
)

const val DEFAULT_SYSTEM_PROMPT = """You are GS Agent, a highly capable AI agent running on Android. You can read, write, list and delete files on the user's device, execute shell commands, edit project files, and perform multi-step tasks autonomously.

When the user asks you to do something, follow this loop:
1. Think about what tools you need.
2. Call tools via the exact JSON tool-call format described below.
3. Observe the result of each tool call.
4. Continue until the task is fully complete, then give a final concise summary.

TOOL CALL FORMAT — emit a single fenced block like this when you want to call a tool:
```tool
{
  "name": "<tool_name>",
  "arguments": { ... }
}
```
Available tools will be listed in each turn. Always use absolute paths when possible. Be safe: confirm before destructive actions unless auto-approve is enabled.
"""

/**
 * Chat related models
 */
@Serializable
enum class Role { USER, ASSISTANT, SYSTEM, TOOL }

@Serializable
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolCalls: List<ToolInvocation> = emptyList()
)

@Serializable
data class ToolInvocation(
    val name: String,
    val arguments: String,
    val result: String? = null,
    val status: ToolStatus = ToolStatus.PENDING,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
)

@Serializable
enum class ToolStatus { PENDING, RUNNING, SUCCESS, ERROR, BLOCKED }

/**
 * Per-task console state — each user-initiated task gets its own console.
 */
data class TaskConsoleState(
    val taskId: String,                    // matches the user message ID that started the task
    val toolInvocations: List<ToolInvocation> = emptyList(),
    val consoleLines: List<UiConsoleLine> = emptyList(),
    val isExpanded: Boolean = true,
    val isFinished: Boolean = false,
    val errorMessage: String? = null,
    val taskName: String = ""
)

data class UiConsoleLine(
    val text: String,
    val kind: ConsoleLineKind,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ConsoleLineKind { INFO, TOOL, OUTPUT, ERROR }