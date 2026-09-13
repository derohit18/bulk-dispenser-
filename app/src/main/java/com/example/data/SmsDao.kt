package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM recipients ORDER BY id ASC")
    fun getAllRecipientsFlow(): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE batchId = :batchId ORDER BY id ASC")
    fun getRecipientsByBatchFlow(batchId: Long): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE batchId = :batchId AND status = 'QUEUED' ORDER BY id ASC")
    suspend fun getQueuedRecipientsByBatch(batchId: Long): List<RecipientEntity>

    @Query("SELECT * FROM recipients WHERE status = 'QUEUED' ORDER BY id ASC")
    suspend fun getQueuedRecipients(): List<RecipientEntity>

    @Query("SELECT * FROM recipients WHERE id = :id LIMIT 1")
    suspend fun getRecipientById(id: Long): RecipientEntity?

    @Query("SELECT * FROM recipients WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getRecipientByPhone(phoneNumber: String): RecipientEntity?

    @Query("SELECT * FROM recipients WHERE batchId = :batchId")
    suspend fun getRecipientsForBatch(batchId: Long): List<RecipientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipients(recipients: List<RecipientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipient(recipient: RecipientEntity): Long

    @Update
    suspend fun updateRecipient(recipient: RecipientEntity)

    @Query("UPDATE recipients SET status = :status, statusMessage = :message, sentAt = :timestamp WHERE id = :id")
    suspend fun updateSentStatus(id: Long, status: MessageStatus, message: String?, timestamp: Long)

    @Query("UPDATE recipients SET status = :status, statusMessage = :message, deliveredAt = :timestamp WHERE id = :id")
    suspend fun updateDeliveredStatus(id: Long, status: MessageStatus, message: String?, timestamp: Long)

    @Query("UPDATE recipients SET status = 'QUEUED', statusMessage = NULL, sentAt = NULL, deliveredAt = NULL WHERE batchId = :batchId AND status = 'FAILED'")
    suspend fun resetFailedToQueuedByBatch(batchId: Long)

    @Query("UPDATE recipients SET status = 'QUEUED', statusMessage = NULL, sentAt = NULL, deliveredAt = NULL WHERE status = 'FAILED'")
    suspend fun resetFailedToQueued()

    @Query("UPDATE recipients SET status = 'QUEUED', statusMessage = NULL, sentAt = NULL, deliveredAt = NULL WHERE batchId = :batchId")
    suspend fun resetAllToQueuedByBatch(batchId: Long)

    @Query("UPDATE recipients SET status = 'QUEUED', statusMessage = NULL, sentAt = NULL, deliveredAt = NULL")
    suspend fun resetAllToQueued()

    @Query("DELETE FROM recipients WHERE batchId = :batchId")
    suspend fun clearRecipientsByBatch(batchId: Long)

    @Query("DELETE FROM recipients")
    suspend fun clearAllRecipients()

    @Query("DELETE FROM recipients WHERE id = :id")
    suspend fun deleteRecipientById(id: Long)

    @Query("SELECT COUNT(*) FROM recipients WHERE batchId = :batchId")
    fun getTotalCountByBatchFlow(batchId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipients WHERE batchId = :batchId AND status = :status")
    fun getCountByStatusAndBatchFlow(batchId: Long, status: MessageStatus): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipients")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipients WHERE status = :status")
    fun getCountByStatusFlow(status: MessageStatus): Flow<Int>

    // Batch management queries
    @Query("SELECT * FROM batches ORDER BY createdAt DESC")
    fun getAllBatchesFlow(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE id = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: Long): BatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: BatchEntity): Long

    @Update
    suspend fun updateBatch(batch: BatchEntity)

    @Query("DELETE FROM batches WHERE id = :batchId")
    suspend fun deleteBatch(batchId: Long)

    @Query("UPDATE batches SET sentAt = :sentAt, totalCount = :total, deliveredCount = :delivered, failedCount = :failed, isCompleted = :completed WHERE id = :batchId")
    suspend fun updateBatchStats(batchId: Long, sentAt: Long, total: Int, delivered: Int, failed: Int, completed: Boolean)

    @Query("SELECT * FROM campaign_settings WHERE id = 1 LIMIT 1")
    fun getCampaignSettingsFlow(): Flow<CampaignEntity?>

    @Query("SELECT * FROM campaign_settings WHERE id = 1 LIMIT 1")
    suspend fun getCampaignSettings(): CampaignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCampaignSettings(campaign: CampaignEntity)
}
