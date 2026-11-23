package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BlockedUser
import com.example.data.model.CallType
import com.example.data.model.FriendEdge
import com.example.data.model.FriendRequestItem
import com.example.data.model.PrivacyLevel
import com.example.data.model.PrivacySettings
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ReportUserDialog
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.DangerRed
import com.example.ui.viewmodel.UiState

@Composable
fun FriendsScreen(
    currentUserId: String,
    usersState: UiState<List<UserProfile>>,
    friendsState: UiState<List<FriendEdge>>,
    requestsState: UiState<List<FriendRequestItem>>,
    blockedUsers: List<BlockedUser>,
    targetPrivacyMap: Map<String, PrivacySettings>,
    onFetchTargetPrivacy: (String) -> Unit,
    onSendRequest: (UserProfile) -> Unit,
    onAcceptRequest: (FriendRequestItem) -> Unit,
    onRejectRequest: (String) -> Unit,
    onCancelRequest: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onOpenChat: (UserProfile) -> Unit,
    onStartCall: (UserProfile, CallType) -> Unit,
    onBlockUser: (UserProfile) -> Unit,
    onReportUser: (UserProfile, String, String, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var inspectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var reportingUser by remember { mutableStateOf<UserProfile?>(null) }

    val users = (usersState as? UiState.Success)?.data.orEmpty().filter { it.userId != currentUserId }
    val usersMap = remember(users) { users.associateBy { it.userId } }
    val friends = (friendsState as? UiState.Success)?.data.orEmpty()
    val requests = (requestsState as? UiState.Success)?.data.orEmpty()
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.blockedId }.toSet() }

    val friendEdgesByUserId = remember(friends, currentUserId) {
        friends.mapNotNull { edge ->
            val otherId = edge.participantIds.firstOrNull { it != currentUserId } ?: return@mapNotNull null
            otherId to edge
        }.toMap()
    }
    val incomingPending = requests.filter { it.receiverId == currentUserId && it.status == "PENDING" }
    val outgoingPending = requests.filter { it.senderId == currentUserId && it.status == "PENDING" }

    val filteredUsers = users.filter { u ->
        !blockedIds.contains(u.userId) && (
            searchQuery.isBlank() ||
                u.displayName.contains(searchQuery, true) ||
                u.username.contains(searchQuery, true)
            )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search users by name or @username...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friends_search_input")
            )
        }

        if (incomingPending.isNotEmpty()) {
            item {
                Text(
                    "Pending Friend Requests (${incomingPending.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(incomingPending, key = { "in_${it.id}" }) { req ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(req.senderName, req.senderAvatarUrl, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(req.senderName, style = MaterialTheme.typography.titleSmall)
                            Text("Sent ${formatRelativeTime(req.createdAt)}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = { onAcceptRequest(req) },
                            modifier = Modifier.testTag("accept_friend_req_${req.id}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Accept", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { onRejectRequest(req.id) },
                            modifier = Modifier.testTag("reject_friend_req_${req.id}")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = DangerRed)
                        }
                    }
                }
            }
        }

        if (outgoingPending.isNotEmpty()) {
            item {
                Text(
                    "Sent Requests (${outgoingPending.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(outgoingPending, key = { "out_${it.id}" }) { req ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(req.receiverName, req.receiverAvatarUrl, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(req.receiverName, style = MaterialTheme.typography.titleSmall)
                            Text("Pending approval", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { onCancelRequest(req.id) }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "My Friends (${friendEdgesByUserId.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (friendEdgesByUserId.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.People,
                    title = "No friends added yet",
                    subtitle = "Search people below and send friend requests to connect."
                )
            }
        } else {
            items(friendEdgesByUserId.entries.toList(), key = { "fr_${it.key}" }) { (friendUid, edge) ->
                val user = usersMap[friendUid] ?: UserProfile(
                    id = friendUid,
                    userId = friendUid,
                    displayName = edge.userNames[friendUid] ?: "Friend",
                    avatarUrl = edge.userAvatars[friendUid].orEmpty()
                )
                if (!blockedIds.contains(friendUid)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { inspectedUser = user }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(user.displayName, user.avatarUrl, size = 48.dp, showOnlineDot = user.online)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    user.bio.ifBlank { "@${user.username}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onOpenChat(user) }) {
                                Icon(Icons.Default.Chat, contentDescription = "Message", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onStartCall(user, CallType.VOICE) }) {
                                Icon(Icons.Default.Call, contentDescription = "Voice Call")
                            }
                            IconButton(onClick = { onStartCall(user, CallType.VIDEO) }) {
                                Icon(Icons.Default.Videocam, contentDescription = "Video Call")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Discover People (${filteredUsers.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(filteredUsers, key = { "disc_${it.userId}" }) { user ->
            val isFriend = friendEdgesByUserId.containsKey(user.userId)
            val hasOutgoing = outgoingPending.any { it.receiverId == user.userId }
            val hasIncoming = incomingPending.any { it.senderId == user.userId }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { inspectedUser = user }
                    .testTag("discover_user_${user.userId}")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(user.displayName, user.avatarUrl, size = 46.dp, showOnlineDot = user.online)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        isFriend -> {
                            OutlinedButton(onClick = { onOpenChat(user) }) {
                                Text("Message")
                            }
                        }
                        hasOutgoing -> {
                            Text("Requested", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        hasIncoming -> {
                            Text("Respond above", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                        else -> {
                            Button(
                                onClick = { onSendRequest(user) },
                                modifier = Modifier.testTag("send_friend_request_${user.userId}")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedUser?.let { target ->
        LaunchedEffect(target.userId) {
            onFetchTargetPrivacy(target.userId)
        }
        val privacy = targetPrivacyMap[target.userId]
        val isFriend = friendEdgesByUserId.containsKey(target.userId)
        val friendEdge = friendEdgesByUserId[target.userId]

        fun allowed(levelStr: String?): Boolean {
            return when (PrivacyLevel.fromRaw(levelStr)) {
                PrivacyLevel.EVERYONE -> true
                PrivacyLevel.FRIENDS -> isFriend
                PrivacyLevel.NOBODY -> false
            }
        }

        val showPhoto = allowed(privacy?.profilePhotoPrivacy)
        val showBio = allowed(privacy?.bioPrivacy)
        val showOnline = allowed(privacy?.onlineStatusPrivacy)
        val showLastSeen = allowed(privacy?.lastSeenPrivacy)

        AlertDialog(
            onDismissRequest = { inspectedUser = null },
            title = { Text(target.displayName) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UserAvatar(
                        name = target.displayName,
                        avatarUrl = if (showPhoto) target.avatarUrl else "",
                        size = 72.dp,
                        showOnlineDot = showOnline && target.online
                    )
                    Text("@${target.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (showBio && target.bio.isNotBlank()) {
                        Text(target.bio, style = MaterialTheme.typography.bodyMedium)
                    } else if (!showBio) {
                        Text("Bio hidden by privacy settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showLastSeen) {
                        Text(
                            text = if (showOnline && target.online) "Online now" else "Last seen ${formatRelativeTime(target.lastSeenAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            inspectedUser = null
                            onOpenChat(target)
                        }) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Chat")
                        }
                        if (friendEdge != null) {
                            OutlinedButton(onClick = {
                                onRemoveFriend(friendEdge.id)
                                inspectedUser = null
                            }) {
                                Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Unfriend")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onBlockUser(target)
                                inspectedUser = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Block")
                        }
                        TextButton(
                            onClick = {
                                reportingUser = target
                                inspectedUser = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                        ) {
                            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Report")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedUser = null }) { Text("Close") }
            }
        )
    }

    reportingUser?.let { target ->
        ReportUserDialog(
            targetName = target.displayName,
            onDismiss = { reportingUser = null },
            onSubmit = { reason, details, alsoBlock ->
                onReportUser(target, reason, details, alsoBlock)
                reportingUser = null
            }
        )
    }
}
