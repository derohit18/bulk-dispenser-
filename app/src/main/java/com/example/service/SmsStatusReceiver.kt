package com.example.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import com.example.BulkSmsApp
import com.example.data.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val recipientId = intent.getLongExtra(EXTRA_RECIPIENT_ID, -1L)
        if (recipientId == -1L) return

        val app = context.applicationContext as? BulkSmsApp ?: return
        val repository = app.repository
        val resultCode = resultCode

        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                ACTION_SMS_SENT -> {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            Log.d("SmsStatusReceiver", "Recipient $recipientId sent OK to SIM")
                            repository.markSent(
                                id = recipientId,
                                status = MessageStatus.SENT_TO_SIM,
                                note = "Handed off to SIM carrier network"
                            )
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            repository.markSent(
                                id = recipientId,
                                status = MessageStatus.FAILED,
                                note = "SIM Error: No cellular service"
                            )
                        }
                        SmsManager.RESULT_ERROR_RADIO_OFF -> {
                            repository.markSent(
                                id = recipientId,
                                status = MessageStatus.FAILED,
                                note = "SIM Error: Airplane mode / Radio off"
                            )
                        }
                        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> {
                            repository.markSent(
                                id = recipientId,
                                status = MessageStatus.FAILED,
                                note = "SIM Error: Daily/hourly SMS limit exceeded"
                            )
                        }
                        else -> {
                            repository.markSent(
                                id = recipientId,
                                status = MessageStatus.FAILED,
                                note = "Transmission error (Code: $resultCode)"
                            )
                        }
                    }
                }
                ACTION_SMS_DELIVERED -> {
                    if (resultCode == Activity.RESULT_OK) {
                        Log.d("SmsStatusReceiver", "Recipient $recipientId DELIVERED ACK received")
                        repository.markDelivered(
                            id = recipientId,
                            status = MessageStatus.DELIVERED,
                            note = "Carrier ACK: Delivered to handset"
                        )
                    } else {
                        repository.markDelivered(
                            id = recipientId,
                            status = MessageStatus.FAILED,
                            note = "Carrier report: Delivery failed or rejected (Code: $resultCode)"
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.example.bulksms.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.bulksms.SMS_DELIVERED"
        const val EXTRA_RECIPIENT_ID = "extra_recipient_id"
    }
}
