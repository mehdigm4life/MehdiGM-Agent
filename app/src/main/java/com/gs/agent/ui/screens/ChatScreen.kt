package com.gs.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gs.agent.data.models.ChatMessage
import com.gs.agent.data.models.ConsoleLineKind
import com.gs.agent.data.models.Role
import com.gs.agent.data.models.TaskConsoleState
import com.gs.agent.data.models.ToolInvocation
import com.gs.agent.data.models.ToolStatus
import com.gs.agent.ui.ChatUiState
import com.gs.agent.ui.ChatViewModel
import com.gs.agent.ui.theme.AccentCyan
import com.gs.agent.ui.theme.ErrorRed
import com.gs.agent.ui.theme.PrimaryPurple
import com.gs.agent.ui.theme.SuccessGreen
import com.gs.agent.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(conversationId: String, onBack: () -> Unit, onSettings: () -> Unit) {
    val vm: ChatViewModel = viewModel()
    LaunchedEffect(conversationId) { vm.bindConversation(conversationId) }
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Auto-scroll to bottom
    LaunchedEffect(state.messages.size, state.streamingAssistantText) {
        val total = state.messages.size + (if (state.streamingAssistantText.isNotEmpty()) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GS Agent", fontWeight = FontWeight.Bold)
                        state.currentTaskName?.let {
                            Text(it, fontSize = 11.sp, color = AccentCyan, maxLines = 1)
                        } ?: run {
                            Text(if (state.isStreaming) "Working…" else "Ready", fontSize = 11.sp, color = Color(0xFF9999A8))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Messages list with per-task consoles
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.messages.isEmpty() && state.streamingAssistantText.isEmpty()) {
                    item { EmptyChatHint() }
                }

                // Group messages: user + assistant pairs get a console attached to the assistant
                val groupedMessages = buildMessageGroups(state)
                items(groupedMessages, key = { it.key }) { group ->
                    MessageGroup(
                        group = group,
                        state = state,
                        onToggleConsole = { taskId -> vm.toggleTaskConsole(taskId) }
                    )
                }

                // Streaming assistant text with its live console
                if (state.streamingAssistantText.isNotEmpty()) {
                    item {
                        val activeConsole = state.activeTaskId?.let { state.taskConsoles[it] }
                        StreamingMessage(
                            text = state.streamingAssistantText,
                            console = activeConsole,
                            isExpanded = activeConsole?.isExpanded ?: true,
                            onToggleConsole = {
                                state.activeTaskId?.let { vm.toggleTaskConsole(it) }
                            }
                        )
                    }
                }
            }

            // Global error
            state.errorMessage?.let { err ->
                Surface(
                    color = ErrorRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(err, color = ErrorRed, modifier = Modifier.padding(10.dp), fontSize = 12.sp)
                }
            }

            // Input bar
            InputBar(
                value = input,
                onChange = { input = it },
                isStreaming = state.isStreaming,
                onSend = {
                    val txt = input.trim()
                    if (txt.isNotEmpty()) {
                        vm.send(txt)
                        input = ""
                        focus.clearFocus()
                    }
                },
                onStop = { vm.stop() }
            )
        }
    }
}

// =========================================================================
// Grouping logic — each user message becomes a "task" with its own console
// =========================================================================

private data class MessageGroupData(
    val key: String,
    val userMessage: ChatMessage?,
    val assistantMessage: ChatMessage?,
    val taskConsole: TaskConsoleState?
)

private fun buildMessageGroups(state: ChatUiState): List<MessageGroupData> {
    val groups = mutableListOf<MessageGroupData>()
    var i = 0
    val msgs = state.messages
    while (i < msgs.size) {
        val msg = msgs[i]
        if (msg.role == Role.USER) {
            val nextAssistant = if (i + 1 < msgs.size && msgs[i + 1].role == Role.ASSISTANT) msgs[i + 1] else null
            val console = state.taskConsoles[msg.id]
            groups.add(MessageGroupData(
                key = msg.id,
                userMessage = msg,
                assistantMessage = nextAssistant,
                taskConsole = console
            ))
            if (nextAssistant != null) i += 2 else i += 1
        } else if (msg.role == Role.ASSISTANT) {
            groups.add(MessageGroupData(
                key = msg.id,
                userMessage = null,
                assistantMessage = msg,
                taskConsole = null
            ))
            i += 1
        } else {
            i += 1
        }
    }
    return groups
}

