package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BatchEntity
import com.example.data.CampaignEntity
import com.example.data.MessageStatus
import com.example.data.RecipientEntity
import com.example.data.SmsRepository
import com.example.service.LiveDispatchState
import com.example.service.SmsDispatchService
import com.example.util.ContactParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class BulkSmsViewModel(private val repository: SmsRepository) : ViewModel() {

    private val _activeBatchId = MutableStateFlow<Long>(1)
    val activeBatchId: StateFlow<Long> = _activeBatchId.asStateFlow()

    val batches: StateFlow<List<BatchEntity>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipients: StateFlow<List<RecipientEntity>> = _activeBatchId
        .flatMapLatest { batchId -> repository.getRecipientsForBatchFlow(batchId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = _activeBatchId
        .flatMapLatest { batchId -> repository.getTotalCountForBatch(batchId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val queuedCount: StateFlow<Int> = _activeBatchId
        .flatMapLatest { batchId -> repository.getCountForStatusAndBatch(batchId, MessageStatus.QUEUED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sentCount: StateFlow<Int> = _activeBatchId
        .flatMapLatest { batchId -> repository.getCountForStatusAndBatch(batchId, MessageStatus.SENT_TO_SIM) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deliveredCount: StateFlow<Int> = _activeBatchId
        .flatMapLatest { batchId -> repository.getCountForStatusAndBatch(batchId, MessageStatus.DELIVERED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = _activeBatchId
        .flatMapLatest { batchId -> repository.getCountForStatusAndBatch(batchId, MessageStatus.FAILED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liveDispatchState: StateFlow<LiveDispatchState> = SmsDispatchService.liveState

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _delaySeconds = MutableStateFlow(3)
    val delaySeconds: StateFlow<Int> = _delaySeconds.asStateFlow()

    private val _filterOnlyNumbers = MutableStateFlow(true)
    val filterOnlyNumbers: StateFlow<Boolean> = _filterOnlyNumbers.asStateFlow()

    private val _deduplicate = MutableStateFlow(true)
    val deduplicate: StateFlow<Boolean> = _deduplicate.asStateFlow()

    private val _batchSplitSize = MutableStateFlow(50)
    val batchSplitSize: StateFlow<Int> = _batchSplitSize.asStateFlow()

    private val _importNotice = MutableStateFlow<String?>(null)
    val importNotice: StateFlow<String?> = _importNotice.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getOrCreateDefaultBatch()
            val initial = repository.getCampaign()
            _activeBatchId.value = initial.activeBatchId
            _messageText.value = initial.messageText
            _delaySeconds.value = initial.delaySeconds
            _filterOnlyNumbers.value = initial.filterOnlyNumbers
            _deduplicate.value = initial.deduplicate
            _batchSplitSize.value = initial.batchDistributionSize
        }
    }

    fun selectBatch(batchId: Long) {
        _activeBatchId.value = batchId
        viewModelScope.launch {
            val current = repository.getCampaign()
            repository.saveCampaign(current.copy(activeBatchId = batchId))
        }
    }

    fun onBatchSplitSizeChange(size: Int) {
        _batchSplitSize.value = size
        persistSettings()
    }

    fun createBatch(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createNewBatch(name = name.trim())
            selectBatch(newId)
            _importNotice.value = "Created and switched to '$name'."
        }
    }

    /**
     * Prepares a 2nd Ping / Follow-up batch from the currently active (or specified) batch.
     * Ready for another day's ping broadcast.
     */
    fun createSecondPing(sourceBatchId: Long, customFollowUpName: String? = null, customMessage: String? = null) {
        viewModelScope.launch {
            val batchesList = batches.value
            val source = batchesList.find { it.id == sourceBatchId }
            val nextPing = (source?.pingNumber ?: 1) + 1
            val batchName = customFollowUpName ?: "${source?.name ?: "Batch"} - ${nextPing}nd Ping"

            val newBatchId = repository.prepareFollowUpPingBatch(
                sourceBatchId = sourceBatchId,
                followUpName = batchName,
                newPingNumber = nextPing,
                customMessage = customMessage ?: _messageText.value
            )
            selectBatch(newBatchId)
            _importNotice.value = "Armed $batchName with ${source?.totalCount ?: 0} contacts for follow-up ping!"
        }
    }

    fun deleteBatch(batchId: Long) {
        viewModelScope.launch {
            repository.deleteBatch(batchId)
            val remaining = batches.value.filter { it.id != batchId }
            if (remaining.isNotEmpty()) {
                selectBatch(remaining.first().id)
            } else {
                val newBatch = repository.getOrCreateDefaultBatch()
                selectBatch(newBatch.id)
            }
            _importNotice.value = "Batch removed."
        }
    }

    fun onMessageChange(text: String) {
        _messageText.value = text
        persistSettings()
    }

    fun onDelayChange(seconds: Int) {
        _delaySeconds.value = seconds.coerceIn(1, 60)
        persistSettings()
    }

    fun onFilterOnlyNumbersChange(enabled: Boolean) {
        _filterOnlyNumbers.value = enabled
        persistSettings()
    }

    fun onDeduplicateChange(enabled: Boolean) {
        _deduplicate.value = enabled
        persistSettings()
    }

    fun clearNotice() {
        _importNotice.value = null
    }

    private fun persistSettings() {
        viewModelScope.launch {
            val current = repository.getCampaign()
            repository.saveCampaign(
                current.copy(
                    activeBatchId = _activeBatchId.value,
                    messageText = _messageText.value,
                    delaySeconds = _delaySeconds.value,
                    batchDistributionSize = _batchSplitSize.value,
                    filterOnlyNumbers = _filterOnlyNumbers.value,
                    deduplicate = _deduplicate.value,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun importRawNumbersText(rawText: String, splitIntoBatches: Boolean = false) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            _isImporting.value = true
            val parsed = withContext(Dispatchers.Default) {
                ContactParser.parseRawText(
                    text = rawText,
                    onlyNumbers = _filterOnlyNumbers.value,
                    deduplicate = _deduplicate.value
                )
            }
            if (parsed.isNotEmpty()) {
                if (splitIntoBatches && parsed.size > _batchSplitSize.value) {
                    // Split into multiple batches
                    val chunkSize = _batchSplitSize.value
                    val chunks = parsed.chunked(chunkSize)
                    var firstNewBatchId: Long? = null
                    val totalBatches = chunks.size
                    for ((index, chunk) in chunks.withIndex()) {
                        val batchNumber = index + 1
                        val bId = repository.createNewBatch("Batch $batchNumber (${chunk.size} nos)")
                        if (firstNewBatchId == null) firstNewBatchId = bId
                        val entities = chunk.map {
                            RecipientEntity(
                                batchId = bId,
                                phoneNumber = it.phoneNumber,
                                displayName = it.displayName,
                                status = MessageStatus.QUEUED
                            )
                        }
                        repository.addRecipients(entities)
                    }
                    if (firstNewBatchId != null) selectBatch(firstNewBatchId)
                    _importNotice.value = "Imported ${parsed.size} contacts distributed across $totalBatches batches ($chunkSize per batch)."
                } else {
                    val currBatchId = _activeBatchId.value
                    val entities = parsed.map {
                        RecipientEntity(
                            batchId = currBatchId,
                            phoneNumber = it.phoneNumber,
                            displayName = it.displayName,
                            status = MessageStatus.QUEUED
                        )
                    }
                    repository.addRecipients(entities)
                    _importNotice.value = "Imported ${entities.size} contacts into active batch."
                }
            } else {
                _importNotice.value = "No valid phone numbers detected in text."
            }
            _isImporting.value = false
        }
    }

    fun importFromFile(context: Context, uri: Uri, fileName: String, splitIntoBatches: Boolean = false) {
        viewModelScope.launch {
            _isImporting.value = true
            val parsed = withContext(Dispatchers.IO) {
                ContactParser.parseUri(
                    context = context,
                    uri = uri,
                    fileName = fileName,
                    onlyNumbers = _filterOnlyNumbers.value,
                    deduplicate = _deduplicate.value
                )
            }
            if (parsed.isNotEmpty()) {
                if (splitIntoBatches && parsed.size > _batchSplitSize.value) {
                    val chunkSize = _batchSplitSize.value
                    val chunks = parsed.chunked(chunkSize)
                    var firstNewBatchId: Long? = null
                    for ((index, chunk) in chunks.withIndex()) {
                        val bId = repository.createNewBatch("$fileName - Batch ${index + 1}")
                        if (firstNewBatchId == null) firstNewBatchId = bId
                        val entities = chunk.map {
                            RecipientEntity(
                                batchId = bId,
                                phoneNumber = it.phoneNumber,
                                displayName = it.displayName,
                                status = MessageStatus.QUEUED
                            )
                        }
                        repository.addRecipients(entities)
                    }
                    if (firstNewBatchId != null) selectBatch(firstNewBatchId)
                    _importNotice.value = "Imported ${parsed.size} contacts split into ${chunks.size} batches."
                } else {
                    val currBatchId = _activeBatchId.value
                    val entities = parsed.map {
                        RecipientEntity(
                            batchId = currBatchId,
                            phoneNumber = it.phoneNumber,
                            displayName = it.displayName,
                            status = MessageStatus.QUEUED
                        )
                    }
                    repository.addRecipients(entities)
                    _importNotice.value = "Imported ${entities.size} contacts from $fileName into active batch."
                }
            } else {
                _importNotice.value = "Could not extract phone numbers from $fileName"
            }
            _isImporting.value = false
        }
    }

    fun clearBatchRecipients() {
        viewModelScope.launch {
            repository.clearBatchRecipients(_activeBatchId.value)
            _importNotice.value = "Active batch contact list cleared."
        }
    }

    fun removeRecipient(id: Long) {
        viewModelScope.launch {
            repository.removeRecipient(id, _activeBatchId.value)
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            repository.resetFailed(_activeBatchId.value)
            _importNotice.value = "Failed contacts reset to queued in this batch."
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            repository.resetAll(_activeBatchId.value)
            _importNotice.value = "Batch contacts reset to queued status."
        }
    }

    fun startDispatch(context: Context) {
        persistSettings()
        SmsDispatchService.start(context)
    }

    fun pauseDispatch(context: Context) {
        SmsDispatchService.pause(context)
    }

    fun resumeDispatch(context: Context) {
        SmsDispatchService.resume(context)
    }

    fun stopDispatch(context: Context) {
        SmsDispatchService.stop(context)
    }
}

class BulkSmsViewModelFactory(private val repository: SmsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BulkSmsViewModel(repository) as T
    }
}
