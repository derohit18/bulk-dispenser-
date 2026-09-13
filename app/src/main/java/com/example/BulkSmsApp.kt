package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.SmsRepository

class BulkSmsApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: SmsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = SmsRepository(database.smsDao())

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DISPATCH_CHANNEL_ID,
                "Bulk SMS Dispatcher Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live bulk SMS queue dispatch progress and carrier delivery status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val DISPATCH_CHANNEL_ID = "bulk_sms_dispatch_channel"
        const val DISPATCH_NOTIFICATION_ID = 2001
    }
}
