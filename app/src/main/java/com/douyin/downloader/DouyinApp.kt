package com.douyin.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DouyinApp : Application() {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "douyin_download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        com.douyin.downloader.data.local.AppPreferences.init(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_download_name)
            val descriptionText = getString(R.string.channel_download_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(DOWNLOAD_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
