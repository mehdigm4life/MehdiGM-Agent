package com.gs.agent.data.providers

import com.gs.agent.data.models.ChatMessage
import com.gs.agent.data.models.ProviderConfig
import com.gs.agent.data.models.ProviderType
import com.gs.agent.data.models.Providers
import com.gs.agent.data.models.Role
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Unified AI client. Handles three protocols:
 *  - OpenAI-compatible Chat Completions (most providers)
 *  - Anthropic Messages
 *  - Google Gemini generateContent
 */
class AiClient(
    private val httpClient: OkHttpClient = defaultClient()
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    data class ChatRequest(
        val config: ProviderConfig,
        val messages: List<ChatMessage>,
        val systemPrompt: String,
        val temperature: Float = 0.7f,
        val maxTokens: Int = 4096,
        val stream: Boolean = true
    )

    /**
     * Streams text deltas from the provider. Emits partial content tokens.
     */
    fun streamChat(request: ChatRequest): Flow<StreamEvent> = callbackFlow {
        val preset = Providers.byId(request.config.providerId)
        val (httpRequest, parser) = when (preset.type) {
            ProviderType.ANTHROPIC -> buildAnthropic(request)
            ProviderType.GOOGLE_GEMINI -> buildGemini(request)
            ProviderType.OPENAI_COMPATIBLE -> buildOpenAi(request)
        }

        if (request.stream) {
            val factory = EventSources.createFactory(httpClient)
            val source = factory.newEventSource(httpRequest, object : EventSourceListener() {
                override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                    try {
                        if (data == "[DONE]") {
                            trySend(StreamEvent.Done)
                            return
                        }
                        parser.parseEvent(type, data)?.let { trySend(it) }
                    } catch (t: Throwable) {
                        trySend(StreamEvent.Error(t.message ?: "parse error"))
                    }
                }
                override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                    val msg = buildString {
                        append(t?.message ?: "Network error")
                        response?.let {
                            append(" [HTTP ").append(it.code).append("]")
                            runCatching { it.body?.string() }.getOrNull()?.let { b -> append(" ").append(b.take(500)) }
                        }
                    }
                    trySend(StreamEvent.Error(msg))
                    close()
                }
                override fun onClosed(es: EventSource) { close() }
            })
            awaitClose { source.cancel() }
        } else {
            // non-streaming fallback
            try {
                val resp = httpClient.newCall(httpRequest).execute()
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    trySend(StreamEvent.Error("HTTP ${resp.code}: ${body.take(500)}"))
                } else {
                    val text = parser.parseFull(body)
                    if (text.isNotEmpty()) trySend(StreamEvent.Delta(text))
                    trySend(StreamEvent.Done)
                }
            } catch (e: IOException) {
                trySend(StreamEvent.Error(e.message ?: "Network error"))
            }
            awaitClose { }
        }
    }

    // ---------- OpenAI compatible ----------
    private fun buildOpenAi(req: ChatRequest): Pair<Request, Parser> {
        val url = req.config.baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildJsonObject {
            put("model", req.config.selectedModel)
            put("temperature", req.temperature)
            put("max_tokens", req.maxTokens)
            put("stream", req.stream)
            put("messages", buildJsonArray {
                if (req.systemPrompt.isNotBlank()) {
                    addJsonObject {
                        put("role", "system")
                        put("content", req.systemPrompt)
                    }
                }
                req.messages.forEach { m ->
                    addJsonObject {
                        put("role", when (m.role) {
                            Role.USER -> "user"
                            Role.ASSISTANT -> "assistant"
                            Role.SYSTEM -> "system"
                            Role.TOOL -> "user" // tool results sent as user content for portability
                        })
                        put("content", m.content)
                    }
                }
            })
        }.toString()

        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
        if (req.config.apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer ${req.config.apiKey}")
        }
        if (req.config.providerId == "openrouter") {
            builder.addHeader("HTTP-Referer", "https://com.gs.agent")
            builder.addHeader("X-Title", "GS Agent")
        }
        req.config.extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        if (req.stream) builder.addHeader("Accept", "text/event-stream")

        return builder.build() to OpenAiParser(json)
    }

    // ---------- Anthropic ----------
    private fun buildAnthropic(req: ChatRequest): Pair<Request, Parser> {
        val url = req.config.baseUrl.trimEnd('/') + "/messages"
        val body = buildJsonObject {
            put("model", req.config.selectedModel)
            put("max_tokens", req.maxTokens)
            put("temperature", req.temperature)
            put("stream", req.stream)
            if (req.systemPrompt.isNotBlank()) put("system", req.systemPrompt)
            put("messages", buildJsonArray {
                req.messages.filter { it.role != Role.SYSTEM }.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == Role.ASSISTANT) "assistant" else "user")
                        put("content", m.content)
                    }
                }
            })
        }.toString()

        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("anthropic-dangerous-direct-browser-access", "true")
        if (req.config.apiKey.isNotBlank()) {
            builder.addHeader("x-api-key", req.config.apiKey)
        }
        req.config.extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        if (req.stream) builder.addHeader("Accept", "text/event-stream")

        return builder.build() to AnthropicParser(json)
    }

    // ---------- Google Gemini ----------
    private fun buildGemini(req: ChatRequest): Pair<Request, Parser> {
        val action = if (req.stream) "streamGenerateContent?alt=sse&key=${req.config.apiKey}" else "generateContent?key=${req.config.apiKey}"
        val url = req.config.baseUrl.trimEnd('/') + "/models/${req.config.selectedModel}:$action"
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                req.messages.filter { it.role != Role.SYSTEM }.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == Role.ASSISTANT) "model" else "user")
                        put("parts", buildJsonArray {
                            addJsonObject { put("text", m.content) }
                        })
                    }
                }
            })
            if (req.systemPrompt.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        addJsonObject { put("text", req.systemPrompt) }
                    })
                })
            }
            put("generationConfig", buildJsonObject {
                put("temperature", req.temperature)
                put("maxOutputTokens", req.maxTokens)
            })
        }.toString()

        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
        req.config.extraHeaders.forEach { (k, v) -> builder.addHeader(k, v) }
        if (req.stream) builder.addHeader("Accept", "text/event-stream")

        return builder.build() to GeminiParser(json)
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

