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
