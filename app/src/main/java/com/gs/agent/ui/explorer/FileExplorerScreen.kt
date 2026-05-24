package com.gs.agent.ui.explorer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gs.agent.ui.theme.PrimaryPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(startPath: String = "/storage/emulated/0", onBack: () -> Unit) {
    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Request storage permission once
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }

    if (!hasPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Waiting for storage permission...", color = Color.White)
        }
        return
    }

    val vm: FileExplorerViewModel = viewModel()
    // Load the initial directory when the composable enters composition
    LaunchedEffect(key1 = startPath) { vm.loadDirectory(startPath) }
    val rootNode by vm.root.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Explorer – ${startPath.substringAfterLast('/')}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PrimaryPurple)
            )
        }
    ) { padding ->
        rootNode?.let { node ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF111111))
            ) {
                items(node.children) { child ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (child.isDirectory) vm.loadDirectory(child.file.absolutePath) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (child.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                            contentDescription = null,
                            tint = if (child.isDirectory) PrimaryPurple else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(child.file.name, color = Color.White, fontSize = 14.sp)
                    }
                    Divider(color = Color(0x33FFFFFF))
                }
            }
        } ?: run {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryPurple)
            }
        }
    }
}
