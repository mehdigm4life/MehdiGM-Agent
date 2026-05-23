package com.gs.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gs.agent.GsAgentApp
import com.gs.agent.ui.theme.PrimaryPurple
import com.gs.agent.ui.theme.AccentCyan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val app = GsAgentApp.instance
    val conversations by app.chatRepository.observeConversations().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).background(
                                Brush.linearGradient(listOf(PrimaryPurple, AccentCyan)),
                                RoundedCornerShape(8.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("GS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("GS Agent", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch {
                        val settings = app.settingsRepository.settingsFlow.first()
                        val activeCfg = settings.providers[settings.activeProviderId]
                        val convo = app.chatRepository.createConversation(
                            providerId = settings.activeProviderId,
                            model = activeCfg?.selectedModel.orEmpty()
                        )
                        onOpen(convo.id)
                    }
                },
                containerColor = PrimaryPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Chat", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversations, key = { it.id }) { c ->
                    ConversationCard(
                        title = c.title,
                        subtitle = "${c.providerId} · ${c.model}",
                        timestamp = c.updatedAt,
                        onClick = { onOpen(c.id) },
                        onDelete = { scope.launch { app.chatRepository.deleteConversation(c.id) } }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).background(
                Brush.linearGradient(listOf(PrimaryPurple.copy(alpha = 0.3f), AccentCyan.copy(alpha = 0.3f))),
                RoundedCornerShape(20.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Start a new conversation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text("Tap the New Chat button to begin working with your AI agent.", color = Color(0xFF9999A8), fontSize = 14.sp)
    }
}

@Composable
private fun ConversationCard(
    title: String,
    subtitle: String,
    timestamp: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(PrimaryPurple.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = PrimaryPurple) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("$subtitle • ${dateFmt.format(Date(timestamp))}", color = Color(0xFF9999A8), fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFF888896))
            }
        }
    }
}
