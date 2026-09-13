package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageStatus {
    QUEUED,
    SENDING,
    SENT_TO_SIM,
    DELIVERED,
    FAILED
}

@Entity(
    tableName = "recipients",
    indices = [Index(value = ["batchId"]), Index(value = ["phoneNumber"])]
)
data class RecipientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val campaignId: Long = 1,
    val batchId: Long = 1,
    val pingNumber: Int = 1,
    val phoneNumber: String,
    val displayName: String = "",
    val status: MessageStatus = MessageStatus.QUEUED,
    val statusMessage: String? = null,
    val sentAt: Long? = null,
    val deliveredAt: Long? = null,
    val partsCount: Int = 1
)
