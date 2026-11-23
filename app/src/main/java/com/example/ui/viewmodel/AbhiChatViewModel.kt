package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.unwrapFirestoreError
import com.example.data.model.AiReplyCategory
import com.example.data.model.AiReplyLanguage
import com.example.data.model.AppNotification
import com.example.data.model.AudiencePrivacy
import com.example.data.model.BlockedUser
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.EmergencyContact
import com.example.data.model.FriendEdge
import com.example.data.model.FriendRequestItem
import com.example.data.model.GroupChat
import com.example.data.model.GroupMember
import com.example.data.model.IceCandidateRecord
import com.example.data.model.LiveLocationDuration
import com.example.data.model.MessageType
import com.example.data.model.PrivacyLevel
import com.example.data.model.PrivacySettings
import com.example.data.model.ReplySuggestion
import com.example.data.model.ReplySuggestionsUiState
import com.example.data.model.SosAlert
import com.example.data.model.StatusItem
import com.example.data.model.StatusView
import com.example.data.model.StoryItem
import com.example.data.model.StoryView
import com.example.data.model.UserProfile
import com.example.data.repository.ChatGroupRepository
import com.example.data.repository.StatusStoryRepository
import com.example.data.repository.UserPrivacyRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ActiveConversationKey(
    val chatId: String,
    val conversationType: String // "DIRECT" or "GROUP"
)

