package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AudiencePrivacy
import com.example.data.model.StatusItem
import com.example.data.model.StatusView
import com.example.data.model.StoryItem
import com.example.data.model.StoryView
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MediaBitmapOrUrlImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.SeenStatusRing
import com.example.ui.theme.StoryRingEnd
import com.example.ui.theme.StoryRingStart
import com.example.ui.theme.UnseenStatusRing
import com.example.ui.viewmodel.UiState
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

@Composable
fun UpdatesScreen(
    currentUserId: String,
    myProfile: UserProfile?,
    statusesState: UiState<List<StatusItem>>,
    statusViewsState: UiState<List<StatusView>>,
    storiesState: UiState<List<StoryItem>>,
    storyViewsState: UiState<List<StoryView>>,
    onPostStatus: (mediaType: String, text: String, bgHex: String, mediaData: String, privacy: AudiencePrivacy) -> Unit,
    onMarkStatusViewed: (StatusItem) -> Unit,
    onReplyToStatus: (StatusItem, String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onPostStory: (mediaType: String, caption: String, overlayText: String, bgHex: String, mediaData: String, privacy: AudiencePrivacy) -> Unit,
    onMarkStoryViewed: (StoryItem) -> Unit,
    onReactToStory: (StoryItem, String) -> Unit,
    onReplyToStory: (StoryItem, String) -> Unit,
    onDeleteStory: (String) -> Unit
) {
    val statuses = (statusesState as? UiState.Success)?.data.orEmpty()
    val statusViews = (statusViewsState as? UiState.Success)?.data.orEmpty()
    val stories = (storiesState as? UiState.Success)?.data.orEmpty()
    val storyViews = (storyViewsState as? UiState.Success)?.data.orEmpty()

    val seenStatusIds = remember(statusViews, currentUserId) {
        statusViews.filter { it.viewerId == currentUserId }.map { it.statusId }.toSet()
    }
    val seenStoryIds = remember(storyViews, currentUserId) {
        storyViews.filter { it.viewerId == currentUserId }.map { it.storyId }.toSet()
    }

    var showStatusComposer by remember { mutableStateOf(false) }
    var showStoryComposer by remember { mutableStateOf(false) }
    var activeStatusViewer by remember { mutableStateOf<StatusItem?>(null) }
    var activeStoryViewer by remember { mutableStateOf<StoryItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SECTION 1: FACEBOOK STYLE STORIES ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Stories", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "24-hour photo, video & text stories with reactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showStoryComposer = true },
                    modifier = Modifier.testTag("add_story_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Story")
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    CreateStoryCard(
                        myProfile = myProfile,
                        onClick = { showStoryComposer = true }
                    )
                }
                items(stories, key = { it.id }) { story ->
                    val seen = story.userId == currentUserId || seenStoryIds.contains(story.id)
                    StoryFeedCard(
                        story = story,
                        seen = seen,
                        onClick = { activeStoryViewer = story }
                    )
                }
            }
        }

        // --- SECTION 2: WHATSAPP STYLE STATUSES ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Status Updates", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "WhatsApp-style 24h status with privacy & viewers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { showStatusComposer = true },
                    modifier = Modifier.testTag("add_status_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("My Status")
                }
            }
        }

        val myStatuses = statuses.filter { it.userId == currentUserId }
        val otherStatuses = statuses.filter { it.userId != currentUserId }
        val unseenStatuses = otherStatuses.filter { !seenStatusIds.contains(it.id) }
        val viewedStatuses = otherStatuses.filter { seenStatusIds.contains(it.id) }

        if (myStatuses.isNotEmpty()) {
            item {
                Text("My Statuses (${myStatuses.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            items(myStatuses, key = { "myst_${it.id}" }) { st ->
                StatusRowItem(
                    status = st,
                    isMine = true,
                    seen = true,
                    onClick = { activeStatusViewer = st }
                )
            }
        }

        if (unseenStatuses.isNotEmpty()) {
            item {
                Text("Recent Updates", style = MaterialTheme.typography.labelLarge, color = UnseenStatusRing)
            }
            items(unseenStatuses, key = { "unseen_${it.id}" }) { st ->
                StatusRowItem(
                    status = st,
                    isMine = false,
                    seen = false,
                    onClick = { activeStatusViewer = st }
                )
            }
        }

        if (viewedStatuses.isNotEmpty()) {
            item {
                Text("Viewed Updates", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(viewedStatuses, key = { "seen_${it.id}" }) { st ->
                StatusRowItem(
                    status = st,
                    isMine = false,
                    seen = true,
                    onClick = { activeStatusViewer = st }
                )
            }
        }

        if (statuses.isEmpty() && stories.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.AutoAwesome,
                    title = "No active updates yet",
                    subtitle = "Post a 24-hour Status or Facebook-style Story to share moments with friends."
                )
            }
        }
    }

    if (showStatusComposer) {
        StatusOrStoryComposerDialog(
            isStory = false,
            onDismiss = { showStatusComposer = false },
            onPost = { mediaType, text, overlayOrUnused, bgHex, mediaData, privacy ->
                showStatusComposer = false
                onPostStatus(mediaType, text, bgHex, mediaData, privacy)
            }
        )
    }

    if (showStoryComposer) {
        StatusOrStoryComposerDialog(
            isStory = true,
            onDismiss = { showStoryComposer = false },
            onPost = { mediaType, caption, overlayText, bgHex, mediaData, privacy ->
                showStoryComposer = false
                onPostStory(mediaType, caption, overlayText, bgHex, mediaData, privacy)
            }
        )
    }

    activeStatusViewer?.let { st ->
        val viewers = statusViews.filter { it.statusId == st.id }
        StatusViewerDialog(
            status = st,
            isMine = st.userId == currentUserId,
            viewers = viewers,
            onViewed = { onMarkStatusViewed(st) },
            onReply = { reply ->
                onReplyToStatus(st, reply)
                activeStatusViewer = null
            },
            onDelete = {
                onDeleteStatus(st.id)
                activeStatusViewer = null
            },
            onDismiss = { activeStatusViewer = null }
        )
    }

    activeStoryViewer?.let { story ->
        val liveStory = stories.firstOrNull { it.id == story.id } ?: story
        val viewers = storyViews.filter { it.storyId == story.id }
        StoryViewerDialog(
            story = liveStory,
            currentUserId = currentUserId,
            viewers = viewers,
            onViewed = { onMarkStoryViewed(liveStory) },
            onReact = { emoji -> onReactToStory(liveStory, emoji) },
            onReply = { reply -> onReplyToStory(liveStory, reply) },
            onDelete = {
                onDeleteStory(liveStory.id)
                activeStoryViewer = null
            },
            onDismiss = { activeStoryViewer = null }
        )
    }
}