sealed class StreamEvent {
    data class Delta(val text: String) : StreamEvent()
    object Done : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

private interface Parser {
    fun parseEvent(eventType: String?, data: String): StreamEvent?
    fun parseFull(body: String): String
}

private class OpenAiParser(val json: Json) : Parser {
    override fun parseEvent(eventType: String?, data: String): StreamEvent? {
        if (data.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(data).jsonObject
            val choices = obj["choices"]?.jsonArray ?: return null
            val first = choices.firstOrNull()?.jsonObject ?: return null
            val delta = first["delta"]?.jsonObject ?: first["message"]?.jsonObject ?: return null
            val txt = delta["content"]?.jsonPrimitive?.contentOrNull
            if (!txt.isNullOrEmpty()) StreamEvent.Delta(txt) else null
        } catch (_: Throwable) { null }
    }
    override fun parseFull(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val choices = obj["choices"]?.jsonArray ?: return ""
            val first = choices.firstOrNull()?.jsonObject ?: return ""
            first["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        } catch (_: Throwable) { "" }
    }
}

private class AnthropicParser(val json: Json) : Parser {
    override fun parseEvent(eventType: String?, data: String): StreamEvent? {
        if (data.isBlank()) return null
        return try {
            val obj = json.parseToJsonElement(data).jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull
            if (type == "content_block_delta") {
                val delta = obj["delta"]?.jsonObject ?: return null
                val txt = delta["text"]?.jsonPrimitive?.contentOrNull
                if (!txt.isNullOrEmpty()) StreamEvent.Delta(txt) else null
            } else if (type == "message_stop") {
                StreamEvent.Done
            } else null
        } catch (_: Throwable) { null }
    }
    override fun parseFull(body: String): String {
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val content = obj["content"]?.jsonArray ?: return ""
            buildString {
                for (block in content) {
                    val b = block.jsonObject
                    if (b["type"]?.jsonPrimitive?.contentOrNull == "text") {
                        b["text"]?.jsonPrimitive?.contentOrNull?.let { append(it) }
                    }
                }
            }
        } catch (_: Throwable) { "" }
    }
}

private class GeminiParser(val json: Json) : Parser {
    override fun parseEvent(eventType: String?, data: String): StreamEvent? {
        if (data.isBlank()) return null
        return try {
            extractText(json.parseToJsonElement(data).jsonObject)?.let { StreamEvent.Delta(it) }
        } catch (_: Throwable) { null }
    }
    override fun parseFull(body: String): String {
        return try {
            extractText(json.parseToJsonElement(body).jsonObject).orEmpty()
        } catch (_: Throwable) { "" }
    }
    private fun extractText(obj: JsonObject): String? {
        val candidates = obj["candidates"]?.jsonArray ?: return null
        val sb = StringBuilder()
        for (c in candidates) {
            val parts = c.jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray ?: continue
            for (p in parts) {
                p.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.let { sb.append(it) }
            }
        }
        return if (sb.isEmpty()) null else sb.toString()
    }
}
