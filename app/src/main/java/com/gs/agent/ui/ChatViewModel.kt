package com.gs.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gs.agent.GsAgentApp
import com.gs.agent.agent.executor.AgentExecutor
import com.gs.agent.data.models.ChatMessage
import com.gs.agent.data.models.Role
import com.gs.agent.data.models.ToolInvocation
import com.gs.agent.data.models.ToolStatus
import com.gs.agent.data.providers.AiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class ConsoleLine(val text: String, val kind: String, val timestamp: Long = System.currentTimeMillis())

data class ChatUiState(
    val conversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingAssistantText: String = "",
    val isStreaming: Boolean = false,
    val toolInvocations: List<ToolInvocation> = emptyList(),
    val consoleLines: List<ConsoleLine> = emptyList(),
    val errorMessage: String? = null,
    val currentTaskName: String? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app as GsAgentApp
    private val aiClient = AiClient()
    private val executor = AgentExecutor(appCtx, aiClient)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var runJob: Job? = null

    fun bindConversation(conversationId: String) {
        if (_state.value.conversationId == conversationId) return
        _state.value = _state.value.copy(conversationId = conversationId, messages = emptyList(), consoleLines = emptyList(), toolInvocations = emptyList())
        viewModelScope.launch {
            appCtx.chatRepository.observeMessages(conversationId).collect { msgs ->
                _state.value = _state.value.copy(messages = msgs)
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        _state.value = _state.value.copy(isStreaming = false)
    }

    fun clearConsole() {
        _state.value = _state.value.copy(consoleLines = emptyList(), toolInvocations = emptyList())
    }

    fun send(text: String) {
        if (text.isBlank() || _state.value.isStreaming) return
        val convId = _state.value.conversationId ?: return
        val settings = appCtx.settingsRepository

        runJob = viewModelScope.launch {
            val current = settings.settingsFlow.first()
            val providerCfg = current.providers[current.activeProviderId]
            if (providerCfg == null || providerCfg.apiKey.isBlank() && providerCfg.providerId !in listOf("ollama", "lmstudio", "custom")) {
                _state.value = _state.value.copy(errorMessage = "Please configure an API key for ${current.activeProviderId} in Settings.")
                return@launch
            }
            if (providerCfg.selectedModel.isBlank()) {
                _state.value = _state.value.copy(errorMessage = "Please select a model in Settings.")
                return@launch
            }

            // Persist user message
            val userMsg = ChatMessage(id = UUID.randomUUID().toString(), role = Role.USER, content = text)
            appCtx.chatRepository.saveMessage(convId, userMsg)

            _state.value = _state.value.copy(
                isStreaming = true,
                streamingAssistantText = "",
                errorMessage = null,
                currentTaskName = text.lines().firstOrNull()?.take(80) ?: "Task",
                consoleLines = _state.value.consoleLines + ConsoleLine("User: ${text.take(200)}", "info")
            )

            val history = appCtx.chatRepository.getMessages(convId).filter { it.id != userMsg.id }

            executor.run(
                history = history,
                userInput = text,
                config = providerCfg,
                settings = current,
                emit = { ev ->
                    when (ev) {
                        is AgentExecutor.Event.AssistantDelta -> {
                            _state.value = _state.value.copy(
                                streamingAssistantText = _state.value.streamingAssistantText + ev.text
                            )
                        }
                        is AgentExecutor.Event.AssistantMessage -> {
                            appCtx.chatRepository.saveMessage(convId, ev.message)
                            _state.value = _state.value.copy(streamingAssistantText = "")
                        }
                        is AgentExecutor.Event.ToolStart -> {
                            _state.value = _state.value.copy(
                                toolInvocations = _state.value.toolInvocations + ev.invocation
                            )
                        }
                        is AgentExecutor.Event.ToolFinish -> {
                            _state.value = _state.value.copy(
                                toolInvocations = _state.value.toolInvocations.map {
                                    if (it.name == ev.invocation.name && it.status == ToolStatus.RUNNING) ev.invocation else it
                                }
                            )
                        }
                        is AgentExecutor.Event.ConsoleLine -> {
                            _state.value = _state.value.copy(
                                consoleLines = _state.value.consoleLines + ConsoleLine(ev.text, ev.kind.name.lowercase())
                            )
                        }
                        is AgentExecutor.Event.Error -> {
                            _state.value = _state.value.copy(errorMessage = ev.message)
                        }
                        AgentExecutor.Event.Done -> {
                            _state.value = _state.value.copy(isStreaming = false, currentTaskName = null)
                        }
                    }
                }
            )
        }
    }
}