@Composable
private fun CreateStoryCard(
    myProfile: UserProfile?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .width(116.dp)
            .height(176.dp)
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (!myProfile?.avatarUrl.isNullOrBlank()) {
                    MediaBitmapOrUrlImage(myProfile!!.avatarUrl, "My profile", Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Story", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Create Story", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun StoryFeedCard(
    story: StoryItem,
    seen: Boolean,
    onClick: () -> Unit
) {
    val bgColor = parseHexColor(story.backgroundColorHex, Color(0xFF1D4ED8))
    Surface(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(120.dp)
            .height(176.dp)
            .border(
                width = 2.5.dp,
                brush = if (seen) {
                    Brush.linearGradient(listOf(SeenStatusRing, SeenStatusRing))
                } else {
                    Brush.linearGradient(listOf(StoryRingStart, StoryRingEnd))
                },
                shape = RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("story_card_${story.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
            if (story.mediaData.isNotBlank()) {
                MediaBitmapOrUrlImage(
                    data = story.mediaData,
                    contentDescription = story.caption,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f))))
            )
            UserAvatar(
                name = story.userName,
                avatarUrl = story.userAvatarUrl,
                size = 34.dp,
                hasStoryRing = true,
                storySeen = seen,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
            if (story.mediaType == "VIDEO") {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Video story",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                )
            } else if (story.overlayText.isNotBlank() || story.caption.isNotBlank()) {
                Text(
                    text = story.overlayText.ifBlank { story.caption },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp)
                )
            }
            Text(
                text = story.userName,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun StatusRowItem(
    status: StatusItem,
    isMine: Boolean,
    seen: Boolean,
    onClick: () -> Unit
) {
    val ringColor = if (seen) SeenStatusRing else UnseenStatusRing
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("status_row_${status.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .border(2.5.dp, ringColor, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(status.backgroundColorHex, Color(0xFF0F766E))),
                contentAlignment = Alignment.Center
            ) {
                if (status.mediaData.isNotBlank()) {
                    MediaBitmapOrUrlImage(status.mediaData, status.userName, Modifier.fillMaxSize())
                } else {
                    Text(
                        text = status.text.take(8),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMine) "My Status" else status.userName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${formatRelativeTime(status.createdAt)} • ${status.privacy.lowercase().replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status.text.isNotBlank()) {
                    Text(
                        text = status.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isMine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = "Views", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("${status.viewerCount}", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Text(
                    text = if (seen) "Seen" else "New",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (seen) SeenStatusRing else UnseenStatusRing,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusOrStoryComposerDialog(
    isStory: Boolean,
    onDismiss: () -> Unit,
    onPost: (mediaType: String, textOrCaption: String, overlayText: String, bgHex: String, mediaData: String, privacy: AudiencePrivacy) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = listOf("#0F766E", "#1D4ED8", "#7C3AED", "#BE123C", "#B45309")

    var mediaType by remember { mutableStateOf("TEXT") }
    var textOrCaption by remember { mutableStateOf("") }
    var overlayText by remember { mutableStateOf("") }
    var bgHex by remember { mutableStateOf(if (isStory) "#1D4ED8" else "#0F766E") }
    var mediaData by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(AudiencePrivacy.EVERYONE) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                val media = MediaUtils.processPickedMedia(context, it)
                if (media != null) {
                    mediaType = if (media.mediaType == "VIDEO") "VIDEO" else "PHOTO"
                    mediaData = media.dataUri
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isStory) "Create Story (24h)" else "Post Status (24h)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (mediaData.isBlank()) "Pick Photo or Video" else "Change Media ($mediaType)")
                }

                if (mediaData.isNotBlank()) {
                    MediaBitmapOrUrlImage(
                        data = mediaData,
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                OutlinedTextField(
                    value = textOrCaption,
                    onValueChange = { textOrCaption = it },
                    label = { Text(if (isStory) "Caption" else "Status Text") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(if (isStory) "story_caption_input" else "status_text_input")
                )

                if (isStory) {
                    OutlinedTextField(
                        value = overlayText,
                        onValueChange = { overlayText = it },
                        label = { Text("Overlay Text on Story (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex, Color.Teal))
                                .border(
                                    width = if (bgHex == hex) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { bgHex = hex }
                        )
                    }
                }

                Text("Privacy", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AudiencePrivacy.entries.forEach { option ->
                        FilterChip(
                            selected = privacy == option,
                            onClick = { privacy = option },
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPost(mediaType, textOrCaption, overlayText, bgHex, mediaData, privacy)
                },
                enabled = textOrCaption.isNotBlank() || mediaData.isNotBlank() || overlayText.isNotBlank(),
                modifier = Modifier.testTag(if (isStory) "publish_story_button" else "publish_status_button")
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StatusViewerDialog(
    status: StatusItem,
    isMine: Boolean,
    viewers: List<StatusView>,
    onViewed: () -> Unit,
    onReply: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(status.id) { onViewed() }
    var replyText by remember { mutableStateOf("") }
    var showViewersSheet by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(parseHexColor(status.backgroundColorHex, Color(0xFF0F766E)))
                .padding(16.dp)
        ) {
            if (status.mediaData.isNotBlank()) {
                MediaBitmapOrUrlImage(
                    data = status.mediaData,
                    contentDescription = status.text,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(status.userName, status.userAvatarUrl, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(status.userName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(formatRelativeTime(status.createdAt), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row {
                    if (isMine) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete status", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            if (status.text.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Text(
                        text = status.text,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                if (isMine) {
                    Button(
                        onClick = { showViewersSheet = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Viewers (${viewers.size})")
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Reply to status...", color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { onReply(replyText) },
                            enabled = replyText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Reply", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showViewersSheet) {
        AlertDialog(
            onDismissRequest = { showViewersSheet = false },
            title = { Text("Status Viewers (${viewers.size})") },
            text = {
                if (viewers.isEmpty()) {
                    Text("No viewers yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(viewers, key = { it.id }) { v ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                UserAvatar(v.viewerName, v.viewerAvatarUrl, size = 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(v.viewerName, style = MaterialTheme.typography.titleSmall)
                                    Text(formatRelativeTime(v.viewedAt), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewersSheet = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun StoryViewerDialog(
    story: StoryItem,
    currentUserId: String,
    viewers: List<StoryView>,
    onViewed: () -> Unit,
    onReact: (String) -> Unit,
    onReply: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(story.id) { onViewed() }
    val isMine = story.userId == currentUserId
    var replyText by remember { mutableStateOf("") }
    var showViewersDialog by remember { mutableStateOf(false) }
    val reactions = listOf("❤️", "😂", "😮", "😢", "🔥", "👍")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(parseHexColor(story.backgroundColorHex, Color(0xFF1D4ED8)))
                .padding(16.dp)
        ) {
            if (story.mediaData.isNotBlank()) {
                MediaBitmapOrUrlImage(
                    data = story.mediaData,
                    contentDescription = story.caption,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(story.userName, story.userAvatarUrl, size = 40.dp, hasStoryRing = true)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(story.userName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(formatRelativeTime(story.createdAt), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row {
                    if (isMine) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete story", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            if (story.overlayText.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = story.overlayText,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (story.caption.isNotBlank()) {
                    Text(
                        text = story.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (story.reactionCounts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        story.reactionCounts.forEach { (emoji, count) ->
                            Surface(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "$emoji $count",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Story Reactions Bar
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(vertical = 6.dp)
                ) {
                    reactions.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .clickable { onReact(emoji) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isMine) {
                        Button(
                            onClick = { showViewersDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Story Viewers & Replies (${viewers.size})")
                        }
                    } else {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Reply to story...", color = Color.White.copy(alpha = 0.7f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                        )
                        IconButton(
                            onClick = {
                                onReply(replyText)
                                replyText = ""
                            },
                            enabled = replyText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Reply", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showViewersDialog) {
        AlertDialog(
            onDismissRequest = { showViewersDialog = false },
            title = { Text("Story Viewers (${viewers.size})") },
            text = {
                if (viewers.isEmpty()) {
                    Text("No viewers yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(viewers, key = { it.id }) { v ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(v.viewerName, v.viewerAvatarUrl, size = 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(v.viewerName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    if (v.reaction.isNotBlank()) {
                                        Text(v.reaction, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                if (v.replyText.isNotBlank()) {
                                    Text(
                                        text = "Replied: \"${v.replyText}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 44.dp, top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewersDialog = false }) { Text("Close") }
            }
        )
    }
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

private val Color.Companion.Teal: Color
    get() = Color(0xFF0F766E)
