package com.gs.agent.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gs.agent.ui.theme.PrimaryPurple
import com.gs.agent.ui.theme.AccentCyan
import com.gs.agent.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val ctx = LocalContext.current
    var manageStorageGranted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    var notificationsGranted by remember { mutableStateOf(true) }

    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        manageStorageGranted = Environment.isExternalStorageManager()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }

    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF1A0F33), Color(0xFF0A0A0F)))
    )) {
        Column(
            Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Box(
                Modifier.size(96.dp)
                    .background(Brush.linearGradient(listOf(PrimaryPurple, AccentCyan)), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("GS", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(24.dp))
            Text("Welcome to GS Agent", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "A powerful on-device AI agent that can manage files, run commands, edit projects, and complete complex tasks autonomously.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0B0C0)
            )
            Spacer(Modifier.height(32.dp))

            PermissionCard(
                icon = Icons.Outlined.Folder,
                title = "All Files Access",
                description = "Required for the agent to read, write, and manage files across the device.",
                granted = manageStorageGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                        storageLauncher.launch(intent)
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                description = "Receive task progress notifications while the agent works in background.",
                granted = notificationsGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            InfoCard(
                icon = Icons.Outlined.Shield,
                title = "Privacy & Safety",
                description = "Your API keys are stored locally on your device. Conversations stay on your phone unless you choose to share them."
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141B))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp)
                    .background(PrimaryPurple.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryPurple)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(description, color = Color(0xFF9999A8), fontSize = 12.sp)
            }
            if (granted) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = SuccessGreen)
            } else {
                TextButton(onClick = onRequest) { Text("Grant", color = AccentCyan) }
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, description: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141B))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(AccentCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = AccentCyan) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(description, color = Color(0xFF9999A8), fontSize = 12.sp)
            }
        }
    }
}
