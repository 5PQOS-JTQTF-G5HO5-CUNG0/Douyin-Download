package com.douyin.downloader.data.local

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {

    private const val PREF_NAME = "douyin_downloader_prefs"
    private const val KEY_SERVER_URL = "key_server_url"
    private const val KEY_MOCK_MODE = "key_mock_mode"

    private const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getServerUrl(): String {
        return if (::prefs.isInitialized) {
            prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        } else {
            DEFAULT_SERVER_URL
        }
    }

    fun setServerUrl(url: String) {
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_SERVER_URL, url).apply()
        }
    }

    fun isMockMode(): Boolean {
        return if (::prefs.isInitialized) {
            prefs.getBoolean(KEY_MOCK_MODE, false)
        } else {
            false
        }
    }

    fun setMockMode(enabled: Boolean) {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_MOCK_MODE, enabled).apply()
        }
    }
}
