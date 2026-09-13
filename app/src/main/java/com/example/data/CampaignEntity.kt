package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaign_settings")
data class CampaignEntity(
    @PrimaryKey
    val id: Long = 1,
    val activeBatchId: Long = 1,
    val messageText: String = "",
    val delaySeconds: Int = 3,
    val batchDistributionSize: Int = 50, // Batch split size (e.g. 50 / 100 / 250 / all)
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val filterOnlyNumbers: Boolean = true,
    val deduplicate: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
