package com.gs.agent

import android.app.Application
import com.gs.agent.data.repository.SettingsRepository
import com.gs.agent.data.repository.ChatRepository
import com.gs.agent.data.db.AppDatabase

class GsAgentApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val chatRepository by lazy { ChatRepository(database.chatDao()) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: GsAgentApp
            private set
    }
}
