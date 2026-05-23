package com.gs.agent.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gs.agent.data.models.AppSettings
import com.gs.agent.data.models.ProviderConfig
import com.gs.agent.data.models.Providers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "gs_agent_settings")

class SettingsRepository(private val context: Context) {
    private val key = stringPreferencesKey("settings_json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val raw = prefs[key]
        if (raw.isNullOrBlank()) defaults()
        else runCatching { json.decodeFromString<AppSettings>(raw) }.getOrElse { defaults() }
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: defaults()
            val newSettings = transform(current)
            prefs[key] = json.encodeToString(AppSettings.serializer(), newSettings)
        }
    }

    suspend fun upsertProvider(config: ProviderConfig) {
        update { s ->
            s.copy(providers = s.providers + (config.providerId to config))
        }
    }

    private fun defaults(): AppSettings {
        val initialProviders = Providers.ALL.associate { preset ->
            preset.id to ProviderConfig(
                providerId = preset.id,
                baseUrl = preset.defaultBaseUrl,
                apiKey = "",
                selectedModel = preset.suggestedModels.firstOrNull() ?: ""
            )
        }
        return AppSettings(providers = initialProviders)
    }
}