class AbhiChatViewModel(
    private val userRepo: UserPrivacyRepository,
    private val chatRepo: ChatGroupRepository,
    private val statusStoryRepo: StatusStoryRepository,
    val currentUserId: String
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val _activeConversation = MutableStateFlow<ActiveConversationKey?>(null)
    val activeConversation = _activeConversation.asStateFlow()

    private val _targetUserPrivacy = MutableStateFlow<Map<String, PrivacySettings>>(emptyMap())
    val targetUserPrivacy = _targetUserPrivacy.asStateFlow()

    private val _liveLocationMessageId = MutableStateFlow<String?>(null)
    val liveLocationMessageId = _liveLocationMessageId.asStateFlow()

    private val _liveLocationExpiresAtMillis = MutableStateFlow<Long>(0L)
    val liveLocationExpiresAtMillis = _liveLocationExpiresAtMillis.asStateFlow()

    private val _isBlockedByPeer = MutableStateFlow(false)
    val isBlockedByPeer = _isBlockedByPeer.asStateFlow()

    private val _activeSosAlertId = MutableStateFlow<String?>(null)
    val activeSosAlertId = _activeSosAlertId.asStateFlow()

    private val _selectedAiCategory = MutableStateFlow(AiReplyCategory.FRIENDLY)
    val selectedAiCategory = _selectedAiCategory.asStateFlow()

    private val _selectedAiLanguage = MutableStateFlow(AiReplyLanguage.ALL)
    val selectedAiLanguage = _selectedAiLanguage.asStateFlow()

    private val _replySuggestionsState = MutableStateFlow<ReplySuggestionsUiState>(ReplySuggestionsUiState.Idle)
    val replySuggestionsState = _replySuggestionsState.asStateFlow()

    private val replySuggestionSchema = Schema.obj(
        properties = mapOf(
            "suggestions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "language" to Schema.string(description = "Language name: Bengali, English, or Hindi"),
                        "category" to Schema.string(description = "Tone category matching the requested tone"),
                        "text" to Schema.string(description = "Concise, natural reply message ready for user review")
                    )
                ),
                description = "List of 3 to 5 concise reply suggestions"
            )
        )
    )

    private val replySuggestionsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = replySuggestionSchema
                temperature = 0.75f
            }
        )
    }

    init {
        viewModelScope.launch {
            userRepo.ensureCurrentUserInitialized()
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to initialize profile") }
            statusStoryRepo.cleanupExpiredOwnItems(currentUserId)
        }
    }

    private fun <T> Flow<T>.toUiState(tag: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(tag, "Stream error", root)
                emit(UiState.Error(root.localizedMessage ?: "Sync error"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val myProfile: StateFlow<UiState<UserProfile?>> =
        userRepo.observeMyProfile(currentUserId).toUiState("MyProfileVM")

    val myPrivacySettings: StateFlow<UiState<PrivacySettings?>> =
        userRepo.observeMyPrivacySettings(currentUserId).toUiState("PrivacyVM")

    val discoverableUsers: StateFlow<UiState<List<UserProfile>>> =
        userRepo.observeDiscoverableUsers(currentUserId).toUiState("UsersVM")

    val chats: StateFlow<UiState<List<ChatRoom>>> =
        chatRepo.observeChats(currentUserId).toUiState("ChatsVM")

    val groups: StateFlow<UiState<List<GroupChat>>> =
        chatRepo.observeGroups(currentUserId).toUiState("GroupsVM")

    val friends: StateFlow<UiState<List<FriendEdge>>> =
        userRepo.observeFriends(currentUserId).toUiState("FriendsVM")

    val friendRequests: StateFlow<UiState<List<FriendRequestItem>>> =
        userRepo.observeFriendRequests(currentUserId).toUiState("FriendReqVM")

    val blockedUsers: StateFlow<UiState<List<BlockedUser>>> =
        userRepo.observeBlockedUsers(currentUserId).toUiState("BlockedVM")

    val statuses: StateFlow<UiState<List<StatusItem>>> =
        statusStoryRepo.observeActiveStatuses(currentUserId).toUiState("StatusVM")

    val statusViews: StateFlow<UiState<List<StatusView>>> =
        statusStoryRepo.observeMyStatusViewReceipts(currentUserId).toUiState("StatusViewsVM")

    val stories: StateFlow<UiState<List<StoryItem>>> =
        statusStoryRepo.observeActiveStories(currentUserId).toUiState("StoriesVM")

    val storyViews: StateFlow<UiState<List<StoryView>>> =
        statusStoryRepo.observeStoryViewReceipts(currentUserId).toUiState("StoryViewsVM")

    val notifications: StateFlow<UiState<List<AppNotification>>> =
        userRepo.observeNotifications(currentUserId).toUiState("NotifVM")

    val calls: StateFlow<UiState<List<CallSession>>> =
        chatRepo.observeCalls(currentUserId).toUiState("CallsVM")

    val emergencyContacts: StateFlow<UiState<List<EmergencyContact>>> =
        userRepo.observeEmergencyContacts(currentUserId).toUiState("EmergencyContactsVM")

    val sosAlerts: StateFlow<UiState<List<SosAlert>>> =
        userRepo.observeSosAlerts(currentUserId).toUiState("SosAlertsVM")

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<UiState<List<ChatMessage>>> =
        _activeConversation.flatMapLatest { key ->
            if (key == null) flowOf(emptyList())
            else chatRepo.observeMessages(key.chatId, currentUserId)
        }.toUiState("ActiveMessagesVM")

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeGroupMembers: StateFlow<UiState<List<GroupMember>>> =
        _activeConversation.flatMapLatest { key ->
            if (key == null || key.conversationType != "GROUP") flowOf(emptyList())
            else chatRepo.observeGroupMembers(key.chatId, currentUserId)
        }.toUiState("GroupMembersVM")

    fun openConversation(chatId: String, type: String, peerUserId: String = "") {
        _activeConversation.value = ActiveConversationKey(chatId, type)
        _replySuggestionsState.value = ReplySuggestionsUiState.Idle
        _isBlockedByPeer.value = false
        if (type == "DIRECT" && peerUserId.isNotBlank() && peerUserId != currentUserId) {
            fetchTargetPrivacy(peerUserId)
            viewModelScope.launch {
                _isBlockedByPeer.value = userRepo.isBlockedByTarget(peerUserId)
            }
        }
    }

    fun closeConversation() {
        _activeConversation.value = null
        _isBlockedByPeer.value = false
        _replySuggestionsState.value = ReplySuggestionsUiState.Idle
    }

    fun fetchTargetPrivacy(targetUserId: String) {
        if (targetUserId.isBlank() || _targetUserPrivacy.value.containsKey(targetUserId)) return
        viewModelScope.launch {
            userRepo.getPrivacySettingsForUser(targetUserId)?.let { settings ->
                _targetUserPrivacy.value = _targetUserPrivacy.value + (targetUserId to settings)
            }
        }
    }

    private fun currentUserProfileOrFallback(): UserProfile {
        val state = myProfile.value
        return (state as? UiState.Success)?.data ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            displayName = "Abhi User",
            username = "user_${currentUserId.take(4)}"
        )
    }

    private fun friendIdSet(): Set<String> {
        val list = (friends.value as? UiState.Success)?.data ?: emptyList()
        return list.flatMap { it.participantIds }.filter { it != currentUserId }.toSet()
    }

    private fun emitMessage(msg: String) {
        _events.tryEmit(msg)
    }

    // --- PROFILE & PRIVACY ---

    fun updateProfile(displayName: String, username: String, bio: String, avatarUrl: String?) {
        viewModelScope.launch {
            userRepo.updateProfile(displayName, username, bio, avatarUrl)
                .onSuccess { emitMessage("Profile updated") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to update profile") }
        }
    }

    fun updatePrivacySettings(settings: PrivacySettings) {
        viewModelScope.launch {
            userRepo.updatePrivacySettings(settings)
                .onSuccess { emitMessage("Privacy settings saved") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to save privacy settings") }
        }
    }

    fun setOnlineStatus(online: Boolean) {
        viewModelScope.launch {
            userRepo.setOnlineStatus(online)
        }
    }

    suspend fun prepareSignOut() {
        userRepo.setOnlineStatus(false)
    }

    // --- DIRECT & GROUP CHATS ---

    fun startDirectChat(otherUser: UserProfile, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            val blocked = (blockedUsers.value as? UiState.Success)?.data.orEmpty().any { it.blockedId == otherUser.userId }
            if (blocked) {
                emitMessage("Unblock ${otherUser.displayName} first to send messages.")
                return@launch
            }
            if (userRepo.isBlockedByTarget(otherUser.userId)) {
                emitMessage("You cannot message this user.")
                return@launch
            }
            chatRepo.getOrCreateDirectChat(
                currentUser = currentUserProfileOrFallback(),
                otherUser = otherUser,
                isFriend = friendIdSet().contains(otherUser.userId)
            ).onSuccess { chatId ->
                openConversation(chatId, "DIRECT")
                onOpened(chatId)
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Could not open chat")
            }
        }
    }

    fun createGroup(
        name: String,
        description: String,
        photoData: String,
        selectedMembers: List<UserProfile>,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            chatRepo.createGroup(
                name = name,
                description = description,
                photoData = photoData,
                creator = currentUserProfileOrFallback(),
                selectedMembers = selectedMembers
            ).onSuccess { groupId ->
                emitMessage("Group created")
                openConversation(groupId, "GROUP")
                onCreated(groupId)
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Could not create group")
            }
        }
    }

    fun updateGroupInfo(groupId: String, name: String, description: String, photoData: String?) {
        viewModelScope.launch {
            chatRepo.updateGroupInfo(groupId, name, description, photoData)
                .onSuccess { emitMessage("Group info updated") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to update group") }
        }
    }

    fun addGroupMember(group: GroupChat, user: UserProfile) {
        viewModelScope.launch {
            chatRepo.addGroupMember(group, user, currentUserProfileOrFallback().displayName)
                .onSuccess { emitMessage("${user.displayName} added to group") }
                .onFailure { emitMessage(it.localizedMessage ?: "Could not add member") }
        }
    }

    fun removeGroupMember(group: GroupChat, targetUserId: String) {
        viewModelScope.launch {
            chatRepo.removeGroupMember(group, targetUserId)
                .onSuccess { emitMessage("Member removed") }
                .onFailure { emitMessage(it.localizedMessage ?: "Could not remove member") }
        }
    }

    fun toggleGroupAdmin(group: GroupChat, targetUserId: String, makeAdmin: Boolean) {
        viewModelScope.launch {
            chatRepo.toggleGroupAdmin(group, targetUserId, makeAdmin)
                .onSuccess { emitMessage(if (makeAdmin) "Promoted to Admin" else "Removed Admin role") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to update role") }
        }
    }

    fun leaveGroup(group: GroupChat, onLeft: () -> Unit) {
        viewModelScope.launch {
            chatRepo.leaveGroup(group)
                .onSuccess {
                    emitMessage("Left group")
                    closeConversation()
                    onLeft()
                }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to leave group") }
        }
    }

    // --- MESSAGES (SEND, EDIT, DELETE, REPLY, FORWARD, LIVE LOCATION) ---

    fun sendTextMessage(
        chatId: String,
        conversationType: String,
        recipientId: String,
        memberIds: List<String>,
        text: String,
        replyTo: ChatMessage? = null,
        groupName: String = ""
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            chatRepo.sendMessage(
                chatId = chatId,
                conversationType = conversationType,
                sender = currentUserProfileOrFallback(),
                recipientId = recipientId,
                memberIds = memberIds,
                text = text,
                messageType = MessageType.TEXT,
                replyTo = replyTo,
                groupName = groupName
            ).onFailure { emitMessage(it.localizedMessage ?: "Message failed to send") }
        }
    }

    fun sendMediaMessage(
        chatId: String,
        conversationType: String,
        recipientId: String,
        memberIds: List<String>,
        mediaType: MessageType,
        mediaData: String,
        durationSec: Long,
        caption: String,
        replyTo: ChatMessage? = null,
        groupName: String = ""
    ) {
        viewModelScope.launch {
            chatRepo.sendMessage(
                chatId = chatId,
                conversationType = conversationType,
                sender = currentUserProfileOrFallback(),
                recipientId = recipientId,
                memberIds = memberIds,
                text = caption,
                messageType = mediaType,
                mediaData = mediaData,
                mediaDurationSec = durationSec,
                replyTo = replyTo,
                groupName = groupName
            ).onFailure { emitMessage(it.localizedMessage ?: "Failed to send media") }
        }
    }

    fun sendLocationMessage(
        chatId: String,
        conversationType: String,
        recipientId: String,
        memberIds: List<String>,
        lat: Double,
        lng: Double,
        isLive: Boolean,
        duration: LiveLocationDuration = LiveLocationDuration.MIN_15,
        groupName: String = ""
    ) {
        viewModelScope.launch {
            val expiresAtMillis = if (isLive) System.currentTimeMillis() + duration.millis else 0L
            chatRepo.sendMessage(
                chatId = chatId,
                conversationType = conversationType,
                sender = currentUserProfileOrFallback(),
                recipientId = recipientId,
                memberIds = memberIds,
                text = if (isLive) "Sharing live location (${duration.label})" else "Shared current location",
                messageType = MessageType.LOCATION,
                locationLat = lat,
                locationLng = lng,
                locationLive = isLive,
                locationExpiresAtMillis = expiresAtMillis,
                groupName = groupName
            ).onSuccess { msgId ->
                if (isLive) {
                    _liveLocationMessageId.value = msgId
                    _liveLocationExpiresAtMillis.value = expiresAtMillis
                    emitMessage("Live location sharing started (${duration.label})")
                } else {
                    emitMessage("Location shared")
                }
            }.onFailure { emitMessage(it.localizedMessage ?: "Failed to share location") }
        }
    }

    fun updateLiveLocationCoordinates(messageId: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            val expires = _liveLocationExpiresAtMillis.value.takeIf { it > 0L }
            chatRepo.updateLiveLocationMessage(messageId, lat, lng, isLive = true, expiresAtMillis = expires)
        }
    }

    fun stopLiveLocation(messageId: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            chatRepo.updateLiveLocationMessage(messageId, lat, lng, isLive = false)
                .onSuccess {
                    if (_liveLocationMessageId.value == messageId) {
                        _liveLocationMessageId.value = null
                        _liveLocationExpiresAtMillis.value = 0L
                    }
                    emitMessage("Live location sharing stopped")
                }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            chatRepo.editMessage(messageId, newText)
                .onSuccess { emitMessage("Message edited") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to edit message") }
        }
    }

    fun deleteMessageForMe(message: ChatMessage) {
        viewModelScope.launch {
            chatRepo.deleteMessageForMe(message)
                .onSuccess { emitMessage("Deleted for you") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to delete message") }
        }
    }

    fun deleteMessageForEveryone(messageId: String) {
        viewModelScope.launch {
            chatRepo.deleteMessageForEveryone(messageId)
                .onSuccess { emitMessage("Deleted for everyone") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to delete message") }
        }
    }

    fun forwardMessage(
        original: ChatMessage,
        targetUsers: List<UserProfile>,
        targetGroups: List<GroupChat>
    ) {
        viewModelScope.launch {
            chatRepo.forwardMessageToMultipleTargets(
                original = original,
                sender = currentUserProfileOrFallback(),
                targetUsers = targetUsers,
                targetGroups = targetGroups,
                friendUserIds = friendIdSet()
            ).onSuccess { count ->
                emitMessage("Forwarded to $count conversation${if (count == 1) "" else "s"}")
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to forward message")
            }
        }
    }

    // --- WHATSAPP STATUSES ---

    fun postStatus(
        mediaType: String,
        text: String,
        bgHex: String,
        mediaData: String,
        privacy: AudiencePrivacy
    ) {
        viewModelScope.launch {
            statusStoryRepo.postStatus(
                user = currentUserProfileOrFallback(),
                mediaType = mediaType,
                text = text,
                backgroundColorHex = bgHex,
                mediaData = mediaData,
                privacy = privacy,
                friendIds = friendIdSet().toList()
            ).onSuccess { emitMessage("Status posted (expires in 24h)") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to post status") }
        }
    }

    fun markStatusViewed(status: StatusItem) {
        viewModelScope.launch {
            statusStoryRepo.markStatusViewed(status, currentUserProfileOrFallback())
        }
    }

    fun replyToStatus(status: StatusItem, replyText: String, onOpenChat: (String) -> Unit) {
        if (replyText.isBlank()) return
        viewModelScope.launch {
            val owner = (discoverableUsers.value as? UiState.Success)?.data
                ?.firstOrNull { it.userId == status.userId }
                ?: UserProfile(id = status.userId, userId = status.userId, displayName = status.userName, avatarUrl = status.userAvatarUrl)
            chatRepo.getOrCreateDirectChat(
                currentUser = currentUserProfileOrFallback(),
                otherUser = owner,
                isFriend = friendIdSet().contains(owner.userId)
            ).onSuccess { chatId ->
                chatRepo.sendMessage(
                    chatId = chatId,
                    conversationType = "DIRECT",
                    sender = currentUserProfileOrFallback(),
                    recipientId = owner.userId,
                    memberIds = listOf(currentUserId, owner.userId),
                    text = "Status reply (${status.text.take(40)}): $replyText",
                    messageType = MessageType.TEXT
                )
                emitMessage("Status reply sent")
                onOpenChat(chatId)
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Could not reply to status")
            }
        }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            statusStoryRepo.deleteStatus(statusId)
                .onSuccess { emitMessage("Status deleted") }
        }
    }

    // --- FACEBOOK STORIES ---

    fun postStory(
        mediaType: String,
        caption: String,
        overlayText: String,
        bgHex: String,
        mediaData: String,
        privacy: AudiencePrivacy
    ) {
        viewModelScope.launch {
            statusStoryRepo.postStory(
                user = currentUserProfileOrFallback(),
                mediaType = mediaType,
                caption = caption,
                overlayText = overlayText,
                backgroundColorHex = bgHex,
                mediaData = mediaData,
                privacy = privacy,
                friendIds = friendIdSet().toList()
            ).onSuccess { emitMessage("Story shared (24h)") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to share story") }
        }
    }

    fun markStoryViewed(story: StoryItem) {
        viewModelScope.launch {
            statusStoryRepo.markStoryViewed(story, currentUserProfileOrFallback())
        }
    }

    fun reactToStory(story: StoryItem, emoji: String) {
        viewModelScope.launch {
            statusStoryRepo.reactToStory(story, currentUserProfileOrFallback(), emoji)
                .onFailure { emitMessage(it.localizedMessage ?: "Could not react") }
        }
    }

    fun replyToStory(story: StoryItem, replyText: String) {
        viewModelScope.launch {
            statusStoryRepo.replyToStory(story, currentUserProfileOrFallback(), replyText)
                .onSuccess { emitMessage("Story reply sent") }
                .onFailure { emitMessage(it.localizedMessage ?: "Could not reply to story") }
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            statusStoryRepo.deleteStory(storyId)
                .onSuccess { emitMessage("Story deleted") }
        }
    }

    // --- FRIEND REQUESTS, BLOCK & REPORT ---

    fun sendFriendRequest(receiver: UserProfile) {
        viewModelScope.launch {
            userRepo.sendFriendRequest(currentUserProfileOrFallback(), receiver)
                .onSuccess { emitMessage("Friend request sent to ${receiver.displayName}") }
                .onFailure { emitMessage(it.localizedMessage ?: "Could not send request") }
        }
    }

    fun acceptFriendRequest(request: FriendRequestItem) {
        viewModelScope.launch {
            userRepo.acceptFriendRequest(request)
                .onSuccess { emitMessage("You are now friends with ${request.senderName}") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to accept request") }
        }
    }

    fun rejectFriendRequest(requestId: String) {
        viewModelScope.launch {
            userRepo.rejectFriendRequest(requestId)
                .onSuccess { emitMessage("Friend request declined") }
        }
    }

    fun cancelFriendRequest(requestId: String) {
        viewModelScope.launch {
            userRepo.cancelFriendRequest(requestId)
                .onSuccess { emitMessage("Friend request cancelled") }
        }
    }

    fun removeFriend(friendEdgeId: String) {
        viewModelScope.launch {
            userRepo.removeFriend(friendEdgeId)
                .onSuccess { emitMessage("Friend removed") }
        }
    }

    fun blockUser(target: UserProfile, reason: String = "Blocked by user") {
        viewModelScope.launch {
            userRepo.blockUser(target, reason)
                .onSuccess { emitMessage("${target.displayName} has been blocked") }
                .onFailure { emitMessage(it.localizedMessage ?: "Could not block user") }
        }
    }

    fun unblockUser(targetUserId: String) {
        viewModelScope.launch {
            userRepo.unblockUser(targetUserId)
                .onSuccess { emitMessage("User unblocked") }
        }
    }

    fun reportUser(target: UserProfile, reason: String, details: String, alsoBlock: Boolean) {
        viewModelScope.launch {
            userRepo.reportUser(target, reason, details)
                .onSuccess {
                    if (alsoBlock) {
                        userRepo.blockUser(target, reason)
                    }
                    emitMessage("Report submitted securely")
                }
                .onFailure { emitMessage(it.localizedMessage ?: "Could not submit report") }
        }
    }

    // --- NOTIFICATIONS ---

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            userRepo.markNotificationRead(notificationId)
        }
    }

    fun markAllNotificationsRead(list: List<AppNotification>) {
        viewModelScope.launch {
            userRepo.markAllNotificationsRead(list)
                .onSuccess { emitMessage("All notifications marked as read") }
        }
    }

    // --- VOICE & VIDEO CALLS ---

    fun startCall(callee: UserProfile, callType: CallType) {
        viewModelScope.launch {
            val blocked = (blockedUsers.value as? UiState.Success)?.data.orEmpty().any { it.blockedId == callee.userId }
            if (blocked || userRepo.isBlockedByTarget(callee.userId)) {
                emitMessage("Calls are blocked between you and ${callee.displayName}.")
                return@launch
            }
            chatRepo.startCall(currentUserProfileOrFallback(), callee, callType)
                .onFailure { emitMessage(it.localizedMessage ?: "Could not place call") }
        }
    }

    fun acceptCall(callId: String) {
        viewModelScope.launch {
            chatRepo.updateCallStatus(callId, CallStatus.ACTIVE)
        }
    }

    fun endCall(callId: String, rejected: Boolean = false) {
        viewModelScope.launch {
            _activeSosAlertId.value?.let { alertId ->
                userRepo.deactivateSosAlert(alertId)
                _activeSosAlertId.value = null
            }
            chatRepo.updateCallStatus(callId, if (rejected) CallStatus.REJECTED else CallStatus.ENDED)
        }
    }

    fun observeCallSession(callId: String): Flow<CallSession?> =
        chatRepo.observeCall(callId, currentUserId)

    fun observeRemoteIceCandidates(callId: String, fromCaller: Boolean): Flow<List<IceCandidateRecord>> =
        chatRepo.observeIceCandidates(callId, fromCaller, currentUserId)

    fun saveCallOffer(callId: String, sdp: String, type: String) {
        viewModelScope.launch {
            chatRepo.updateCallOffer(callId, sdp, type)
        }
    }

    fun saveCallAnswer(callId: String, sdp: String, type: String) {
        viewModelScope.launch {
            chatRepo.updateCallAnswer(callId, sdp, type)
        }
    }

    fun saveIceCandidate(
        callId: String,
        isCaller: Boolean,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        viewModelScope.launch {
            chatRepo.addIceCandidate(callId, isCaller, sdpMid, sdpMLineIndex, candidate)
        }
    }

    fun updateCallIceState(callId: String, iceState: String) {
        viewModelScope.launch {
            chatRepo.updateCallIceState(callId, iceState)
        }
    }

    // --- EMERGENCY CONTACTS & IN-CALL SOS LIVE LOCATION ---

    fun addEmergencyContact(name: String, phoneNumber: String, relationship: String) {
        viewModelScope.launch {
            userRepo.addEmergencyContact(name, phoneNumber, relationship)
                .onSuccess { emitMessage("Emergency contact saved") }
                .onFailure { emitMessage(it.localizedMessage ?: "Could not save emergency contact") }
        }
    }

    fun deleteEmergencyContact(contactId: String) {
        viewModelScope.launch {
            userRepo.deleteEmergencyContact(contactId)
                .onSuccess { emitMessage("Emergency contact removed") }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to remove emergency contact") }
        }
    }

    fun activateInCallSos(
        callId: String,
        latitude: Double,
        longitude: Double,
        recipientPhones: List<String>,
        onActivated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val existingAlertId = _activeSosAlertId.value
            if (existingAlertId != null) {
                userRepo.updateSosAlertLocation(existingAlertId, latitude, longitude)
                onActivated(existingAlertId)
                emitMessage("SOS live location updated")
                return@launch
            }
            userRepo.createSosAlert(
                userName = currentUserProfileOrFallback().displayName,
                callId = callId,
                latitude = latitude,
                longitude = longitude,
                recipientPhones = recipientPhones
            ).onSuccess { alertId ->
                _activeSosAlertId.value = alertId
                onActivated(alertId)
                emitMessage("SOS activated: live location syncing")
            }.onFailure {
                emitMessage(it.localizedMessage ?: "Failed to sync SOS location")
            }
        }
    }

    fun updateSosLocation(alertId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            userRepo.updateSosAlertLocation(alertId, latitude, longitude)
        }
    }

    fun stopSosAlert(alertId: String? = _activeSosAlertId.value) {
        val targetId = alertId ?: return
        viewModelScope.launch {
            userRepo.deactivateSosAlert(targetId)
                .onSuccess {
                    if (_activeSosAlertId.value == targetId) _activeSosAlertId.value = null
                    emitMessage("SOS live location stopped")
                }
                .onFailure { emitMessage(it.localizedMessage ?: "Failed to stop SOS") }
        }
    }

    // --- ABHI AI SMART REPLY ASSISTANT (BENGALI / ENGLISH / HINDI) ---

    private fun sanitizeForAi(raw: String): String {
        return raw
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email]")
            .replace(Regex("\\b\\+?\\d[\\d\\s\\-()]{6,}\\d\\b"), "[phone]")
            .replace(Regex("-?\\d{1,3}\\.\\d{4,},\\s*-?\\d{1,3}\\.\\d{4,}"), "[location]")
            .take(280)
    }

    fun selectAiCategory(category: AiReplyCategory, recentMessages: List<ChatMessage>) {
        _selectedAiCategory.value = category
        generateAbhiAiReplies(recentMessages, category, _selectedAiLanguage.value)
    }

    fun selectAiLanguage(language: AiReplyLanguage, recentMessages: List<ChatMessage>) {
        _selectedAiLanguage.value = language
        generateAbhiAiReplies(recentMessages, _selectedAiCategory.value, language)
    }

    fun generateAbhiAiReplies(
        recentMessages: List<ChatMessage>,
        category: AiReplyCategory = _selectedAiCategory.value,
        language: AiReplyLanguage = _selectedAiLanguage.value,
        customInstruction: String = ""
    ) {
        _selectedAiCategory.value = category
        _selectedAiLanguage.value = language
        viewModelScope.launch {
            _replySuggestionsState.value = ReplySuggestionsUiState.Loading
            try {
                val contextLines = recentMessages
                    .filter { !it.deletedForEveryone && it.text.isNotBlank() }
                    .takeLast(8)
                    .joinToString("\n") { msg ->
                        val role = if (msg.senderId == currentUserId) "Me" else "Peer"
                        "$role: ${sanitizeForAi(msg.text)}"
                    }

                val languageDirective = if (language == AiReplyLanguage.ALL) {
                    "Generate 4 distinct natural replies covering Bengali (বাংলা), English, and Hindi (हिंदी) in the requested tone. Label each language accurately as Bengali, English, or Hindi."
                } else {
                    "Generate 4 distinct natural replies all written in ${language.label} using authentic script/expression appropriate for ${language.label} chat."
                }

                val prompt = buildString {
                    appendLine("You are ABHI AI, an assistive smart-reply drafting assistant inside ABHI CHAT.")
                    appendLine("Target reply category/tone: ${category.label} (${category.toneHint}).")
                    appendLine(languageDirective)
                    appendLine("Provide between 3 and 5 concise suggestions, each under 160 characters, with no markdown.")
                    appendLine("IMPORTANT: Draft replies only for user review — never include instructions to send automatically.")
                    if (customInstruction.isNotBlank()) {
                        appendLine("Additional user direction: ${customInstruction.take(160)}")
                    }
                    if (contextLines.isNotBlank()) {
                        appendLine("Recent conversation context (privacy-redacted):")
                        appendLine(contextLines)
                    } else {
                        appendLine("No messages yet. Draft warm, ${category.label.lowercase()} conversation starters.")
                    }
                }

                val response = withTimeout(45_000L) {
                    replySuggestionsModel.generateContent(prompt)
                }
                val jsonString = response.text
                    ?: throw IllegalStateException("ABHI AI returned an empty response.")

                val json = JSONObject(jsonString)
                val array = json.optJSONArray("suggestions")
                val parsed = buildList {
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val obj = array.optJSONObject(i) ?: continue
                            val lang = obj.optString("language").ifBlank { language.label }.trim()
                            val cat = obj.optString("category").ifBlank { category.label }.trim()
                            val text = obj.optString("text").trim()
                            if (text.isNotEmpty()) {
                                add(ReplySuggestion(language = lang, category = cat, text = text))
                            }
                        }
                    }
                }.take(5)

                if (parsed.isNotEmpty()) {
                    _replySuggestionsState.value = ReplySuggestionsUiState.Success(
                        suggestions = parsed,
                        languageMode = language.label,
                        category = category
                    )
                } else {
                    _replySuggestionsState.value = ReplySuggestionsUiState.Error(
                        "No reply suggestions were returned. Tap Retry to regenerate."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("AbhiChatVM", "ABHI AI timed out", e)
                _replySuggestionsState.value = ReplySuggestionsUiState.Error(
                    "ABHI AI timed out. Please tap Retry."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AbhiChatVM", "ABHI AI reply generation failed", e)
                _replySuggestionsState.value = ReplySuggestionsUiState.Error(
                    e.localizedMessage ?: "Failed to generate reply suggestions."
                )
            }
        }
    }

    fun editAiSuggestion(suggestionId: String, newText: String) {
        val current = _replySuggestionsState.value as? ReplySuggestionsUiState.Success ?: return
        val clean = newText.trim().take(500)
        if (clean.isEmpty()) return
        val updated = current.suggestions.map {
            if (it.id == suggestionId) it.copy(text = clean) else it
        }
        _replySuggestionsState.value = current.copy(suggestions = updated)
    }

    fun deleteAiSuggestion(suggestionId: String) {
        val current = _replySuggestionsState.value as? ReplySuggestionsUiState.Success ?: return
        _replySuggestionsState.value = current.copy(
            suggestions = current.suggestions.filterNot { it.id == suggestionId }
        )
    }

    fun clearReplySuggestions() {
        _replySuggestionsState.value = ReplySuggestionsUiState.Idle
    }

    // --- PRIVACY FILTER HELPERS ---

    fun canViewField(targetUserId: String, privacyField: String): Boolean {
        if (targetUserId == currentUserId) return true
        val level = PrivacyLevel.fromRaw(privacyField)
        return when (level) {
            PrivacyLevel.EVERYONE -> true
            PrivacyLevel.FRIENDS -> friendIdSet().contains(targetUserId)
            PrivacyLevel.NOBODY -> false
        }
    }
}
