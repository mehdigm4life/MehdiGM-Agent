package com.gs.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gs.agent.GsAgentApp
import com.gs.agent.agent.executor.AgentExecutor
import com.gs.agent.data.models.ChatMessage
import com.gs.agent.data.models.ConsoleLineKind
import com.gs.agent.data.models.Role
import com.gs.agent.data.models.TaskConsoleState
import com.gs.agent.data.models.ToolInvocation
import com.gs.agent.data.models.ToolStatus
import com.gs.agent.data.models.UiConsoleLine
import com.gs.agent.data.providers.AiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val conversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streamingAssistantText: String = "",
    val isStreaming: Boolean = false,
    /** Per-task consoles — each user message gets its own console */
    val taskConsoles: Map<String, TaskConsoleState> = emptyMap(),
    val activeTaskId: String? = null,
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
        _state.value = _state.value.copy(
            conversationId = conversationId,
            messages = emptyList(),
            taskConsoles = emptyMap(),
            activeTaskId = null
        )
        viewModelScope.launch {
            appCtx.chatRepository.observeMessages(conversationId).collect { msgs ->
                _state.value = _state.value.copy(messages = msgs)
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        _state.value = _state.value.copy(isStreaming = false, activeTaskId = null)
    }

    /** Toggle the expanded/collapsed state of a task's console */
    fun toggleTaskConsole(taskId: String) {
        val current = _state.value.taskConsoles[taskId] ?: return
        _state.value = _state.value.copy(
            taskConsoles = _state.value.taskConsoles + (taskId to current.copy(isExpanded = !current.isExpanded))
        )
    }

    /**
     * Each user message is a new "task" with its own dedicated console.
     * We create a fresh TaskConsoleState for it.
     */
    fun send(text: String) {
        if (text.isBlank() || _state.value.isStreaming) return
        val convId = _state.value.conversationId ?: return
        val settingsRepo = appCtx.settingsRepository

        // Generate a task ID — corresponds to the user message that starts it
        val taskId = UUID.randomUUID().toString()

        runJob = viewModelScope.launch {
            val current = settingsRepo.settingsFlow.first()
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
            val userMsg = ChatMessage(id = taskId, role = Role.USER, content = text)
            appCtx.chatRepository.saveMessage(convId, userMsg)

            // Create a dedicated console for this task
            val taskName = text.lines().firstOrNull()?.take(80) ?: "Task"
            val initialConsole = TaskConsoleState(
                taskId = taskId,
                taskName = taskName,
                consoleLines = listOf(UiConsoleLine("🚀 Starting task: $taskName", ConsoleLineKind.INFO)),
                isExpanded = true
            )

            _state.value = _state.value.copy(
                isStreaming = true,
                streamingAssistantText = "",
                errorMessage = null,
                currentTaskName = taskName,
                activeTaskId = taskId,
                taskConsoles = _state.value.taskConsoles + (taskId to initialConsole)
            )

            val history = appCtx.chatRepository.getMessages(convId).filter { it.id != taskId }

            executor.run(
                history = history,
                userInput = text,
                config = providerCfg,
                settings = current,
                emit = { ev ->
                    val currentState = _state.value
                    val taskConsole = currentState.taskConsoles[taskId] ?: return@run

                    when (ev) {
                        is AgentExecutor.Event.AssistantDelta -> {
                            _state.value = currentState.copy(
                                streamingAssistantText = currentState.streamingAssistantText + ev.text
                            )
                        }
                        is AgentExecutor.Event.AssistantMessage -> {
                            appCtx.chatRepository.saveMessage(convId, ev.message)
                            _state.value = currentState.copy(streamingAssistantText = "")
                        }
                        is AgentExecutor.Event.ToolStart -> {
                            _state.value = currentState.copy(
                                taskConsoles = currentState.taskConsoles + (taskId to taskConsole.copy(
                                    toolInvocations = taskConsole.toolInvocations + ev.invocation,
                                    consoleLines = taskConsole.consoleLines + UiConsoleLine(
                                        "▶ ${ev.invocation.name} ${ev.invocation.arguments.take(120)}",
                                        ConsoleLineKind.TOOL
                                    )
                                ))
                            )
                        }
                        is AgentExecutor.Event.ToolFinish -> {
                            val updatedInvocations = taskConsole.toolInvocations.map {
                                if (it.name == ev.invocation.name && it.status == ToolStatus.RUNNING) ev.invocation else it
                            }
                            val statusLabel = if (ev.invocation.status == ToolStatus.SUCCESS) "✅" else "❌"
                            _state.value = currentState.copy(
                                taskConsoles = currentState.taskConsoles + (taskId to taskConsole.copy(
                                    toolInvocations = updatedInvocations,
                                    consoleLines = taskConsole.consoleLines + UiConsoleLine(
                                        "$statusLabel ${ev.invocation.name} — ${if (ev.invocation.status == ToolStatus.SUCCESS) "Success" else "Error"}: ${ev.invocation.result?.take(200) ?: ""}",
                                        if (ev.invocation.status == ToolStatus.SUCCESS) ConsoleLineKind.OUTPUT else ConsoleLineKind.ERROR
                                    )
                                ))
                            )
                        }
                        is AgentExecutor.Event.ConsoleLine -> {
                            val kind = when (ev.kind) {
                                AgentExecutor.ConsoleKind.INFO -> ConsoleLineKind.INFO
                                AgentExecutor.ConsoleKind.TOOL -> ConsoleLineKind.TOOL
                                AgentExecutor.ConsoleKind.OUTPUT -> ConsoleLineKind.OUTPUT
                                AgentExecutor.ConsoleKind.ERROR -> ConsoleLineKind.ERROR
                            }
                            _state.value = currentState.copy(
                                taskConsoles = currentState.taskConsoles + (taskId to taskConsole.copy(
                                    consoleLines = taskConsole.consoleLines + UiConsoleLine(ev.text, kind)
                                ))
                            )
                        }
                        is AgentExecutor.Event.Error -> {
                            _state.value = currentState.copy(
                                errorMessage = ev.message,
                                taskConsoles = currentState.taskConsoles + (taskId to taskConsole.copy(
                                    consoleLines = taskConsole.consoleLines + UiConsoleLine("❌ Error: ${ev.message}", ConsoleLineKind.ERROR),
                                    errorMessage = ev.message
                                ))
                            )
                        }
                        AgentExecutor.Event.Done -> {
                            _state.value = currentState.copy(
                                isStreaming = false,
                                currentTaskName = null,
                                activeTaskId = null,
                                taskConsoles = currentState.taskConsoles + (taskId to taskConsole.copy(
                                    isFinished = true,
                                    consoleLines = taskConsole.consoleLines + UiConsoleLine("✓ Task completed.", ConsoleLineKind.INFO)
                                ))
                            )
                        }
                    }
                }
            )
        }
    }
}