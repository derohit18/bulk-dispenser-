package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ComposerSection(
    messageText: String,
    onMessageChange: (String) -> Unit,
    delaySeconds: Int,
    onDelayChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val charCount = messageText.length
    val smsParts = if (charCount == 0) 1 else if (charCount <= 160) 1 else ((charCount - 1) / 153) + 1

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
                Text("Message Composer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Write your campaign template", fontSize = 13.sp, color = TextSecondary)
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (smsParts > 1) StatusThrottleBg else CardSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("$charCount chars · $smsParts part${if(smsParts > 1) "s" else ""}", 
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, 
                    color = if (smsParts > 1) StatusThrottle else TelecomCobalt)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp),
            placeholder = { Text("Start typing your message...", color = TextMuted) },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TelecomCobalt,
                unfocusedBorderColor = BorderSubtle,
                focusedContainerColor = CardSurfaceVariant.copy(alpha=0.3f),
                unfocusedContainerColor = CardSurfaceVariant.copy(alpha=0.3f),
            ),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            FilledTonalButton(
                onClick = { if (!messageText.contains("{name}")) onMessageChange("$messageText{name}") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = TelecomCobaltLight, contentColor = TelecomCobaltDark)
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Insert {name}", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Divider(color = BorderSubtle)
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dispatch Pacing", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Time between each SMS", fontSize = 13.sp, color = TextSecondary)
            }
            Text("${delaySeconds}s", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TelecomCobalt)
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Slider(
            value = delaySeconds.toFloat(),
            onValueChange = { onDelayChange(it.toInt()) },
            valueRange = 1f..60f,
            colors = SliderDefaults.colors(thumbColor = TelecomCobalt, activeTrackColor = TelecomCobalt)
        )
    }
}
