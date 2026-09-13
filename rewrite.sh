#!/bin/bash
cat << 'INNER_EOF' > app/src/main/java/com/example/ui/BulkSmsScreen.kt
package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.RecipientEntity
import com.example.ui.components.BatchSelectorSection
import com.example.ui.components.ComposerSection
import com.example.ui.components.ContactIngestSection
import com.example.ui.components.DeliveryReportFeed
import com.example.ui.components.TelemetryHud
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.StatusDeliveredBg
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusFailedBg
import com.example.ui.theme.TelecomCobalt
import com.example.ui.theme.TelecomCobaltDark
import com.example.ui.theme.TelecomCobaltLight
import com.example.ui.theme.TelemetryMonospace
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class AppTab(val label: String, val icon: ImageVector) {
    Dispatch("Dispatch", Icons.Default.Send),
    Audience("Audience", Icons.Default.Groups),
    Message("Message", Icons.Default.Edit)
}

@Composable
fun BulkSmsScreen(
    viewModel: BulkSmsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val activeBatchId by viewModel.activeBatchId.collectAsStateWithLifecycle()
    val recipients by viewModel.recipients.collectAsStateWithLifecycle()

    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val queuedCount by viewModel.queuedCount.collectAsStateWithLifecycle()
    val sentCount by viewModel.sentCount.collectAsStateWithLifecycle()
    val deliveredCount by viewModel.deliveredCount.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedCount.collectAsStateWithLifecycle()

    val batchSplitSize by viewModel.batchSplitSize.collectAsStateWithLifecycle()

    val liveDispatchState by viewModel.liveDispatchState.collectAsStateWithLifecycle()

    val messageText by viewModel.messageText.collectAsStateWithLifecycle()
    val delaySeconds by viewModel.delaySeconds.collectAsStateWithLifecycle()
    val filterOnlyNumbers by viewModel.filterOnlyNumbers.collectAsStateWithLifecycle()
    val deduplicate by viewModel.deduplicate.collectAsStateWithLifecycle()
    val importNotice by viewModel.importNotice.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(AppTab.Dispatch) }

    // Permissions check
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermission = permissions[Manifest.permission.SEND_SMS] == true
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = CardSurface,
                tonalElevation = 8.dp
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) },
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TelecomCobalt,
                            selectedTextColor = TelecomCobalt,
                            indicatorColor = TelecomCobaltLight
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Persistent App Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CanvasBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TelecomCobalt),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = CardSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Bulk SMS Dispatcher",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Native SIM Broadcast Engine · Live Status",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (totalCount > 0) {
                        IconButton(
                            onClick = { viewModel.resetAll() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset All Statuses",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Permission Warning Banner if SEND_SMS not granted
                if (!hasSmsPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusFailedBg)
                            .border(1.dp, StatusFailed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = StatusFailed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "SMS Permission Required",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusFailed
                                    )
                                    Text(
                                        text = "Required to broadcast SMS via your device's SIM card.",
                                        fontSize = 11.sp,
                                        color = TextPrimary
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val perms = mutableListOf(Manifest.permission.SEND_SMS)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionLauncher.launch(perms.toTypedArray())
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusFailed),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("grant_sms_permission_button")
                            ) {
                                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Notice Banner
                AnimatedVisibility(visible = importNotice != null) {
                    importNotice?.let { notice ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TelecomCobalt.copy(alpha = 0.08f))
                                .border(1.dp, TelecomCobalt.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = TelecomCobalt,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = notice, fontSize = 12.sp, color = TextPrimary)
                                }
                                IconButton(
                                    onClick = { viewModel.clearNotice() },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scrollable Tab Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    AppTab.Dispatch -> {
                        TelemetryHud(
                            liveState = liveDispatchState,
                            totalCount = totalCount,
                            queuedCount = queuedCount,
                            sentCount = sentCount,
                            deliveredCount = deliveredCount,
                            failedCount = failedCount,
                            hasSmsPermission = hasSmsPermission,
                            onStartDispatch = {
                                if (hasSmsPermission) {
                                    viewModel.startDispatch(context)
                                } else {
                                    val perms = mutableListOf(Manifest.permission.SEND_SMS)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionLauncher.launch(perms.toTypedArray())
                                }
                            },
                            onPauseDispatch = { viewModel.pauseDispatch(context) },
                            onResumeDispatch = { viewModel.resumeDispatch(context) },
                            onStopDispatch = { viewModel.stopDispatch(context) },
                            onRetryFailed = { viewModel.retryFailed() }
                        )

                        DeliveryReportFeed(
                            recipients = recipients,
                            onRemoveRecipient = { viewModel.removeRecipient(it) }
                        )
                    }

                    AppTab.Audience -> {
                        BatchSelectorSection(
                            batches = batches,
                            activeBatchId = activeBatchId,
                            onSelectBatch = { viewModel.selectBatch(it) },
                            onCreateBatch = { viewModel.createBatch(it) },
                            onDeleteBatch = { viewModel.deleteBatch(it) },
                            onPrepareSecondPing = { srcId, name, customMsg ->
                                viewModel.createSecondPing(sourceBatchId = srcId, customFollowUpName = name, customMessage = customMsg)
                            },
                            onImportCsv = { uri, fileName ->
                                viewModel.importFromFile(context, uri, fileName, false)
                            },
                            batchSplitSize = batchSplitSize,
                            onBatchSplitSizeChange = { viewModel.onBatchSplitSizeChange(it) }
                        )

                        ContactIngestSection(
                            filterOnlyNumbers = filterOnlyNumbers,
                            onFilterOnlyNumbersChange = { viewModel.onFilterOnlyNumbersChange(it) },
                            deduplicate = deduplicate,
                            onDeduplicateChange = { viewModel.onDeduplicateChange(it) },
                            isImporting = isImporting,
                            onImportRawText = { text, splitBatches -> viewModel.importRawNumbersText(text, splitBatches) },
                            onImportFile = { uri, fileName, splitBatches -> viewModel.importFromFile(context, uri, fileName, splitBatches) },
                            onClearAll = { viewModel.clearBatchRecipients() },
                            onLoadSampleData = {
                                val sample = """
                                    +1-800-555-0142, Sarah Miller
                                    +1-800-555-0188, John Davis
                                    (555) 234-5678, Robert Wilson
                                    +44 7911 123456, Emma Taylor
                                    9876543210, Michael Brown
                                """.trimIndent()
                                viewModel.importRawNumbersText(sample, false)
                                if (messageText.isBlank()) {
                                    viewModel.onMessageChange("Hello {name}, your scheduled service appointment is confirmed for tomorrow.")
                                }
                            },
                            totalContacts = totalCount,
                            batchSplitSize = batchSplitSize
                        )
                    }

                    AppTab.Message -> {
                        ComposerSection(
                            messageText = messageText,
                            onMessageChange = { viewModel.onMessageChange(it) },
                            delaySeconds = delaySeconds,
                            onDelayChange = { viewModel.onDelayChange(it) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
INNER_EOF
