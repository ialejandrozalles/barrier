package org.barrierfoss.androidclient.data

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): ConnectionConfig {
        return ConnectionConfig(
            serverHost = prefs.getString(KEY_HOST, ConnectionConfig.DEFAULT.serverHost).orEmpty(),
            serverPort = prefs.getInt(KEY_PORT, ConnectionConfig.DEFAULT.serverPort),
            screenName = prefs.getString(KEY_SCREEN_NAME, ConnectionConfig.DEFAULT.screenName).orEmpty(),
        )
    }

    fun save(config: ConnectionConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.serverHost)
            .putInt(KEY_PORT, config.serverPort)
            .putString(KEY_SCREEN_NAME, config.screenName)
            .apply()
    }

    private companion object {
        const val PREF_NAME = "barrier_android_client"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_SCREEN_NAME = "screen_name"
    }
}
