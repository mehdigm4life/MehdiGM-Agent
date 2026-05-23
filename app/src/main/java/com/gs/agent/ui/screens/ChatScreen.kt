package com.gs.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.filled.Send as SendIcon
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
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
import com.gs.agent.data.models.Role
import com.gs.agent.data.models.ToolInvocation
import com.gs.agent.data.models.ToolStatus
import com.gs.agent.ui.ChatViewModel
import com.gs.agent.ui.ConsoleLine
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
    var consoleVisible by remember { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

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
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.messages.isEmpty() && state.streamingAssistantText.isEmpty()) {
                    item { EmptyChatHint() }
                }
                items(state.messages, key = { it.id }) { msg -> MessageBubble(msg) }
                if (state.streamingAssistantText.isNotEmpty()) {
                    item {
                        MessageBubble(
                            ChatMessage(
                                id = "streaming",
                                role = Role.ASSISTANT,
                                content = state.streamingAssistantText
                            ),
                            isStreaming = true
                        )
                    }
                }
            }

            // Tools / Console panel
            if (state.toolInvocations.isNotEmpty() || state.consoleLines.isNotEmpty()) {
                ConsolePanel(
                    visible = consoleVisible,
                    onToggle = { consoleVisible = !consoleVisible },
                    invocations = state.toolInvocations,
                    lines = state.consoleLines,
                    onClear = vm::clearConsole
                )
            }

            // Error
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

@Composable
private fun MessageBubble(msg: ChatMessage, isStreaming: Boolean = false) {
    val isUser = msg.role == Role.USER
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
            ) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isUser) PrimaryPurple else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = msg.content + if (isStreaming) " ▍" else "",
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
            ) { Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF9999A8), modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun ConsolePanel(
    visible: Boolean,
    onToggle: () -> Unit,
    invocations: List<ToolInvocation>,
    lines: List<ConsoleLine>,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            // Header (always visible)
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Agent console",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(8.dp))
                Text("${invocations.size} tools · ${lines.size} lines",
                    color = Color(0xFF9999A8), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                if (visible) {
                    IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = Color(0xFF9999A8), modifier = Modifier.size(16.dp))
                    }
                }
                Icon(
                    if (visible) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = null, tint = Color(0xFF9999A8)
                )
            }

            // Tool chips bar (always visible if there are invocations)
            if (invocations.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    invocations.takeLast(8).forEach { inv ->
                        ToolChip(inv)
                    }
                }
            }

            // Expandable console content
            AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp).background(Color(0xFF07070C))
                ) {
                    LazyColumn(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(lines) { line ->
                            val color = when (line.kind) {
                                "tool" -> AccentCyan
                                "output" -> SuccessGreen
                                "error" -> ErrorRed
                                else -> Color(0xFFB0B0C0)
                            }
                            Text(
                                line.text,
                                color = color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(inv: ToolInvocation) {
    val (bg, fg) = when (inv.status) {
        ToolStatus.RUNNING -> WarningOrange.copy(alpha = 0.2f) to WarningOrange
        ToolStatus.SUCCESS -> SuccessGreen.copy(alpha = 0.18f) to SuccessGreen
        ToolStatus.ERROR -> ErrorRed.copy(alpha = 0.18f) to ErrorRed
        ToolStatus.PENDING -> Color(0xFF353548) to Color(0xFFB0B0C0)
        ToolStatus.BLOCKED -> Color(0xFF353548) to Color(0xFFB0B0C0)
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Build, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(inv.name, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

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
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                    if (isStreaming) Icons.Outlined.Stop else SendIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
