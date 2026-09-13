package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.data.MessageStatus
import com.example.data.RecipientEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeliveryReportFeed(
    recipients: List<RecipientEntity>,
    onRemoveRecipient: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<MessageStatus?>(null) } 

    val filteredList = remember(recipients, selectedFilter) {
        if (selectedFilter == null) recipients else recipients.filter { it.status == selectedFilter }
    }

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
                Text("Delivery Reports", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Real-time SMS status log", fontSize = 13.sp, color = TextSecondary)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("${filteredList.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TelecomCobalt)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterTab("All", selectedFilter == null, { selectedFilter = null }, Modifier.weight(1f))
            FilterTab("Sent", selectedFilter == MessageStatus.DELIVERED, { selectedFilter = MessageStatus.DELIVERED }, Modifier.weight(1f))
            FilterTab("Errors", selectedFilter == MessageStatus.FAILED, { selectedFilter = MessageStatus.FAILED }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No records found", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredList.take(50).forEach { recipient ->
                    RecipientRow(recipient, onRemoveRecipient)
                }
            }
        }
    }
}

@Composable
private fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) TelecomCobalt else CardSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) CardSurface else TextSecondary
        )
    }
}

@Composable
private fun RecipientRow(recipient: RecipientEntity, onDelete: (Long) -> Unit) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when(recipient.status) {
            MessageStatus.DELIVERED -> Icons.Rounded.CheckCircle
            MessageStatus.FAILED -> Icons.Rounded.Error
            MessageStatus.QUEUED -> Icons.Rounded.Schedule
            else -> Icons.Rounded.Send
        }
        val tint = when(recipient.status) {
            MessageStatus.DELIVERED -> StatusDelivered
            MessageStatus.FAILED -> StatusFailed
            MessageStatus.QUEUED -> TextMuted
            else -> TelecomCobalt
        }
        
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(recipient.phoneNumber, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (recipient.displayName.isNotBlank()) {
                Text(recipient.displayName, fontSize = 13.sp, color = TextSecondary)
            }
        }
        
        val ts = recipient.deliveredAt ?: recipient.sentAt
        if (ts != null) {
            Text(formatter.format(Date(ts)), fontSize = 12.sp, color = TextMuted)
            Spacer(modifier = Modifier.width(12.dp))
        }
        
        IconButton(onClick = { onDelete(recipient.id) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