// =========================================================================
// Message Group Composable — user bubble + assistant bubble + per-task console
// =========================================================================

@Composable
private fun MessageGroup(
    group: MessageGroupData,
    state: ChatUiState,
    onToggleConsole: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // User message bubble
        group.userMessage?.let { userMsg ->
            MessageBubble(msg = userMsg, isUser = true)
        }

        // Assistant message bubble (if present)
        group.assistantMessage?.let { asstMsg ->
            MessageBubble(msg = asstMsg, isUser = false)
        }

        // Per-task console card — attached directly under the assistant message
        group.taskConsole?.let { console ->
            TaskConsoleCard(
                console = console,
                onToggle = { onToggleConsole(console.taskId) }
            )
        }
    }
}

// =========================================================================
// Streaming Message — live assistant text + its live console
// =========================================================================

@Composable
private fun StreamingMessage(
    text: String,
    console: TaskConsoleState?,
    isExpanded: Boolean,
    onToggleConsole: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Streaming assistant text bubble
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                Modifier.size(30.dp).background(
                    Brush.linearGradient(listOf(PrimaryPurple, AccentCyan)),
                    RoundedCornerShape(10.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = text + " ▍",
                        color = Color(0xFFE8E8F0),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Live console for the current streaming task
        console?.let {
            TaskConsoleCard(
                console = it,
                onToggle = onToggleConsole,
                isLive = true
            )
        }
    }
}

// =========================================================================
// Message Bubble — user or assistant
// =========================================================================

@Composable
private fun MessageBubble(msg: ChatMessage, isUser: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                Modifier.size(30.dp).background(
                    Brush.linearGradient(listOf(PrimaryPurple, AccentCyan)),
                    RoundedCornerShape(10.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            color = if (isUser) PrimaryPurple else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = msg.content,
                    color = if (isUser) Color.White else Color(0xFFE8E8F0),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(30.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF9999A8), modifier = Modifier.size(16.dp))
            }
        }
    }
}

// =========================================================================
// Per-Task Console Card — one per task, independently collapsible
// =========================================================================

