package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AiReplyCategory
import com.example.data.model.AiReplyLanguage
import com.example.data.model.CallType
import com.example.data.model.ChatMessage
import com.example.data.model.GroupChat
import com.example.data.model.LiveLocationDuration
import com.example.data.model.MessageType
import com.example.data.model.PrivacyLevel
import com.example.data.model.PrivacySettings
import com.example.data.model.ReplySuggestionsUiState
import com.example.data.model.UserProfile
import com.example.ui.components.AbhiAiBottomSheet
import com.example.ui.components.MediaBitmapOrUrlImage
import com.example.ui.components.ReportUserDialog
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.components.formatTimestampTime
import com.example.ui.theme.DangerRed
import com.example.ui.theme.IncomingBubbleDark
import com.example.ui.theme.IncomingBubbleLight
import com.example.ui.theme.OutgoingBubbleDark
import com.example.ui.theme.OutgoingBubbleLight
import com.example.ui.viewmodel.UiState
import com.example.util.LocationHelper
import com.example.util.MediaUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    currentUserId: String,
    chatId: String,
    conversationType: String, // "DIRECT" or "GROUP"
    title: String,
    recipientId: String,
    memberIds: List<String>,
    messagesState: UiState<List<ChatMessage>>,
    otherUser: UserProfile?,
    group: GroupChat?,
    allUsers: List<UserProfile>,
    allGroups: List<GroupChat>,
    isBlocked: Boolean,
    isBlockedByPeer: Boolean = false,
    peerPrivacy: PrivacySettings? = null,
    isFriendWithPeer: Boolean = false,
    selectedAiCategory: AiReplyCategory = AiReplyCategory.FRIENDLY,
    selectedAiLanguage: AiReplyLanguage = AiReplyLanguage.ALL,
    replySuggestionsState: ReplySuggestionsUiState = ReplySuggestionsUiState.Idle,
    onSelectAiCategory: (AiReplyCategory, List<ChatMessage>) -> Unit = { _, _ -> },
    onSelectAiLanguage: (AiReplyLanguage, List<ChatMessage>) -> Unit = { _, _ -> },
    onGenerateReplies: (List<ChatMessage>, AiReplyCategory, AiReplyLanguage, String) -> Unit = { _, _, _, _ -> },
    onEditAiSuggestion: (String, String) -> Unit = { _, _ -> },
    onDeleteAiSuggestion: (String) -> Unit = { _ -> },
    onDismissReplies: () -> Unit = {},
    onBack: () -> Unit,
    onOpenGroupInfo: () -> Unit,
    onStartCall: (UserProfile, CallType) -> Unit,
    onSendText: (text: String, replyTo: ChatMessage?) -> Unit,
    onSendMedia: (MessageType, String, Long, String, ChatMessage?) -> Unit,
    onShareLocation: (lat: Double, lng: Double, isLive: Boolean, duration: LiveLocationDuration) -> Unit,
    onStopLiveLocation: (messageId: String, lat: Double, lng: Double) -> Unit,
    onEditMessage: (messageId: String, newText: String) -> Unit,
    onDeleteForMe: (ChatMessage) -> Unit,
    onDeleteForEveryone: (messageId: String) -> Unit,
    onForwardMessage: (ChatMessage, List<UserProfile>, List<GroupChat>) -> Unit,
    onBlockUser: (UserProfile) -> Unit,
    onReportUser: (UserProfile, String, String, Boolean) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var messagePendingDeleteConfirm by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToForward by remember { mutableStateOf<ChatMessage?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showLocationChooser by remember { mutableStateOf(false) }
    var selectedLocationDuration by remember { mutableStateOf(LiveLocationDuration.MIN_15) }
    var showAiBottomSheet by remember { mutableStateOf(false) }

    val canSeePeerOnline = remember(peerPrivacy, isFriendWithPeer) {
        when (PrivacyLevel.fromRaw(peerPrivacy?.onlineStatusPrivacy)) {
            PrivacyLevel.EVERYONE -> true
            PrivacyLevel.FRIENDS -> isFriendWithPeer
            PrivacyLevel.NOBODY -> false
        }
    }
    val canSeePeerLastSeen = remember(peerPrivacy, isFriendWithPeer) {
        when (PrivacyLevel.fromRaw(peerPrivacy?.lastSeenPrivacy)) {
            PrivacyLevel.EVERYONE -> true
            PrivacyLevel.FRIENDS -> isFriendWithPeer
            PrivacyLevel.NOBODY -> false
        }
    }
    val canSeePeerPhoto = remember(peerPrivacy, isFriendWithPeer) {
        when (PrivacyLevel.fromRaw(peerPrivacy?.profilePhotoPrivacy)) {
            PrivacyLevel.EVERYONE -> true
            PrivacyLevel.FRIENDS -> isFriendWithPeer
            PrivacyLevel.NOBODY -> false
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                val media = MediaUtils.processPickedMedia(context, it)
                if (media != null) {
                    val mType = if (media.mediaType == "VIDEO") MessageType.VIDEO else MessageType.IMAGE
                    onSendMedia(mType, media.dataUri, media.durationSec, inputText, replyingTo)
                    inputText = ""
                    replyingTo = null
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            showLocationChooser = true
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = conversationType == "GROUP") { onOpenGroupInfo() }
                    ) {
                        if (conversationType == "GROUP") {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                val groupPhoto = group?.photoData
                                if (!groupPhoto.isNullOrBlank()) {
                                    MediaBitmapOrUrlImage(groupPhoto, title, Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        } else {
                            UserAvatar(
                                name = title,
                                avatarUrl = if (canSeePeerPhoto) otherUser?.avatarUrl.orEmpty() else "",
                                size = 38.dp,
                                showOnlineDot = canSeePeerOnline && otherUser?.online == true
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val peerStatusText = when {
                                conversationType == "GROUP" -> "${group?.memberIds?.size ?: memberIds.size} members • Tap for info"
                                isBlocked || isBlockedByPeer -> "Messaging unavailable"
                                canSeePeerOnline && otherUser?.online == true -> "Online"
                                canSeePeerLastSeen && otherUser?.lastSeenAt != null -> "Last seen ${formatRelativeTime(otherUser.lastSeenAt)}"
                                canSeePeerOnline -> "Offline"
                                else -> "Privacy protected"
                            }
                            Text(
                                text = peerStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (conversationType == "DIRECT" && otherUser != null && !isBlocked && !isBlockedByPeer) {
                        IconButton(
                            onClick = { onStartCall(otherUser, CallType.VOICE) },
                            modifier = Modifier.testTag("voice_call_button")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call")
                        }
                        IconButton(
                            onClick = { onStartCall(otherUser, CallType.VIDEO) },
                            modifier = Modifier.testTag("video_call_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                        }
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            if (conversationType == "GROUP") {
                                DropdownMenuItem(
                                    text = { Text("Group Info & Members") },
                                    leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onOpenGroupInfo()
                                    }
                                )
                            } else if (otherUser != null) {
                                DropdownMenuItem(
                                    text = { Text("Block ${otherUser.displayName}") },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = DangerRed) },
                                    onClick = {
                                        showMoreMenu = false
                                        onBlockUser(otherUser)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Report ${otherUser.displayName}") },
                                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = DangerRed) },
                                    onClick = {
                                        showMoreMenu = false
                                        showReportDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (messagesState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(messagesState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is UiState.Success -> {
                        if (messages.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Say hello! Long-press any message to reply, edit, forward, or delete.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(32.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("messages_list")
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    MessageBubble(
                                        message = msg,
                                        isMine = msg.senderId == currentUserId,
                                        showSenderName = conversationType == "GROUP" && msg.senderId != currentUserId,
                                        onLongOrTap = { selectedMessageForActions = msg },
                                        onOpenMap = {
                                            LocationHelper.openInMaps(
                                                context,
                                                msg.locationLat,
                                                msg.locationLng,
                                                "${msg.senderName}'s Location"
                                            )
                                        },
                                        onStopLiveLocation = {
                                            onStopLiveLocation(msg.id, msg.locationLat, msg.locationLng)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isBlocked || isBlockedByPeer) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBlocked) {
                            "You have blocked this user. Unblock from Profile > Blocked Contacts to send messages."
                        } else {
                            "You cannot send messages or place calls to this user."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                // Reply or Edit Context Banner
                val activeContextLabel = when {
                    editingMessage != null -> "Editing message"
                    replyingTo != null -> "Replying to ${replyingTo?.senderName}"
                    else -> null
                }
                val activeContextPreview = when {
                    editingMessage != null -> editingMessage?.text.orEmpty()
                    replyingTo != null -> replyingTo?.text?.ifBlank { replyingTo?.messageType }.orEmpty()
                    else -> ""
                }
                if (activeContextLabel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (editingMessage != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activeContextLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(activeContextPreview, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = {
                                if (editingMessage != null) inputText = ""
                                editingMessage = null
                                replyingTo = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        }
                    }
                }

                // Composer Row with ABHI AI Trigger Icon
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                mediaPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.testTag("attach_media_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Send Image or Video")
                        }
                        IconButton(
                            onClick = {
                                val fineGranted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val coarseGranted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (fineGranted || coarseGranted) {
                                    showLocationChooser = true
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.testTag("share_location_button")
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Share Live Location")
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text(if (editingMessage != null) "Edit message..." else "Message...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_input")
                        )
                        IconButton(
                            onClick = {
                                showAiBottomSheet = true
                                if (replySuggestionsState is ReplySuggestionsUiState.Idle) {
                                    onGenerateReplies(messages, selectedAiCategory, selectedAiLanguage, "")
                                }
                            },
                            modifier = Modifier.testTag("gemini_reply_trigger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Open ABHI AI Assistant",
                                tint = if (showAiBottomSheet || replySuggestionsState is ReplySuggestionsUiState.Success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        IconButton(
                            onClick = {
                                val editTarget = editingMessage
                                if (editTarget != null) {
                                    onEditMessage(editTarget.id, inputText)
                                    editingMessage = null
                                    inputText = ""
                                } else {
                                    onSendText(inputText, replyingTo)
                                    inputText = ""
                                    replyingTo = null
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Message Action Dialog (Reply, Forward, Edit, Delete for Me, Delete for Everyone)
    selectedMessageForActions?.let { msg ->
        val isMine = msg.senderId == currentUserId
        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            title = { Text("Message Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!msg.deletedForEveryone) {
                        MessageActionRow(Icons.AutoMirrored.Filled.Reply, "Reply") {
                            replyingTo = msg
                            editingMessage = null
                            selectedMessageForActions = null
                        }
                        MessageActionRow(Icons.AutoMirrored.Filled.Forward, "Forward") {
                            messageToForward = msg
                            selectedMessageForActions = null
                        }
                        if (isMine && msg.messageType == MessageType.TEXT.name) {
                            MessageActionRow(Icons.Default.Edit, "Edit Message") {
                                editingMessage = msg
                                replyingTo = null
                                inputText = msg.text
                                selectedMessageForActions = null
                            }
                        }
                    }
                    MessageActionRow(Icons.Default.Delete, "Delete for Me", DangerRed) {
                        selectedMessageForActions = null
                        onDeleteForMe(msg)
                    }
                    if (isMine && !msg.deletedForEveryone) {
                        MessageActionRow(Icons.Default.Delete, "Delete for Everyone", DangerRed) {
                            selectedMessageForActions = null
                            messagePendingDeleteConfirm = msg
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForActions = null }) { Text("Close") }
            }
        )
    }

    // Confirmation Before Delete for Everyone
    messagePendingDeleteConfirm?.let { msg ->
        AlertDialog(
            onDismissRequest = { messagePendingDeleteConfirm = null },
            title = { Text("Delete for Everyone?") },
            text = { Text("This message will be replaced with \"This message was deleted\" for all participants in real time.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteForEveryone(msg.id)
                        messagePendingDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.testTag("confirm_delete_everyone_button")
                ) {
                    Text("Delete for Everyone")
                }
            },
            dismissButton = {
                TextButton(onClick = { messagePendingDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }

    // Forward to Multiple Users & Groups Dialog
    messageToForward?.let { msg ->
        ForwardMessageDialog(
            users = allUsers.filter { it.userId != currentUserId },
            groups = allGroups,
            onDismiss = { messageToForward = null },
            onForward = { targetUsers, targetGroups ->
                onForwardMessage(msg, targetUsers, targetGroups)
                messageToForward = null
            }
        )
    }

    // Live Location Chooser Dialog with Duration Selector (15 mins, 1 hour, 8 hours)
    if (showLocationChooser) {
        AlertDialog(
            onDismissRequest = { showLocationChooser = false },
            title = { Text("Share Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Send a one-time current location pin or share live GPS updates for a selected duration.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Live Sharing Duration",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(LiveLocationDuration.entries) { dur ->
                            FilterChip(
                                selected = selectedLocationDuration == dur,
                                onClick = { selectedLocationDuration = dur },
                                label = { Text(dur.label) },
                                modifier = Modifier.testTag("live_loc_duration_${dur.name.lowercase()}")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosen = selectedLocationDuration
                        showLocationChooser = false
                        scope.launch {
                            val coords = LocationHelper.fetchCurrentLocation(context)
                            if (coords != null) {
                                onShareLocation(coords.latitude, coords.longitude, true, chosen)
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_share_live_location")
                ) {
                    Text("Share Live (${selectedLocationDuration.label})")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showLocationChooser = false
                        scope.launch {
                            val coords = LocationHelper.fetchCurrentLocation(context)
                            if (coords != null) {
                                onShareLocation(coords.latitude, coords.longitude, false, selectedLocationDuration)
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_share_pin_location")
                ) {
                    Text("Current Pin")
                }
            }
        )
    }

    if (showAiBottomSheet) {
        AbhiAiBottomSheet(
            recentMessages = messages,
            currentUserId = currentUserId,
            selectedCategory = selectedAiCategory,
            selectedLanguage = selectedAiLanguage,
            suggestionsState = replySuggestionsState,
            onSelectCategory = { cat -> onSelectAiCategory(cat, messages) },
            onSelectLanguage = { lang -> onSelectAiLanguage(lang, messages) },
            onRegenerate = { custom ->
                onGenerateReplies(messages, selectedAiCategory, selectedAiLanguage, custom)
            },
            onEditSuggestion = { id, newText -> onEditAiSuggestion(id, newText) },
            onDeleteSuggestion = { id -> onDeleteAiSuggestion(id) },
            onCopied = {
                Toast.makeText(context, "Reply copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onSendReply = { approvedText ->
                onSendText(approvedText, replyingTo)
                replyingTo = null
                showAiBottomSheet = false
                onDismissReplies()
            },
            onDismiss = {
                showAiBottomSheet = false
                onDismissReplies()
            }
        )
    }

    if (showReportDialog && otherUser != null) {
        ReportUserDialog(
            targetName = otherUser.displayName,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details, alsoBlock ->
                showReportDialog = false
                onReportUser(otherUser, reason, details, alsoBlock)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    showSenderName: Boolean,
    onLongOrTap: () -> Unit,
    onOpenMap: () -> Unit,
    onStopLiveLocation: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val bubbleColor = when {
        message.deletedForEveryone -> MaterialTheme.colorScheme.surfaceVariant
        isMine -> if (isDark) OutgoingBubbleDark else OutgoingBubbleLight
        else -> if (isDark) IncomingBubbleDark else IncomingBubbleLight
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = onLongOrTap,
                    onLongClick = onLongOrTap
                )
                .testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (showSenderName) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (message.forwarded && !message.deletedForEveryone) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Forward,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Forwarded",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (message.replyToMessageId.isNotBlank() && !message.deletedForEveryone) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = message.replyToSenderName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = message.replyToText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (message.deletedForEveryone) {
                    Text(
                        text = "This message was deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    when (MessageType.fromRaw(message.messageType)) {
                        MessageType.IMAGE -> {
                            if (message.mediaData.isNotBlank()) {
                                MediaBitmapOrUrlImage(
                                    data = message.mediaData,
                                    contentDescription = "Photo message",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                            if (message.text.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        MessageType.VIDEO -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (message.mediaData.isNotBlank()) {
                                    MediaBitmapOrUrlImage(
                                        data = message.mediaData,
                                        contentDescription = "Video thumbnail",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "${message.mediaDurationSec}s",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (message.text.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(message.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        MessageType.LOCATION -> {
                            var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
                            val isExpired = message.locationExpiresAtMillis > 0L && nowMillis >= message.locationExpiresAtMillis
                            val isCurrentlyLive = message.locationLive && !isExpired

                            LaunchedEffect(message.id, message.locationLive, message.locationExpiresAtMillis) {
                                if (message.locationLive && !isExpired) {
                                    while (true) {
                                        nowMillis = System.currentTimeMillis()
                                        if (message.locationExpiresAtMillis > 0L && nowMillis >= message.locationExpiresAtMillis) {
                                            break
                                        }
                                        delay(10_000L)
                                    }
                                }
                            }

                            val gridColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            val pinColor = if (isCurrentlyLive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenMap)
                                    .testTag("location_card_${message.id}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(92.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val cols = 5
                                            val rows = 3
                                            for (c in 1 until cols) {
                                                val x = size.width * c / cols
                                                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                                            }
                                            for (r in 1 until rows) {
                                                val y = size.height * r / rows
                                                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                                            }
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            if (isCurrentlyLive) {
                                                drawCircle(pinColor.copy(alpha = 0.16f), radius = 34f, center = center)
                                                drawCircle(pinColor.copy(alpha = 0.35f), radius = 22f, center = center, style = Stroke(width = 2f))
                                            }
                                            drawCircle(pinColor, radius = 9f, center = center)
                                        }
                                        Surface(
                                            color = if (isCurrentlyLive) Color(0xFF065F46) else MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.MyLocation,
                                                    contentDescription = null,
                                                    tint = if (isCurrentlyLive) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = when {
                                                        isCurrentlyLive -> "LIVE • ${LocationHelper.formatRemainingDuration(message.locationExpiresAtMillis, nowMillis)}"
                                                        message.locationLive && isExpired -> "Live Ended"
                                                        else -> "Location Pin"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isCurrentlyLive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = LocationHelper.formatCoordinates(message.locationLat, message.locationLng),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = onOpenMap,
                                            modifier = Modifier.testTag("open_maps_button_${message.id}")
                                        ) {
                                            Text("Open Google Maps")
                                        }
                                        if (isMine && isCurrentlyLive) {
                                            TextButton(
                                                onClick = onStopLiveLocation,
                                                modifier = Modifier.testTag("stop_live_location_${message.id}")
                                            ) {
                                                Text("Stop Live", color = DangerRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (message.edited && !message.deletedForEveryone) {
                        val editTime = formatTimestampTime(message.editedAt)
                        Text(
                            text = "Edited $editTime • ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatTimestampTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun ForwardMessageDialog(
    users: List<UserProfile>,
    groups: List<GroupChat>,
    onDismiss: () -> Unit,
    onForward: (List<UserProfile>, List<GroupChat>) -> Unit
) {
    val selectedUserIds = remember { mutableStateListOf<String>() }
    val selectedGroupIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward to Multiple Recipients") },
        text = {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                if (groups.isNotEmpty()) {
                    item {
                        Text("Groups", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    items(groups, key = { "fg_${it.id}" }) { grp ->
                        val checked = selectedGroupIds.contains(grp.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedGroupIds.remove(grp.id) else selectedGroupIds.add(grp.id)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selectedGroupIds.add(grp.id) else selectedGroupIds.remove(grp.id)
                                }
                            )
                            Text(grp.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    Text("Users", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(users, key = { "fu_${it.userId}" }) { u ->
                    val checked = selectedUserIds.contains(u.userId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selectedUserIds.remove(u.userId) else selectedUserIds.add(u.userId)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                if (it) selectedUserIds.add(u.userId) else selectedUserIds.remove(u.userId)
                            }
                        )
                        UserAvatar(u.displayName, u.avatarUrl, size = 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(u.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onForward(
                        users.filter { selectedUserIds.contains(it.userId) },
                        groups.filter { selectedGroupIds.contains(it.id) }
                    )
                },
                enabled = selectedUserIds.isNotEmpty() || selectedGroupIds.isNotEmpty(),
                modifier = Modifier.testTag("confirm_forward_button")
            ) {
                Text("Forward (${selectedUserIds.size + selectedGroupIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
