package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val pingNumber: Int = 1, // 1 = 1st Ping, 2 = 2nd Ping, etc.
    val parentBatchId: Long? = null, // Links to 1st ping batch if follow-up
    val messageTemplate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val totalCount: Int = 0,
    val deliveredCount: Int = 0,
    val failedCount: Int = 0,
    val isCompleted: Boolean = false
)