@Composable
private fun TaskConsoleCard(
    console: TaskConsoleState,
    onToggle: () -> Unit,
    isLive: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isLive) 1.dp else 0.5.dp,
            color = if (isLive) AccentCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column {
            // ---- Header (always visible, clickable to toggle) ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                val (statusIcon, statusTint) = when {
                    isLive -> Icons.Outlined.HourglassTop to WarningOrange
                    console.errorMessage != null -> Icons.Outlined.Error to ErrorRed
                    console.isFinished -> Icons.Outlined.TaskAlt to SuccessGreen
                    console.toolInvocations.any { it.status == ToolStatus.RUNNING } -> Icons.Outlined.PlayArrow to AccentCyan
                    console.toolInvocations.isNotEmpty() -> Icons.Outlined.CheckCircle to SuccessGreen
                    else -> Icons.Outlined.Terminal to AccentCyan
                }
                Icon(statusIcon, contentDescription = null, tint = statusTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))

                // Task name
                Text(
                    console.taskName.take(30),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))

                // Stats
                Text(
                    "· ${console.toolInvocations.size} tools · ${console.consoleLines.size} lines",
                    color = Color(0xFF777788),
                    fontSize = 10.sp
                )

                Spacer(Modifier.weight(1f))

                // Status badge
                val badgeText = when {
                    isLive -> "RUNNING"
                    console.errorMessage != null -> "ERROR"
                    console.isFinished -> "DONE"
                    else -> "${console.toolInvocations.count { it.status == ToolStatus.SUCCESS }}/${console.toolInvocations.size}"
                }
                val badgeColor = when {
                    isLive -> WarningOrange.copy(alpha = 0.2f)
                    console.errorMessage != null -> ErrorRed.copy(alpha = 0.2f)
                    console.isFinished -> SuccessGreen.copy(alpha = 0.2f)
                    else -> Color(0xFF353548)
                }
                val badgeTextColor = when {
                    isLive -> WarningOrange
                    console.errorMessage != null -> ErrorRed
                    console.isFinished -> SuccessGreen
                    else -> Color(0xFFB0B0C0)
                }
                Surface(color = badgeColor, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        badgeText,
                        color = badgeTextColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Expand/collapse chevron
                Icon(
                    if (console.isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = null,
                    tint = Color(0xFF777788),
                    modifier = Modifier.size(18.dp)
                )
            }

            // ---- Tool chips row (always visible when invocations exist) ----
            if (console.toolInvocations.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 10.dp, end = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    console.toolInvocations.takeLast(10).forEach { inv ->
                        PerTaskToolChip(inv)
                    }
                }
            }

            // ---- Expandable log area ----
            AnimatedVisibility(
                visible = console.isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(Color(0xFF07070C))
                ) {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(console.consoleLines) { line ->
                            val color = when (line.kind) {
                                ConsoleLineKind.TOOL -> AccentCyan
                                ConsoleLineKind.OUTPUT -> SuccessGreen
                                ConsoleLineKind.ERROR -> ErrorRed
                                ConsoleLineKind.INFO -> Color(0xFFB0B0C0)
                            }
                            val prefix = when (line.kind) {
                                ConsoleLineKind.TOOL -> "⚡"
                                ConsoleLineKind.OUTPUT -> "→"
                                ConsoleLineKind.ERROR -> "✗"
                                ConsoleLineKind.INFO -> "·"
                            }
                            Text(
                                text = "$prefix ${line.text}",
                                color = color,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Tool Chip for per-task consoles
// =========================================================================

@Composable
private fun PerTaskToolChip(inv: ToolInvocation) {
    val (bg, fg) = when (inv.status) {
        ToolStatus.RUNNING -> WarningOrange.copy(alpha = 0.2f) to WarningOrange
        ToolStatus.SUCCESS -> SuccessGreen.copy(alpha = 0.18f) to SuccessGreen
        ToolStatus.ERROR -> ErrorRed.copy(alpha = 0.18f) to ErrorRed
        ToolStatus.PENDING -> Color(0xFF353548) to Color(0xFFB0B0C0)
        ToolStatus.BLOCKED -> Color(0xFF353548) to Color(0xFFB0B0C0)
    }
    Surface(color = bg, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Build, contentDescription = null, tint = fg, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
            Text(inv.name, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// =========================================================================
// Empty state hint
// =========================================================================

@Composable
private fun EmptyChatHint() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).background(
                Brush.linearGradient(listOf(PrimaryPurple.copy(alpha = 0.25f), AccentCyan.copy(alpha = 0.25f))),
                RoundedCornerShape(18.dp)
            ),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White) }
        Spacer(Modifier.height(12.dp))
        Text("Ask anything", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            "Try: \"List files in Download\", \"Create a Hello World project\", or \"What's my device info?\"",
            color = Color(0xFF8B8B9A),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )
    }
}

// =========================================================================
// Input bar
// =========================================================================

@Composable
private fun InputBar(
    value: String,
    onChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = Brush.linearGradient(listOf(PrimaryPurple, AccentCyan)),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text("Message GS Agent…", color = Color(0xFF666678), fontSize = 14.sp)
                    }
                    inner()
                }
            )
            IconButton(
                onClick = if (isStreaming) onStop else onSend,
                modifier = Modifier.size(40.dp).background(
                    Brush.linearGradient(listOf(PrimaryPurple, AccentCyan)),
                    RoundedCornerShape(20.dp)
                )
            ) {
                Icon(
                    if (isStreaming) Icons.Outlined.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}