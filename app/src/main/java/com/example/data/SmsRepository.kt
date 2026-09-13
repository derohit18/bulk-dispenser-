package com.example.data

import kotlinx.coroutines.flow.Flow

class SmsRepository(private val smsDao: SmsDao) {

    val allRecipients: Flow<List<RecipientEntity>> = smsDao.getAllRecipientsFlow()
    val allBatches: Flow<List<BatchEntity>> = smsDao.getAllBatchesFlow()
    val campaignSettings: Flow<CampaignEntity?> = smsDao.getCampaignSettingsFlow()
    val totalCount: Flow<Int> = smsDao.getTotalCountFlow()

    fun getRecipientsForBatchFlow(batchId: Long): Flow<List<RecipientEntity>> =
        smsDao.getRecipientsByBatchFlow(batchId)

    fun getTotalCountForBatch(batchId: Long): Flow<Int> =
        smsDao.getTotalCountByBatchFlow(batchId)

    fun getCountForStatus(status: MessageStatus): Flow<Int> = smsDao.getCountByStatusFlow(status)

    fun getCountForStatusAndBatch(batchId: Long, status: MessageStatus): Flow<Int> =
        smsDao.getCountByStatusAndBatchFlow(batchId, status)

    suspend fun getCampaign(): CampaignEntity {
        val existing = smsDao.getCampaignSettings()
        if (existing != null) return existing
        val defaultCampaign = CampaignEntity(id = 1, activeBatchId = 1)
        smsDao.saveCampaignSettings(defaultCampaign)
        return defaultCampaign
    }

    suspend fun saveCampaign(campaign: CampaignEntity) {
        smsDao.saveCampaignSettings(campaign)
    }

    suspend fun getOrCreateDefaultBatch(): BatchEntity {
        val batches = smsDao.getBatchById(1)
        if (batches != null) return batches
        val firstBatch = BatchEntity(
            id = 1,
            name = "Batch 1 - Initial List",
            pingNumber = 1,
            createdAt = System.currentTimeMillis()
        )
        val id = smsDao.insertBatch(firstBatch)
        return firstBatch.copy(id = id)
    }

    suspend fun createNewBatch(name: String, pingNumber: Int = 1, parentBatchId: Long? = null, template: String = ""): Long {
        val batch = BatchEntity(
            name = name,
            pingNumber = pingNumber,
            parentBatchId = parentBatchId,
            messageTemplate = template,
            createdAt = System.currentTimeMillis()
        )
        return smsDao.insertBatch(batch)
    }

    /**
     * Creates a 2nd Ping / Follow-up batch from a previously completed batch.
     * Copies all contacts (or optionally only delivered/successful contacts)
     * and sets status to QUEUED ready for a second broadcast on another day.
     */
    suspend fun prepareFollowUpPingBatch(
        sourceBatchId: Long,
        followUpName: String,
        newPingNumber: Int = 2,
        customMessage: String = ""
    ): Long {
        val sourceBatch = smsDao.getBatchById(sourceBatchId)
        val recipients = smsDao.getRecipientsForBatch(sourceBatchId)

        val newBatchId = smsDao.insertBatch(
            BatchEntity(
                name = followUpName,
                pingNumber = newPingNumber,
                parentBatchId = sourceBatchId,
                messageTemplate = customMessage.ifBlank { sourceBatch?.messageTemplate ?: "" },
                createdAt = System.currentTimeMillis(),
                totalCount = recipients.size
            )
        )

        val clonedRecipients = recipients.map { original ->
            RecipientEntity(
                batchId = newBatchId,
                pingNumber = newPingNumber,
                phoneNumber = original.phoneNumber,
                displayName = original.displayName,
                status = MessageStatus.QUEUED,
                statusMessage = null,
                sentAt = null,
                deliveredAt = null
            )
        }
        smsDao.insertRecipients(clonedRecipients)

        // Switch active batch in campaign settings
        val campaign = getCampaign()
        smsDao.saveCampaignSettings(
            campaign.copy(
                activeBatchId = newBatchId,
                messageText = if (customMessage.isNotBlank()) customMessage else campaign.messageText,
                updatedAt = System.currentTimeMillis()
            )
        )

        return newBatchId
    }

    suspend fun updateBatchStats(batchId: Long) {
        val recipients = smsDao.getRecipientsForBatch(batchId)
        val total = recipients.size
        val delivered = recipients.count { it.status == MessageStatus.DELIVERED }
        val sent = recipients.count { it.status == MessageStatus.SENT_TO_SIM }
        val failed = recipients.count { it.status == MessageStatus.FAILED }
        val queued = recipients.count { it.status == MessageStatus.QUEUED }
        val completed = total > 0 && queued == 0

        smsDao.updateBatchStats(
            batchId = batchId,
            sentAt = System.currentTimeMillis(),
            total = total,
            delivered = delivered + sent,
            failed = failed,
            completed = completed
        )
    }

    suspend fun deleteBatch(batchId: Long) {
        smsDao.clearRecipientsByBatch(batchId)
        smsDao.deleteBatch(batchId)
    }

    suspend fun updateCampaignState(isRunning: Boolean, isPaused: Boolean) {
        val current = getCampaign()
        smsDao.saveCampaignSettings(
            current.copy(
                isRunning = isRunning,
                isPaused = isPaused,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addRecipients(recipients: List<RecipientEntity>) {
        smsDao.insertRecipients(recipients)
        // Refresh batch count
        val firstBatchId = recipients.firstOrNull()?.batchId ?: 1
        updateBatchStats(firstBatchId)
    }

    suspend fun clearBatchRecipients(batchId: Long) {
        smsDao.clearRecipientsByBatch(batchId)
        updateBatchStats(batchId)
    }

    suspend fun clearAll() {
        smsDao.clearAllRecipients()
    }

    suspend fun removeRecipient(id: Long, batchId: Long) {
        smsDao.deleteRecipientById(id)
        updateBatchStats(batchId)
    }

    suspend fun resetFailed(batchId: Long) {
        smsDao.resetFailedToQueuedByBatch(batchId)
        updateBatchStats(batchId)
    }

    suspend fun resetAll(batchId: Long) {
        smsDao.resetAllToQueuedByBatch(batchId)
        updateBatchStats(batchId)
    }

    suspend fun getQueuedListByBatch(batchId: Long): List<RecipientEntity> {
        return smsDao.getQueuedRecipientsByBatch(batchId)
    }

    suspend fun markSent(id: Long, status: MessageStatus, note: String?) {
        smsDao.updateSentStatus(id, status, note, System.currentTimeMillis())
    }

    suspend fun markDelivered(id: Long, status: MessageStatus, note: String?) {
        smsDao.updateDeliveredStatus(id, status, note, System.currentTimeMillis())
    }
}
