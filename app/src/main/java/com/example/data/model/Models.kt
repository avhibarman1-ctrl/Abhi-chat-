package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PrivacyLevel(val label: String) {
    EVERYONE("Everyone"),
    FRIENDS("Friends"),
    NOBODY("Nobody");

    companion object {
        fun fromRaw(raw: String?): PrivacyLevel =
            entries.firstOrNull { it.name == raw } ?: EVERYONE
    }
}

enum class FriendRequestPrivacy(val label: String) {
    EVERYONE("Everyone"),
    FRIENDS_OF_FRIENDS("Friends of Friends"),
    NOBODY("Nobody");

    companion object {
        fun fromRaw(raw: String?): FriendRequestPrivacy =
            entries.firstOrNull { it.name == raw } ?: EVERYONE
    }
}

enum class AudiencePrivacy(val label: String) {
    EVERYONE("Everyone"),
    FRIENDS("Friends Only"),
    ONLY_ME("Only Me");

    companion object {
        fun fromRaw(raw: String?): AudiencePrivacy =
            entries.firstOrNull { it.name == raw } ?: EVERYONE
    }
}

enum class MessageType {
    TEXT, IMAGE, VIDEO, LOCATION, CALL_LOG;

    companion object {
        fun fromRaw(raw: String?): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class CallType {
    VOICE, VIDEO;

    companion object {
        fun fromRaw(raw: String?): CallType =
            entries.firstOrNull { it.name == raw } ?: VOICE
    }
}

enum class CallStatus {
    RINGING, ACTIVE, ENDED, REJECTED, MISSED;

    companion object {
        fun fromRaw(raw: String?): CallStatus =
            entries.firstOrNull { it.name == raw } ?: ENDED
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val online: Boolean = false,
    val profileVisibility: String = PrivacyLevel.EVERYONE.name,
    val hasActiveStory: Boolean = false,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrivacySettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileVisibility: String = PrivacyLevel.EVERYONE.name,
    val lastSeenPrivacy: String = PrivacyLevel.EVERYONE.name,
    val onlineStatusPrivacy: String = PrivacyLevel.EVERYONE.name,
    val profilePhotoPrivacy: String = PrivacyLevel.EVERYONE.name,
    val bioPrivacy: String = PrivacyLevel.EVERYONE.name,
    val statusPrivacy: String = PrivacyLevel.FRIENDS.name,
    val storyPrivacy: String = PrivacyLevel.EVERYONE.name,
    val whoCanMessageMe: String = PrivacyLevel.EVERYONE.name,
    val whoCanSendFriendRequests: String = FriendRequestPrivacy.EVERYONE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageType: String = MessageType.TEXT.name,
    val lastSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupChat(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val photoData: String = "",
    val createdBy: String = "",
    val adminIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastSenderName: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GroupMember(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val role: String = "MEMBER",
    val visibleToMemberIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val conversationType: String = "DIRECT",
    val senderId: String = "",
    val senderName: String = "",
    val recipientId: String = "",
    val memberIds: List<String> = emptyList(),
    val text: String = "",
    val messageType: String = MessageType.TEXT.name,
    val mediaData: String = "",
    val mediaDurationSec: Long = 0L,
    val locationLat: Double = 0.0,
    val locationLng: Double = 0.0,
    val locationLive: Boolean = false,
    val locationExpiresAtMillis: Long = 0L,
    val locationUpdatedAtMillis: Long = 0L,
    val replyToMessageId: String = "",
    val replyToSenderName: String = "",
    val replyToText: String = "",
    val forwarded: Boolean = false,
    val edited: Boolean = false,
    val editedAt: Timestamp? = null,
    val deletedForEveryone: Boolean = false,
    val deletedForUserIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StatusItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatarUrl: String = "",
    val mediaType: String = "TEXT",
    val text: String = "",
    val backgroundColorHex: String = "#0F766E",
    val mediaData: String = "",
    val privacy: String = AudiencePrivacy.EVERYONE.name,
    val allowedViewerIds: List<String> = emptyList(),
    val viewerCount: Long = 0L,
    val expiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StatusView(
    @DocumentId val id: String = "",
    val statusId: String = "",
    val statusOwnerId: String = "",
    val viewerId: String = "",
    val viewerName: String = "",
    val viewerAvatarUrl: String = "",
    @ServerTimestamp val viewedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatarUrl: String = "",
    val mediaType: String = "TEXT",
    val caption: String = "",
    val overlayText: String = "",
    val backgroundColorHex: String = "#1D4ED8",
    val mediaData: String = "",
    val privacy: String = AudiencePrivacy.EVERYONE.name,
    val allowedViewerIds: List<String> = emptyList(),
    val reactionCounts: Map<String, Long> = emptyMap(),
    val userReactions: Map<String, String> = emptyMap(),
    val viewerCount: Long = 0L,
    val replyCount: Long = 0L,
    val expiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryView(
    @DocumentId val id: String = "",
    val storyId: String = "",
    val storyOwnerId: String = "",
    val viewerId: String = "",
    val viewerName: String = "",
    val viewerAvatarUrl: String = "",
    val reaction: String = "",
    val replyText: String = "",
    @ServerTimestamp val viewedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendRequestItem(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatarUrl: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val receiverAvatarUrl: String = "",
    val status: String = "PENDING",
    val participantIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendEdge(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val userNames: Map<String, String> = emptyMap(),
    val userAvatars: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val blockerId: String = "",
    val blockedId: String = "",
    val blockedName: String = "",
    val blockedAvatarUrl: String = "",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val reportedUserName: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "SUBMITTED",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = "MESSAGE_REPLY",
    val title: String = "",
    val body: String = "",
    val targetId: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerAvatarUrl: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val calleeAvatarUrl: String = "",
    val participantIds: List<String> = emptyList(),
    val callType: String = CallType.VOICE.name,
    val status: String = CallStatus.RINGING.name,
    val offerSdp: String = "",
    val offerType: String = "",
    val answerSdp: String = "",
    val answerType: String = "",
    val iceState: String = "NEW",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val endedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IceCandidateRecord(
    @DocumentId val id: String = "",
    val callId: String = "",
    val senderId: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EmergencyContact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SosAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val callId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val active: Boolean = true,
    val recipientPhones: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class AiReplyCategory(val label: String, val toneHint: String) {
    FRIENDLY("Friendly", "warm, friendly, cheerful, and supportive"),
    ROMANTIC("Romantic", "affectionate, caring, sweet, and romantic"),
    FUNNY("Funny", "playful, witty, light-hearted, and humorous"),
    ATTITUDE("Attitude", "bold, confident, sharp, and expressive"),
    PROFESSIONAL("Professional", "polite, clear, respectful, and business-appropriate"),
    NORMAL("Normal", "natural, casual, and everyday")
}

enum class AiReplyLanguage(val label: String) {
    BENGALI("Bengali"),
    ENGLISH("English"),
    HINDI("Hindi"),
    ALL("All (BN/EN/HI)")
}

enum class LiveLocationDuration(val label: String, val millis: Long) {
    MIN_15("15 mins", 15L * 60L * 1000L),
    HOUR_1("1 hour", 60L * 60L * 1000L),
    HOURS_8("8 hours", 8L * 60L * 60L * 1000L)
}

data class ReplySuggestion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val language: String,
    val category: String = AiReplyCategory.FRIENDLY.label,
    val text: String
)

sealed interface ReplySuggestionsUiState {
    data object Idle : ReplySuggestionsUiState
    data object Loading : ReplySuggestionsUiState
    data class Success(
        val suggestions: List<ReplySuggestion>,
        val languageMode: String,
        val category: AiReplyCategory = AiReplyCategory.FRIENDLY
    ) : ReplySuggestionsUiState
    data class Error(val message: String) : ReplySuggestionsUiState
}

