package com.gs.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gs.agent.data.models.ChatMessage
import com.gs.agent.data.models.ConsoleLineKind
import com.gs.agent.data.models.Role
import com.gs.agent.data.models.TaskConsoleState
import com.gs.agent.data.models.ToolInvocation
import com.gs.agent.data.models.ToolStatus
import com.gs.agent.data.models.UiConsoleLine
import com.gs.agent.ui.ChatViewModel
import com.gs.agent.ui.DisplayTaskGroup
import com.gs.agent.ui.buildDisplayGroups
import com.gs.agent.ui.theme.*

// ──────────────────────────────────────────────────────────────────────────
// Colors
// ──────────────────────────────────────────────────────────────────────────
private val UserBubble = PrimaryPurple
private val UserText = Color.White
private val AsstBubble = Color(0xFF1A1A26)
private val AsstText = Color(0xFFE8E8F0)
private val SurfaceBg = Color(0xFF14141B)
private val ConsoleBg = Color(0xFF0D0D14)
private val BorderDim = Color(0xFF2A2A38)

// ──────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(conversationId: String, onBack: () -> Unit, onSettings: () -> Unit) {
    val vm: ChatViewModel = viewModel()
    LaunchedEffect(conversationId) { vm.bindConversation(conversationId) }
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Build unified display groups (DB + live streaming merged into one list)
    val displayGroups = remember(state.messages, state.streamingAssistantText, state.taskConsoles, state.activeTaskId) {
        buildDisplayGroups(state.messages, state.streamingAssistantText, state.taskConsoles, state.activeTaskId)
    }

    // Auto-scroll to bottom when groups change or streaming text changes
    LaunchedEffect(displayGroups.size, state.streamingAssistantText) {
        if (displayGroups.isNotEmpty()) {
            listState.animateScrollToItem(displayGroups.size - 1)
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GS Agent", fontWeight = FontWeight.Bold)
                        state.currentTaskName?.let {
                            Text(it, fontSize = 11.sp, color = AccentCyan, maxLines = 1)
                        } ?: run {
                            Text(
                                if (state.isStreaming) "Working…" else "Ready",
                                fontSize = 11.sp,
                                color = Color(0xFF9999A8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Message list ──
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (displayGroups.isEmpty()) {
                    item { EmptyChatHint() }
                }

                items(displayGroups, key = { it.key }) { group ->
                    TaskGroupCard(
                        group = group,
                        onToggleConsole = { vm.toggleTaskConsole(it) }
                    )
                }
            }

            // ── Error banner ──
            state.errorMessage?.let { err ->
                Surface(
                    color = ErrorRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        err,
                        color = ErrorRed,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // ── Input bar ──
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

// ──────────────────────────────────────────────────────────────────────────
// Task Group Card — ONE item = USER + ASSISTANT + CONSOLE
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun TaskGroupCard(
    group: DisplayTaskGroup,
    onToggleConsole: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── User bubble (top-right) ──
        group.userMessage?.let { user ->
            UserBubble(user)
        }

        // ── Assistant content (avatar + bubble + console) ──
        val hasAssistantText = group.assistantMessage != null || group.streamingAssistantText.isNotEmpty()
        if (hasAssistantText || group.console != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                // Avatar column
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(32.dp)
                ) {
                    AgentAvatar()

                    // Connector line
                    if (group.console != null) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(20.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(AccentCyan.copy(alpha = 0.4f), AccentCyan.copy(alpha = 0.1f))
                                    ),
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Content column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Assistant message bubble
                    if (hasAssistantText) {
                        AssistantBubble(
                            text = group.assistantMessage?.content ?: "",
                            streamingText = group.streamingAssistantText,
                            isStreaming = group.isStreaming
                        )
                    }

                    // Per-task console
                    group.console?.let { console ->
                        TaskConsoleCard(
                            console = console,
                            onToggle = { onToggleConsole(console.taskId) },
                            isLive = group.isStreaming
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// User bubble (right-aligned, purple)
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun UserBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = UserBubble,
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = 20.dp, bottomEnd = 6.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = msg.content,
                    color = UserText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Tiny indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(y = 14.dp)
                .background(UserBubble.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Agent avatar
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
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
}

// ──────────────────────────────────────────────────────────────────────────
// Assistant message bubble (left-aligned, dark surface)
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun AssistantBubble(
    text: String,
    streamingText: String,
    isStreaming: Boolean
) {
    Surface(
        color = AsstBubble,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.widthIn(max = 360.dp),
        border = BorderStroke(0.5.dp, BorderDim.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Show final text when available, otherwise streaming text
            val displayText = if (text.isNotEmpty()) text else streamingText
            if (displayText.isNotEmpty()) {
                MarkdownText(
                    text = displayText + (if (isStreaming) " ▍" else ""),
                    color = AsstText
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Light markdown rendering for code blocks
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun MarkdownText(text: String, color: Color) {
    val segments = remember(text) { parseMarkdownSegments(text) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (segment in segments) {
            when (segment) {
                is MdSegment.Text -> {
                    Text(
                        text = segment.content,
                        color = color,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                is MdSegment.CodeBlock -> {
                    Surface(
                        color = Color(0xFF07070C),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = segment.content,
                            color = AccentCyan.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

private sealed class MdSegment {
    data class Text(val content: String) : MdSegment()
    data class CodeBlock(val content: String) : MdSegment()
}

private fun parseMarkdownSegments(text: String): List<MdSegment> {
    val out = mutableListOf<MdSegment>()
    val regex = Regex("```[a-zA-Z]*\\n([\\s\\S]*?)```", RegexOption.DOT_MATCHES_ALL)
    var lastEnd = 0
    for (match in regex.findAll(text)) {
        val before = text.substring(lastEnd, match.range.first)
        if (before.isNotBlank()) out.add(MdSegment.Text(before.trimEnd()))
        val code = match.groupValues[1]
        if (code.isNotBlank()) out.add(MdSegment.CodeBlock(code.trimEnd()))
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        val remaining = text.substring(lastEnd)
        if (remaining.isNotBlank()) out.add(MdSegment.Text(remaining.trimStart()))
    }
    return out
}

// ──────────────────────────────────────────────────────────────────────────
// Per-task Console Card — independently collapsible
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun TaskConsoleCard(
    console: TaskConsoleState,
    onToggle: () -> Unit,
    isLive: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ConsoleBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isLive) 1.5.dp else 0.5.dp,
            color = if (isLive) AccentCyan.copy(alpha = 0.35f) else BorderDim.copy(alpha = 0.3f)
        )
    ) {
        Column {
            // ── Header ──
            HeaderRow(console = console, isLive = isLive, onToggle = onToggle)

            // ── Tool chips ──
            if (console.toolInvocations.isNotEmpty()) {
                ToolChipsRow(console.toolInvocations)
            }

            // ── Expandable log ──
            AnimatedVisibility(
                visible = console.isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                ConsoleLog(console.consoleLines)
            }
        }
    }
}

@Composable
private fun HeaderRow(console: TaskConsoleState, isLive: Boolean, onToggle: () -> Unit) {
    // Pulse animation for live indicator
    val animatedAlpha = if (isLive) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        alpha
    } else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        val statusIcon: ImageVector
        val statusTint: Color

        when {
            isLive -> { statusIcon = Icons.Outlined.HourglassTop; statusTint = WarningOrange }
            console.errorMessage != null -> { statusIcon = Icons.Outlined.Error; statusTint = ErrorRed }
            console.isFinished -> { statusIcon = Icons.Outlined.TaskAlt; statusTint = SuccessGreen }
            console.toolInvocations.any { it.status == ToolStatus.RUNNING } -> { statusIcon = Icons.Outlined.PlayArrow; statusTint = AccentCyan }
            console.toolInvocations.isNotEmpty() -> { statusIcon = Icons.Outlined.CheckCircle; statusTint = SuccessGreen }
            else -> { statusIcon = Icons.Outlined.Terminal; statusTint = AccentCyan }
        }

        Icon(
            statusIcon, contentDescription = null, tint = statusTint,
            modifier = Modifier.size(18.dp).alpha(animatedAlpha)
        )
        Spacer(Modifier.width(8.dp))

        // Task name
        Text(
            text = console.taskName.take(30),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(Modifier.width(6.dp))
        Text("•", color = Color(0xFF555568), fontSize = 10.sp)
        Spacer(Modifier.width(6.dp))

        // Quick stats
        Text("${console.toolInvocations.size} tools", color = Color(0xFF8888A0), fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))

        // Badge
        Badge(isLive = isLive, console = console)

        Spacer(Modifier.width(4.dp))

        // Expand/collapse
        Icon(
            if (console.isExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
            contentDescription = null,
            tint = Color(0xFF666680),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Badge(isLive: Boolean, console: TaskConsoleState) {
    val (text, bg, fg) = when {
        isLive -> "RUNNING" to WarningOrange.copy(alpha = 0.15f) to WarningOrange
        console.errorMessage != null -> "ERROR" to ErrorRed.copy(alpha = 0.15f) to ErrorRed
        console.isFinished -> "DONE" to SuccessGreen.copy(alpha = 0.15f) to SuccessGreen
        else -> {
            val done = console.toolInvocations.count { it.status == ToolStatus.SUCCESS }
            val total = console.toolInvocations.size
            "$done/$total" to Color(0xFF2A2A38) to Color(0xFFB0B0C0)
        }
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text, color = fg,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ToolChipsRow(invocations: List<ToolInvocation>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        invocations.takeLast(12).forEach { inv -> ToolChip(inv) }
    }
}

@Composable
private fun ToolChip(inv: ToolInvocation) {
    val (bg, fg) = when (inv.status) {
        ToolStatus.RUNNING -> WarningOrange.copy(alpha = 0.18f) to WarningOrange
        ToolStatus.SUCCESS -> SuccessGreen.copy(alpha = 0.15f) to SuccessGreen
        ToolStatus.ERROR -> ErrorRed.copy(alpha = 0.15f) to ErrorRed
        ToolStatus.PENDING -> Color(0xFF2A2A38) to Color(0xFF909098)
        ToolStatus.BLOCKED -> Color(0xFF2A2A38) to Color(0xFF909098)
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Build, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(inv.name, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ConsoleLog(lines: List<UiConsoleLine>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .background(Color(0xFF06060A))
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "  Waiting for tool calls…",
                color = Color(0xFF666680),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(lines) { line -> ConsoleLineRow(logLine = line) }
            }
        }
    }
}

@Composable
private fun ConsoleLineRow(logLine: UiConsoleLine) {
    val color = when (logLine.kind) {
        ConsoleLineKind.TOOL -> AccentCyan
        ConsoleLineKind.OUTPUT -> SuccessGreen
        ConsoleLineKind.ERROR -> ErrorRed
        ConsoleLineKind.INFO -> Color(0xFFB0B0C0)
    }
    val prefix = when (logLine.kind) {
        ConsoleLineKind.TOOL -> "⚡"
        ConsoleLineKind.OUTPUT -> "→"
        ConsoleLineKind.ERROR -> "✗"
        ConsoleLineKind.INFO -> "·"
    }
    Text(
        text = "$prefix ${logLine.text}",
        color = color,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 14.sp
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Empty state
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyChatHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Brush.linearGradient(
                        listOf(PrimaryPurple.copy(alpha = 0.2f), AccentCyan.copy(alpha = 0.2f))
                    ),
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("What can I help you with?", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Try asking me to list files, create a project,\nrun a shell command, or get device info.",
            color = Color(0xFF8B8B9A),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Input bar
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    value: String,
    onChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        color = SurfaceBg,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, BorderDim)
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
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
                        Text("Ask GS Agent anything…", color = Color(0xFF555568), fontSize = 14.sp)
                    }
                    inner()
                }
            )

            Spacer(Modifier.width(8.dp))

            FilledIconButton(
                onClick = if (isStreaming) onStop else onSend,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isStreaming) ErrorRed.copy(alpha = 0.2f) else PrimaryPurple
                )
            ) {
                Icon(
                    if (isStreaming) Icons.Outlined.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if (isStreaming) ErrorRed else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}