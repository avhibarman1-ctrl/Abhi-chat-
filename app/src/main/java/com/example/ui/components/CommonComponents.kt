package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.CallSession
import com.example.data.model.CallType
import com.example.ui.theme.DangerRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.StoryRingEnd
import com.example.ui.theme.StoryRingStart
import com.example.util.MediaUtils
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MediaBitmapOrUrlImage(
    data: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (data.startsWith("data:image")) {
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = data) {
            value = withContext(Dispatchers.Default) {
                MediaUtils.decodeDataUriBitmap(data)
            }
        }
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    } else if (data.isNotBlank()) {
        AsyncImage(
            model = data,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
fun UserAvatar(
    name: String,
    avatarUrl: String,
    size: Dp = 48.dp,
    showOnlineDot: Boolean = false,
    hasStoryRing: Boolean = false,
    storySeen: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        val ringModifier = if (hasStoryRing) {
            val brush = if (storySeen) {
                Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFFCBD5E1)))
            } else {
                Brush.linearGradient(listOf(StoryRingStart, StoryRingEnd))
            }
            Modifier
                .fillMaxSize()
                .border(2.5.dp, brush, CircleShape)
                .padding(3.5.dp)
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = ringModifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                MediaBitmapOrUrlImage(
                    data = avatarUrl,
                    contentDescription = "$name avatar",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initials = name.trim()
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.first().uppercaseChar().toString() }
                    .ifBlank { "A" }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.27f).dp.coerceIn(10.dp, 14.dp))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ReportUserDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String, alsoBlock: Boolean) -> Unit
) {
    val reasons = listOf(
        "Spam or Scam",
        "Harassment or Bullying",
        "Inappropriate Content",
        "Fake Profile or Impersonation",
        "Privacy Violation"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Flag, contentDescription = null, tint = DangerRed) },
        title = { Text("Report $targetName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select a reason for reporting this user. Reports are encrypted and reviewed securely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                reasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Additional details (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alsoBlock, onCheckedChange = { alsoBlock = it })
                    Text("Also block $targetName", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details, alsoBlock) },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IncomingCallBanner(
    call: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                UserAvatar(
                    name = call.callerName,
                    avatarUrl = call.callerAvatarUrl,
                    size = 44.dp
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = call.callerName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (call.callType == CallType.VIDEO.name) "Incoming Video Call" else "Incoming Voice Call",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDecline,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    modifier = Modifier.testTag("decline_call_button")
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Decline call", modifier = Modifier.size(18.dp))
                }
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                    modifier = Modifier.testTag("accept_call_button")
                ) {
                    Icon(
                        if (call.callType == CallType.VIDEO.name) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = "Accept call",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

fun formatTimestampTime(ts: Timestamp?): String {
    if (ts == null) return "Just now"
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(ts.toDate())
}

fun formatRelativeTime(ts: Timestamp?): String {
    if (ts == null) return "Just now"
    val diffMs = System.currentTimeMillis() - ts.toDate().time
    val mins = (diffMs / 60_000L).coerceAtLeast(0L)
    return when {
        mins < 1L -> "Just now"
        mins < 60L -> "${mins}m ago"
        mins < 1440L -> "${mins / 60L}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(ts.toDate())
    }
}
