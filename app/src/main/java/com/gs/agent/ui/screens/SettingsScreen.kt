package com.gs.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gs.agent.GsAgentApp
import com.gs.agent.data.models.AppSettings
import com.gs.agent.data.models.ProviderConfig
import com.gs.agent.data.models.ProviderPreset
import com.gs.agent.data.models.Providers
import com.gs.agent.ui.theme.AccentCyan
import com.gs.agent.ui.theme.PrimaryPurple
import com.gs.agent.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = GsAgentApp.instance
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var selectedProviderId by remember { mutableStateOf(settings.activeProviderId) }

    LaunchedEffect(settings.activeProviderId) {
        selectedProviderId = settings.activeProviderId
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Active provider")
            ProviderSelector(
                providers = Providers.ALL,
                active = settings.activeProviderId,
                onSelect = {
                    selectedProviderId = it
                    scope.launch {
                        app.settingsRepository.update { s -> s.copy(activeProviderId = it) }
                    }
                }
            )

            SectionTitle("Provider configuration")
            val currentPreset = Providers.byId(selectedProviderId)
            val currentConfig = settings.providers[selectedProviderId] ?: ProviderConfig(
                providerId = selectedProviderId,
                baseUrl = currentPreset.defaultBaseUrl,
                apiKey = "",
                selectedModel = currentPreset.suggestedModels.firstOrNull().orEmpty()
            )
            ProviderConfigCard(
                preset = currentPreset,
                config = currentConfig,
                onChange = { newCfg ->
                    scope.launch { app.settingsRepository.upsertProvider(newCfg) }
                }
            )

            SectionTitle("Agent behavior")
            AgentBehaviorCard(
                settings = settings,
                onChange = { transform -> scope.launch { app.settingsRepository.update(transform) } }
            )

            SectionTitle("System prompt")
            SystemPromptCard(
                value = settings.systemPrompt,
                onChange = { v -> scope.launch { app.settingsRepository.update { it.copy(systemPrompt = v) } } }
            )

            Spacer(Modifier.height(24.dp))
            Text("GS Agent v1.0.0", color = Color(0xFF6B6B7A), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title.uppercase(),
        color = AccentCyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun ProviderSelector(
    providers: List<ProviderPreset>,
    active: String,
    onSelect: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(4.dp)) {
            providers.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(p.id) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(8.dp).background(
                            if (active == p.id) PrimaryPurple else Color(0xFF3A3A4A),
                            RoundedCornerShape(4.dp)
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.displayName, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(p.defaultBaseUrl, color = Color(0xFF8B8B9A), fontSize = 11.sp)
                    }
                    if (active == p.id) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigCard(
    preset: ProviderPreset,
    config: ProviderConfig,
    onChange: (ProviderConfig) -> Unit
) {
    var apiKey by remember(config.providerId) { mutableStateOf(config.apiKey) }
    var baseUrl by remember(config.providerId) { mutableStateOf(config.baseUrl) }
    var model by remember(config.providerId) { mutableStateOf(config.selectedModel) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf("") }

    LaunchedEffect(apiKey, baseUrl, model, config.customModels) {
        onChange(config.copy(apiKey = apiKey, baseUrl = baseUrl, selectedModel = model))
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    apiKey = config.apiKey
                    baseUrl = preset.defaultBaseUrl
                    onChange(config.copy(baseUrl = preset.defaultBaseUrl))
                }) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset URL", fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text(preset.apiKeyHint) },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
                    }
                },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors()
            )

            // Model selector with custom support
            Box {
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model ID") },
                    leadingIcon = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { modelMenuOpen = true }) {
                            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors()
                )
                DropdownMenu(
                    expanded = modelMenuOpen,
                    onDismissRequest = { modelMenuOpen = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    (preset.suggestedModels + config.customModels).distinct().forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, fontSize = 13.sp) },
                            onClick = { model = m; modelMenuOpen = false }
                        )
                    }
                }
            }

            // Custom model add
            Text("Add custom model ID", color = Color(0xFF9999A8), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it },
                    placeholder = { Text("e.g. my-org/custom-model-v2") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = textFieldColors()
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = {
                        if (customModelInput.isNotBlank()) {
                            val list = (config.customModels + customModelInput.trim()).distinct()
                            onChange(config.copy(customModels = list, selectedModel = customModelInput.trim()))
                            model = customModelInput.trim()
                            customModelInput = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryPurple)
                ) { Icon(Icons.Outlined.Add, contentDescription = "Add") }
            }
            if (config.customModels.isNotEmpty()) {
                Column {
                    config.customModels.forEach { cm ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(cm, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                onChange(config.copy(customModels = config.customModels - cm))
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentBehaviorCard(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SwitchRow("Enable tools (file/shell access)", settings.enableTools) { v ->
                onChange { it.copy(enableTools = v) }
            }
            SwitchRow("Auto-approve destructive actions", settings.autoApproveTools) { v ->
                onChange { it.copy(autoApproveTools = v) }
            }
            SwitchRow("Stream responses", settings.streamResponses) { v ->
                onChange { it.copy(streamResponses = v) }
            }
            // Temperature
            Column {
                Row {
                    Text("Temperature", color = Color.White, modifier = Modifier.weight(1f))
                    Text("%.2f".format(settings.temperature), color = AccentCyan)
                }
                Slider(
                    value = settings.temperature,
                    onValueChange = { v -> onChange { it.copy(temperature = v) } },
                    valueRange = 0f..2f,
                    colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
                )
            }
            // Max tokens
            Column {
                Row {
                    Text("Max output tokens", color = Color.White, modifier = Modifier.weight(1f))
                    Text("${settings.maxTokens}", color = AccentCyan)
                }
                Slider(
                    value = settings.maxTokens.toFloat(),
                    onValueChange = { v -> onChange { it.copy(maxTokens = v.toInt()) } },
                    valueRange = 256f..16384f,
                    colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryPurple)
        )
    }
}

@Composable
private fun SystemPromptCard(value: String, onChange: (String) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = local,
                onValueChange = { local = it; onChange(it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp),
                colors = textFieldColors(),
                placeholder = { Text("Describe how the agent should behave…") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = PrimaryPurple,
    focusedBorderColor = PrimaryPurple,
    unfocusedBorderColor = Color(0xFF3A3A4A),
    focusedLabelColor = AccentCyan,
    unfocusedLabelColor = Color(0xFF9999A8),
    focusedLeadingIconColor = AccentCyan,
    unfocusedLeadingIconColor = Color(0xFF9999A8),
    focusedTrailingIconColor = AccentCyan,
    unfocusedTrailingIconColor = Color(0xFF9999A8)
)
