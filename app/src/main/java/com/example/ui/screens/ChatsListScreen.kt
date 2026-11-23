package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BlockedUser
import com.example.data.model.ChatRoom
import com.example.data.model.FriendEdge
import com.example.data.model.GroupChat
import com.example.data.model.PrivacyLevel
import com.example.data.model.PrivacySettings
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MediaBitmapOrUrlImage
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.viewmodel.UiState
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

@Composable
fun ChatsListScreen(
    currentUserId: String,
    chatsState: UiState<List<ChatRoom>>,
    groupsState: UiState<List<GroupChat>>,
    usersState: UiState<List<UserProfile>>,
    storiesState: UiState<List<StoryItem>>,
    blockedUsers: List<BlockedUser>,
    friendsState: UiState<List<FriendEdge>> = UiState.Success(emptyList()),
    targetPrivacyMap: Map<String, PrivacySettings> = emptyMap(),
    onFetchTargetPrivacy: (String) -> Unit = {},
    onOpenDirectChat: (chatId: String, title: String, otherUserId: String) -> Unit,
    onOpenGroupChat: (groupId: String, title: String) -> Unit,
    onStartDirectChatWithUser: (UserProfile) -> Unit,
    onCreateGroup: (name: String, desc: String, photoData: String, members: List<UserProfile>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, DIRECT, GROUPS
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val usersMap = remember(users) { users.associateBy { it.userId } }
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.blockedId }.toSet() }
    val activeStoriesByUser = remember(storiesState) {
        (storiesState as? UiState.Success)?.data.orEmpty().map { it.userId }.toSet()
    }

    val chats = (chatsState as? UiState.Success)?.data.orEmpty()
    val groups = (groupsState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = { showCreateGroupDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("create_group_fab")
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Create Group")
                }
                FloatingActionButton(
                    onClick = { showNewChatDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("new_chat_fab")
                ) {
                    Icon(Icons.Default.AddComment, contentDescription = "New Chat")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search chats & groups...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("chats_search_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                FilterChip(
                    selected = filterMode == "ALL",
                    onClick = { filterMode = "ALL" },
                    label = { Text("All (${chats.size + groups.size})") }
                )
                FilterChip(
                    selected = filterMode == "DIRECT",
                    onClick = { filterMode = "DIRECT" },
                    label = { Text("Chats (${chats.size})") }
                )
                FilterChip(
                    selected = filterMode == "GROUPS",
                    onClick = { filterMode = "GROUPS" },
                    label = { Text("Groups (${groups.size})") }
                )
            }

            if (chatsState is UiState.Loading || groupsState is UiState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredChats = if (filterMode == "GROUPS") emptyList() else chats.filter { room ->
                    val otherId = room.participantIds.firstOrNull { it != currentUserId }.orEmpty()
                    val name = usersMap[otherId]?.displayName ?: room.participantNames[otherId] ?: "User"
                    !blockedIds.contains(otherId) &&
                        (searchQuery.isBlank() || name.contains(searchQuery, true) || room.lastMessage.contains(searchQuery, true))
                }
                val filteredGroups = if (filterMode == "DIRECT") emptyList() else groups.filter { grp ->
                    searchQuery.isBlank() || grp.name.contains(searchQuery, true) || grp.lastMessage.contains(searchQuery, true)
                }

                if (filteredChats.isEmpty() && filteredGroups.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Chat,
                        title = "No conversations yet",
                        subtitle = "Start a 1-on-1 chat or create a group to message friends in real time.",
                        actionLabel = "Start a Chat",
                        onAction = { showNewChatDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (filteredGroups.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Groups",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(filteredGroups, key = { "grp_${it.id}" }) { group ->
                                GroupRowItem(
                                    group = group,
                                    onClick = { onOpenGroupChat(group.id, group.name) }
                                )
                            }
                        }
                        if (filteredChats.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Direct Messages",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(filteredChats, key = { "chat_${it.id}" }) { chat ->
                                val otherId = chat.participantIds.firstOrNull { it != currentUserId }.orEmpty()
                                val otherUser = usersMap[otherId]
                                if (otherId.isNotBlank() && !targetPrivacyMap.containsKey(otherId)) {
                                    onFetchTargetPrivacy(otherId)
                                }
                                val peerPriv = targetPrivacyMap[otherId]
                                val friendIds = remember(friendsState, currentUserId) {
                                    (friendsState as? UiState.Success)?.data.orEmpty()
                                        .flatMap { it.participantIds }
                                        .filter { it != currentUserId }
                                        .toSet()
                                }
                                val isFriend = friendIds.contains(otherId)
                                val canSeeOnline = when (PrivacyLevel.fromRaw(peerPriv?.onlineStatusPrivacy)) {
                                    PrivacyLevel.EVERYONE -> true
                                    PrivacyLevel.FRIENDS -> isFriend
                                    PrivacyLevel.NOBODY -> false
                                }
                                val canSeePhoto = when (PrivacyLevel.fromRaw(peerPriv?.profilePhotoPrivacy)) {
                                    PrivacyLevel.EVERYONE -> true
                                    PrivacyLevel.FRIENDS -> isFriend
                                    PrivacyLevel.NOBODY -> false
                                }
                                val displayName = otherUser?.displayName ?: chat.participantNames[otherId] ?: "Abhi User"
                                val avatarUrl = if (canSeePhoto) otherUser?.avatarUrl.orEmpty() else ""
                                val online = canSeeOnline && otherUser?.online == true
                                val hasStory = activeStoriesByUser.contains(otherId) || otherUser?.hasActiveStory == true
                                DirectChatRowItem(
                                    chat = chat,
                                    title = displayName,
                                    avatarUrl = avatarUrl,
                                    online = online,
                                    hasStory = hasStory,
                                    onClick = { onOpenDirectChat(chat.id, displayName, otherId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatDialog) {
        NewDirectChatDialog(
            users = users.filter { it.userId != currentUserId && !blockedIds.contains(it.userId) },
            onDismiss = { showNewChatDialog = false },
            onSelectUser = { user ->
                showNewChatDialog = false
                onStartDirectChatWithUser(user)
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            availableUsers = users.filter { it.userId != currentUserId && !blockedIds.contains(it.userId) },
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, desc, photoData, selected ->
                showCreateGroupDialog = false
                onCreateGroup(name, desc, photoData, selected)
            }
        )
    }
}

@Composable
private fun DirectChatRowItem(
    chat: ChatRoom,
    title: String,
    avatarUrl: String,
    online: Boolean,
    hasStory: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_row_${chat.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = title,
                avatarUrl = avatarUrl,
                size = 52.dp,
                showOnlineDot = online,
                hasStoryRing = hasStory
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatRelativeTime(chat.lastMessageAt ?: chat.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = chat.lastMessage.ifBlank { "Tap to message" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GroupRowItem(
    group: GroupChat,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("group_row_${group.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (group.photoData.isNotBlank()) {
                    MediaBitmapOrUrlImage(
                        data = group.photoData,
                        contentDescription = "${group.name} group photo",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatRelativeTime(group.lastMessageAt ?: group.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (group.lastSenderName.isNotBlank()) "${group.lastSenderName}: ${group.lastMessage}" else group.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.memberIds.size} members",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NewDirectChatDialog(
    users: List<UserProfile>,
    onDismiss: () -> Unit,
    onSelectUser: (UserProfile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = users.filter {
        query.isBlank() || it.displayName.contains(query, true) || it.username.contains(query, true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start New Chat") },
        text = {
            Column(modifier = Modifier.height(340.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search users...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No other users discovered yet. Sign in with another Google account to chat!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.userId }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectUser(user) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(user.displayName, user.avatarUrl, size = 40.dp, showOnlineDot = user.online)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun CreateGroupDialog(
    availableUsers: List<UserProfile>,
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, photoData: String, selected: List<UserProfile>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photoData by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                MediaUtils.compressImageToDataUri(context, it)?.let { compressed ->
                    photoData = compressed
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoData.isNotBlank()) {
                            MediaBitmapOrUrlImage(photoData, "Group photo", Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Pick group photo", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Group Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("group_name_input")
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Select Members (${selectedIds.size} selected)", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.height(160.dp)) {
                    items(availableUsers, key = { it.userId }) { user ->
                        val checked = selectedIds.contains(user.userId)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedIds.remove(user.userId) else selectedIds.add(user.userId)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selectedIds.add(user.userId) else selectedIds.remove(user.userId)
                                }
                            )
                            UserAvatar(user.displayName, user.avatarUrl, size = 32.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = availableUsers.filter { selectedIds.contains(it.userId) }
                    onCreate(name, description, photoData, chosen)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_group_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
