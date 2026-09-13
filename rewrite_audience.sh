#!/bin/bash

# BatchSelectorSection.kt
cat << 'INNER' > app/src/main/java/com/example/ui/components/BatchSelectorSection.kt
package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BatchEntity
import com.example.ui.theme.*

@Composable
fun BatchSelectorSection(
    batches: List<BatchEntity>,
    activeBatchId: Long,
    onSelectBatch: (Long) -> Unit,
    onCreateBatch: (String) -> Unit,
    onDeleteBatch: (Long) -> Unit,
    onPrepareSecondPing: (Long, String, String?) -> Unit,
    onImportCsv: (Uri, String) -> Unit,
    batchSplitSize: Int,
    onBatchSplitSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewBatchDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Audiences", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Manage contact batches", fontSize = 13.sp, color = TextSecondary)
            }
            
            FilledTonalButton(
                onClick = { showNewBatchDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = TelecomCobaltLight, contentColor = TelecomCobaltDark)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (batches.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No audiences created yet.", color = TextMuted)
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                items(batches) { batch ->
                    val isActive = batch.id == activeBatchId
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isActive) TelecomCobalt else CardSurfaceVariant)
                            .clickable { onSelectBatch(batch.id) }
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Icon(
                                    Icons.Rounded.Groups, 
                                    contentDescription = null, 
                                    tint = if (isActive) CardSurface else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                if (!isActive && batch.id != 1L) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Delete",
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp).clickable { onDeleteBatch(batch.id) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                batch.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) CardSurface else TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                "ID: ${batch.id}",
                                fontSize = 12.sp,
                                color = if (isActive) TelecomCobaltLight else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewBatchDialog) {
        var newBatchName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewBatchDialog = false },
            title = { Text("Create Audience", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newBatchName,
                    onValueChange = { newBatchName = it },
                    placeholder = { Text("e.g. VIP Customers") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newBatchName.isNotBlank()) onCreateBatch(newBatchName)
                    showNewBatchDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewBatchDialog = false }) { Text("Cancel") }
            }
        )
    }
}
INNER

# ContactIngestSection.kt
cat << 'INNER' > app/src/main/java/com/example/ui/components/ContactIngestSection.kt
package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ContactIngestSection(
    filterOnlyNumbers: Boolean,
    onFilterOnlyNumbersChange: (Boolean) -> Unit,
    deduplicate: Boolean,
    onDeduplicateChange: (Boolean) -> Unit,
    isImporting: Boolean,
    onImportRawText: (String, Boolean) -> Unit,
    onImportFile: (Uri, String, Boolean) -> Unit,
    onClearAll: () -> Unit,
    onLoadSampleData: () -> Unit,
    totalContacts: Int,
    batchSplitSize: Int
) {
    var rawText by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val name = cursor?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else "Unknown"
            } ?: "Unknown"
            onImportFile(it, name, false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Text("Import Contacts", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Add recipients to current audience", fontSize = 13.sp, color = TextSecondary)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Big File Upload Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(TelecomCobaltLight.copy(alpha=0.5f))
                .border(2.dp, TelecomCobaltLight, RoundedCornerShape(16.dp))
                .clickable { fileLauncher.launch(arrayOf("*/*")) }
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = TelecomCobalt, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Tap to upload CSV or Excel", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TelecomCobalt)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Divider(modifier = Modifier.weight(1f), color = BorderSubtle)
            Text(" OR PASTE RAW ", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(horizontal = 8.dp))
            Divider(modifier = Modifier.weight(1f), color = BorderSubtle)
        }
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            placeholder = { Text("Paste numbers here...") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TelecomCobalt,
                unfocusedBorderColor = BorderSubtle
            )
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = { onImportRawText(rawText, false); rawText = "" },
            enabled = rawText.isNotBlank() && !isImporting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TelecomCobalt)
        ) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CardSurface, strokeWidth = 2.dp)
            } else {
                Text("Process Raw Text", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-Extract & Filter", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = filterOnlyNumbers, 
                onCheckedChange = onFilterOnlyNumbersChange,
                colors = SwitchDefaults.colors(checkedTrackColor = TelecomCobalt)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Deduplicate List", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = deduplicate, 
                onCheckedChange = onDeduplicateChange,
                colors = SwitchDefaults.colors(checkedTrackColor = TelecomCobalt)
            )
        }

        if (totalContacts > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onClearAll,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = StatusFailedBg, contentColor = StatusFailed),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Audience", fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onLoadSampleData,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Load Sample Data")
            }
        }
    }
}
INNER
