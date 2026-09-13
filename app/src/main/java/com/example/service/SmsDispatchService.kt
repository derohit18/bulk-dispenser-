package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.BulkSmsApp
import com.example.MainActivity
import com.example.data.MessageStatus
import com.example.data.RecipientEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class LiveDispatchState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val activePhoneNumber: String = "",
    val activeIndex: Int = 0,
    val totalCount: Int = 0,
    val delayCountdownSeconds: Int = 0,
    val statusMessage: String = ""
)

class SmsDispatchService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var dispatchJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val isPaused = AtomicBoolean(false)
    private val isStopping = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BulkSms:DispatchWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> {
                isStopping.set(false)
                isPaused.set(false)
                startDispatchForeground()
            }
            ACTION_PAUSE -> {
                isPaused.set(true)
                _liveState.value = _liveState.value.copy(isPaused = true, statusMessage = "Dispatch paused")
                updateNotification("Dispatch Paused", "Tap to resume or open app")
            }
            ACTION_RESUME -> {
                isPaused.set(false)
                _liveState.value = _liveState.value.copy(isPaused = false, statusMessage = "Resuming dispatch...")
                updateNotification("Dispatch Resumed", "Sending queued messages...")
            }
            ACTION_STOP -> {
                stopDispatch()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDispatchForeground() {
        wakeLock?.acquire(60 * 60 * 1000L) // 1 hr max safeguard
        val initialNotification = buildNotification("Bulk SMS Engine", "Starting dispatch queue...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                BulkSmsApp.DISPATCH_NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(BulkSmsApp.DISPATCH_NOTIFICATION_ID, initialNotification)
        }

        dispatchJob?.cancel()
        dispatchJob = serviceScope.launch {
            executeDispatchLoop()
        }
    }

    private suspend fun executeDispatchLoop() {
        val app = applicationContext as BulkSmsApp
        val repository = app.repository
        val campaign = repository.getCampaign()
        val activeBatchId = campaign.activeBatchId
        val messageText = campaign.messageText.trim()
        val delayIntervalSeconds = campaign.delaySeconds.coerceAtLeast(1)

        repository.updateCampaignState(isRunning = true, isPaused = false)

        val queuedList = repository.getQueuedListByBatch(activeBatchId)
        val total = queuedList.size

        _liveState.value = LiveDispatchState(
            isRunning = true,
            isPaused = false,
            activeIndex = 0,
            totalCount = total,
            statusMessage = "Processing batch: $total messages"
        )

        if (total == 0 || messageText.isEmpty()) {
            _liveState.value = LiveDispatchState(
                isRunning = false,
                statusMessage = if (messageText.isEmpty()) "Empty message text" else "No queued recipients in batch"
            )
            repository.updateCampaignState(isRunning = false, isPaused = false)
            stopSelf()
            return
        }

        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        var processed = 0

        for (recipient in queuedList) {
            if (isStopping.get()) break

            // Handle paused state
            while (isPaused.get() && !isStopping.get()) {
                delay(500)
            }
            if (isStopping.get()) break

            processed++
            _liveState.value = _liveState.value.copy(
                activePhoneNumber = recipient.phoneNumber,
                activeIndex = processed,
                totalCount = total,
                statusMessage = "Sending to ${recipient.phoneNumber} ($processed/$total)"
            )

            updateNotification(
                "Sending $processed of $total",
                "Target: ${recipient.phoneNumber}"
            )

            // Mark as SENDING
            repository.markSent(recipient.id, MessageStatus.SENDING, "Dispatching via SIM...")

            // Send via SIM SmsManager
            dispatchSingleSms(smsManager, recipient, messageText)

            // Throttling countdown delay before next message
            if (processed < total && !isStopping.get()) {
                for (sec in delayIntervalSeconds downTo 1) {
                    if (isStopping.get()) break
                    while (isPaused.get() && !isStopping.get()) {
                        delay(500)
                    }
                    _liveState.value = _liveState.value.copy(delayCountdownSeconds = sec)
                    delay(1000)
                }
                _liveState.value = _liveState.value.copy(delayCountdownSeconds = 0)
            }
        }

        _liveState.value = _liveState.value.copy(
            isRunning = false,
            isPaused = false,
            delayCountdownSeconds = 0,
            statusMessage = "Dispatch complete ($processed messages sent)"
        )
        repository.updateCampaignState(isRunning = false, isPaused = false)
        repository.updateBatchStats(activeBatchId)

        updateNotification("Dispatch Complete", "All $processed messages processed")
        delay(2000)
        stopDispatch()
    }

    private fun dispatchSingleSms(smsManager: SmsManager, recipient: RecipientEntity, templateMessage: String) {
        try {
            // Support simple dynamic replacement if recipient has name: {name} -> name
            val finalMessage = if (recipient.displayName.isNotBlank()) {
                templateMessage.replace("{name}", recipient.displayName)
            } else {
                templateMessage.replace("{name}", "")
            }

            val sentPendingIntent = createPendingIntent(
                SmsStatusReceiver.ACTION_SMS_SENT,
                recipient.id,
                (recipient.id * 10).toInt()
            )
            val deliveryPendingIntent = createPendingIntent(
                SmsStatusReceiver.ACTION_SMS_DELIVERED,
                recipient.id,
                (recipient.id * 10 + 1).toInt()
            )

            val parts = smsManager.divideMessage(finalMessage)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()
                for (i in 0 until parts.size) {
                    // Send callback on the final part or each part
                    sentIntents.add(sentPendingIntent)
                    deliveryIntents.add(deliveryPendingIntent)
                }
                smsManager.sendMultipartTextMessage(
                    recipient.phoneNumber,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                smsManager.sendTextMessage(
                    recipient.phoneNumber,
                    null,
                    finalMessage,
                    sentPendingIntent,
                    deliveryPendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e("SmsDispatchService", "Failed to send SMS to ${recipient.phoneNumber}", e)
            val app = applicationContext as BulkSmsApp
            serviceScope.launch {
                app.repository.markSent(
                    recipient.id,
                    MessageStatus.FAILED,
                    "Exception: ${e.localizedMessage ?: "SIM send failure"}"
                )
            }
        }
    }

    private fun createPendingIntent(action: String, recipientId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(this, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(SmsStatusReceiver.EXTRA_RECIPIENT_ID, recipientId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(this, requestCode, intent, flags)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, BulkSmsApp.DISPATCH_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(BulkSmsApp.DISPATCH_NOTIFICATION_ID, buildNotification(title, content))
    }

    private fun stopDispatch() {
        isStopping.set(true)
        dispatchJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        _liveState.value = LiveDispatchState(isRunning = false, statusMessage = "Stopped")
        val app = applicationContext as? BulkSmsApp
        app?.let {
            serviceScope.launch {
                it.repository.updateCampaignState(isRunning = false, isPaused = false)
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopDispatch()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.bulksms.START"
        const val ACTION_PAUSE = "com.example.bulksms.PAUSE"
        const val ACTION_RESUME = "com.example.bulksms.RESUME"
        const val ACTION_STOP = "com.example.bulksms.STOP"

        private val _liveState = MutableStateFlow(LiveDispatchState())
        val liveState = _liveState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SmsDispatchService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, SmsDispatchService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, SmsDispatchService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmsDispatchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
