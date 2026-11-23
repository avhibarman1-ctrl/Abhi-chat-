package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.GroupChat
import com.example.data.model.GroupMember
import com.example.data.model.IceCandidateRecord
import com.example.data.model.MessageType
import com.example.data.model.PrivacyLevel
import com.example.data.model.PrivacySettings
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatGroupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun deterministicChatId(uid1: String, uid2: String): String {
        val sorted = listOf(uid1, uid2).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    fun observeChats(uid: String): Flow<List<ChatRoom>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("chats")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt?.seconds ?: it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "chats")
                    }
                    throw error
                }
        )
    }

    fun observeGroups(uid: String): Flow<List<GroupChat>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("groups")
                .whereArrayContains("memberIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GroupChat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt?.seconds ?: it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "groups")
                    }
                    throw error
                }
        )
    }

    fun observeGroupMembers(groupId: String, uid: String): Flow<List<GroupMember>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("group_members")
                .whereEqualTo("groupId", groupId)
                .whereArrayContains("visibleToMemberIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GroupMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<GroupMember> { it.role == "ADMIN" }.thenBy { it.displayName.lowercase() })
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "group_members")
                    }
                    throw error
                }
        )
    }

    fun observeMessages(chatId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("messages")
                .whereEqualTo("chatId", chatId)
                .whereArrayContains("memberIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { !it.deletedForUserIds.contains(uid) }
                        .distinctBy { it.id }
                        .sortedWith(
                            compareBy<ChatMessage>(
                                { it.createdAt?.seconds ?: Long.MAX_VALUE },
                                { it.createdAt?.nanoseconds ?: Int.MAX_VALUE },
                                { it.id }
                            )
                        )
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "messages")
                    }
                    throw error
                }
        )
    }

    suspend fun getOrCreateDirectChat(
        currentUser: UserProfile,
        otherUser: UserProfile,
        isFriend: Boolean
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        if (uid == otherUser.userId) return Result.failure(IllegalArgumentException("Cannot message yourself"))
        val chatId = deterministicChatId(uid, otherUser.userId)
        return try {
            val chatRef = db.collection("chats").document(chatId)
            val existing = chatRef.get().await()
            if (existing.exists()) {
                return Result.success(chatId)
            }
            // Respect target's whoCanMessageMe privacy setting
            val privSnap = db.collection("privacy_settings").document(otherUser.userId).get().await()
            val privacy = privSnap.toObject(PrivacySettings::class.java)
            val msgRule = PrivacyLevel.fromRaw(privacy?.whoCanMessageMe)
            if (msgRule == PrivacyLevel.NOBODY) {
                return Result.failure(IllegalStateException("${otherUser.displayName} does not accept new messages."))
            }
            if (msgRule == PrivacyLevel.FRIENDS && !isFriend) {
                return Result.failure(IllegalStateException("Only friends can message ${otherUser.displayName}. Send a friend request first."))
            }

            val participants = listOf(uid, otherUser.userId).sorted()
            val payload = mapOf(
                "participantIds" to participants,
                "participantNames" to mapOf(
                    uid to currentUser.displayName.take(60),
                    otherUser.userId to otherUser.displayName.take(60)
                ),
                "lastMessage" to "Started a conversation",
                "lastMessageType" to MessageType.TEXT.name,
                "lastSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chatRef.set(payload).await()
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/$chatId")
            Result.failure(e)
        }
    }

    suspend fun createGroup(
        name: String,
        description: String,
        photoData: String,
        creator: UserProfile,
        selectedMembers: List<UserProfile>
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Group name cannot be empty" }
            val allMembers = (listOf(creator) + selectedMembers.filter { it.userId != uid })
                .distinctBy { it.userId }
                .take(100)
            val memberIds = allMembers.map { it.userId }
            val groupRef = db.collection("groups").document()
            val groupId = groupRef.id

            val batch = db.batch()
            val groupPayload = mapOf(
                "name" to cleanName,
                "description" to description.trim().take(240),
                "photoData" to photoData.take(350000),
                "createdBy" to uid,
                "adminIds" to listOf(uid),
                "memberIds" to memberIds,
                "lastMessage" to "${creator.displayName} created group \"$cleanName\"",
                "lastSenderName" to creator.displayName.take(60),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(groupRef, groupPayload)

            allMembers.forEach { member ->
                val memberRef = db.collection("group_members").document("${groupId}_${member.userId}")
                batch.set(
                    memberRef,
                    mapOf(
                        "groupId" to groupId,
                        "userId" to member.userId,
                        "displayName" to member.displayName.take(60),
                        "avatarUrl" to member.avatarUrl.take(350000),
                        "role" to if (member.userId == uid) "ADMIN" else "MEMBER",
                        "visibleToMemberIds" to memberIds,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (member.userId != uid) {
                    val notifRef = db.collection("notifications").document()
                    batch.set(
                        notifRef,
                        mapOf(
                            "recipientId" to member.userId,
                            "senderId" to uid,
                            "senderName" to creator.displayName.take(60),
                            "type" to "GROUP_MESSAGE",
                            "title" to "Added to $cleanName",
                            "body" to "${creator.displayName} added you to the group",
                            "targetId" to groupId,
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "groups")
            Result.failure(e)
        }
    }

    suspend fun updateGroupInfo(
        groupId: String,
        name: String,
        description: String,
        photoData: String?
    ): Result<Unit> {
        return try {
            requireUserId()
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Group name cannot be empty" }
            val payload = mutableMapOf<String, Any>(
                "name" to cleanName,
                "description" to description.trim().take(240),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (photoData != null) {
                payload["photoData"] = photoData.take(350000)
            }
            db.collection("groups").document(groupId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "groups/$groupId")
            Result.failure(e)
        }
    }

    suspend fun addGroupMember(group: GroupChat, newMember: UserProfile, actorName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        if (group.memberIds.contains(newMember.userId)) return Result.success(Unit)
        return try {
            val updatedMembers = (group.memberIds + newMember.userId).distinct().take(100)
            val batch = db.batch()
            batch.update(
                db.collection("groups").document(group.id),
                mapOf(
                    "memberIds" to updatedMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            // Update visibility on existing members
            group.memberIds.forEach { existingUid ->
                batch.update(
                    db.collection("group_members").document("${group.id}_$existingUid"),
                    mapOf(
                        "visibleToMemberIds" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            // Add new member record
            batch.set(
                db.collection("group_members").document("${group.id}_${newMember.userId}"),
                mapOf(
                    "groupId" to group.id,
                    "userId" to newMember.userId,
                    "displayName" to newMember.displayName.take(60),
                    "avatarUrl" to newMember.avatarUrl.take(350000),
                    "role" to "MEMBER",
                    "visibleToMemberIds" to updatedMembers,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("notifications").document(),
                mapOf(
                    "recipientId" to newMember.userId,
                    "senderId" to uid,
                    "senderName" to actorName.take(60),
                    "type" to "GROUP_MESSAGE",
                    "title" to "Added to ${group.name}",
                    "body" to "$actorName added you to ${group.name}",
                    "targetId" to group.id,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "groups/${group.id}")
            Result.failure(e)
        }
    }

    suspend fun removeGroupMember(group: GroupChat, targetUserId: String): Result<Unit> {
        return try {
            requireUserId()
            val updatedMembers = group.memberIds.filter { it != targetUserId }
            val updatedAdmins = group.adminIds.filter { it != targetUserId }
            require(updatedMembers.isNotEmpty()) { "Group must keep at least one member" }
            val batch = db.batch()
            batch.update(
                db.collection("groups").document(group.id),
                mapOf(
                    "memberIds" to updatedMembers,
                    "adminIds" to updatedAdmins.ifEmpty { listOf(updatedMembers.first()) },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.delete(db.collection("group_members").document("${group.id}_$targetUserId"))
            updatedMembers.forEach { remainingUid ->
                batch.update(
                    db.collection("group_members").document("${group.id}_$remainingUid"),
                    mapOf(
                        "visibleToMemberIds" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "groups/${group.id}")
            Result.failure(e)
        }
    }

    suspend fun toggleGroupAdmin(group: GroupChat, targetUserId: String, makeAdmin: Boolean): Result<Unit> {
        return try {
            requireUserId()
            val updatedAdmins = if (makeAdmin) {
                (group.adminIds + targetUserId).distinct()
            } else {
                group.adminIds.filter { it != targetUserId }.ifEmpty { listOf(group.createdBy) }
            }
            val batch = db.batch()
            batch.update(
                db.collection("groups").document(group.id),
                mapOf(
                    "adminIds" to updatedAdmins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("group_members").document("${group.id}_$targetUserId"),
                mapOf(
                    "role" to if (updatedAdmins.contains(targetUserId)) "ADMIN" else "MEMBER",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "groups/${group.id}")
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(group: GroupChat): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val remainingMembers = group.memberIds.filter { it != uid }
            if (remainingMembers.isEmpty()) {
                db.collection("groups").document(group.id).delete().await()
                return Result.success(Unit)
            }
            val remainingAdmins = group.adminIds.filter { it != uid }
            val batch = db.batch()
            batch.update(
                db.collection("groups").document(group.id),
                mapOf(
                    "memberIds" to remainingMembers,
                    "adminIds" to remainingAdmins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.delete(db.collection("group_members").document("${group.id}_$uid"))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "groups/${group.id}")
            Result.failure(e)
        }
    }

    // --- MESSAGES (SEND, REPLY, EDIT, DELETE, FORWARD, MEDIA, LIVE LOCATION) ---

    suspend fun sendMessage(
        chatId: String,
        conversationType: String, // "DIRECT" or "GROUP"
        sender: UserProfile,
        recipientId: String,
        memberIds: List<String>,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        mediaData: String = "",
        mediaDurationSec: Long = 0L,
        locationLat: Double? = null,
        locationLng: Double? = null,
        locationLive: Boolean = false,
        locationExpiresAtMillis: Long = 0L,
        replyTo: ChatMessage? = null,
        forwarded: Boolean = false,
        groupName: String = ""
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val msgRef = db.collection("messages").document()
            val cleanText = text.trim().take(4000)
            val previewText = when (messageType) {
                MessageType.IMAGE -> if (cleanText.isBlank()) "Photo" else "Photo: $cleanText"
                MessageType.VIDEO -> if (cleanText.isBlank()) "Video (${mediaDurationSec}s)" else "Video: $cleanText"
                MessageType.LOCATION -> if (locationLive) "Live location shared" else "Location shared"
                else -> cleanText
            }.take(500)

            val nowMs = System.currentTimeMillis()
            val payload = mutableMapOf<String, Any?>(
                "chatId" to chatId,
                "conversationType" to conversationType,
                "senderId" to uid,
                "senderName" to sender.displayName.take(60),
                "recipientId" to recipientId,
                "memberIds" to memberIds.distinct().take(100),
                "text" to cleanText,
                "messageType" to messageType.name,
                "mediaData" to mediaData.take(350000).takeIf { it.isNotEmpty() },
                "mediaDurationSec" to mediaDurationSec.takeIf { messageType == MessageType.VIDEO },
                "locationLat" to locationLat,
                "locationLng" to locationLng,
                "locationLive" to (if (messageType == MessageType.LOCATION) locationLive else null),
                "locationExpiresAtMillis" to (if (messageType == MessageType.LOCATION && locationLive && locationExpiresAtMillis > 0L) locationExpiresAtMillis else null),
                "locationUpdatedAtMillis" to (if (messageType == MessageType.LOCATION) nowMs else null),
                "replyToMessageId" to replyTo?.id?.takeIf { it.isNotBlank() },
                "replyToSenderName" to replyTo?.senderName?.take(60)?.takeIf { it.isNotBlank() },
                "replyToText" to replyTo?.text?.take(300)?.ifBlank { replyTo.messageType }?.takeIf { it.isNotBlank() },
                "forwarded" to forwarded,
                "edited" to false,
                "deletedForEveryone" to false,
                "deletedForUserIds" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val batch = db.batch()
            batch.set(msgRef, payload)

            if (conversationType == "DIRECT") {
                batch.update(
                    db.collection("chats").document(chatId),
                    mapOf(
                        "lastMessage" to previewText,
                        "lastMessageType" to messageType.name,
                        "lastSenderId" to uid,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    db.collection("groups").document(chatId),
                    mapOf(
                        "lastMessage" to previewText,
                        "lastSenderName" to sender.displayName.take(60),
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Reply Notification
            if (replyTo != null && replyTo.senderId != uid) {
                batch.set(
                    db.collection("notifications").document(),
                    mapOf(
                        "recipientId" to replyTo.senderId,
                        "senderId" to uid,
                        "senderName" to sender.displayName.take(60),
                        "type" to "MESSAGE_REPLY",
                        "title" to "${sender.displayName} replied to you",
                        "body" to previewText.take(300),
                        "targetId" to chatId,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            } else if (conversationType == "GROUP") {
                // Group message notification to other members (capped to 15 to keep batch fast)
                memberIds.filter { it != uid }.take(15).forEach { targetMember ->
                    batch.set(
                        db.collection("notifications").document(),
                        mapOf(
                            "recipientId" to targetMember,
                            "senderId" to uid,
                            "senderName" to sender.displayName.take(60),
                            "type" to "GROUP_MESSAGE",
                            "title" to groupName.ifBlank { "Group Chat" }.take(120),
                            "body" to "${sender.displayName}: ${previewText}".take(300),
                            "targetId" to chatId,
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "messages")
            Result.failure(e)
        }
    }

    suspend fun editMessage(messageId: String, newText: String): Result<Unit> {
        return try {
            requireUserId()
            val clean = newText.trim().take(4000)
            require(clean.isNotEmpty()) { "Message cannot be empty" }
            db.collection("messages").document(messageId).update(
                mapOf(
                    "text" to clean,
                    "edited" to true,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "messages/$messageId")
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForMe(message: ChatMessage): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val updated = (message.deletedForUserIds + uid).distinct()
            db.collection("messages").document(message.id).update(
                mapOf(
                    "deletedForUserIds" to updated,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "messages/${message.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForEveryone(messageId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("messages").document(messageId).update(
                mapOf(
                    "text" to "This message was deleted",
                    "mediaData" to "",
                    "deletedForEveryone" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "messages/$messageId")
            Result.failure(e)
        }
    }

    suspend fun updateLiveLocationMessage(
        messageId: String,
        lat: Double,
        lng: Double,
        isLive: Boolean,
        expiresAtMillis: Long? = null
    ): Result<Unit> {
        return try {
            requireUserId()
            val payload = mutableMapOf<String, Any>(
                "locationLat" to lat.coerceIn(-90.0, 90.0),
                "locationLng" to lng.coerceIn(-180.0, 180.0),
                "locationLive" to isLive,
                "locationUpdatedAtMillis" to System.currentTimeMillis(),
                "text" to if (isLive) "Sharing live location" else "Live location ended",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (expiresAtMillis != null && expiresAtMillis > 0L) {
                payload["locationExpiresAtMillis"] = expiresAtMillis
            }
            db.collection("messages").document(messageId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "messages/$messageId")
            Result.failure(e)
        }
    }

    suspend fun forwardMessageToMultipleTargets(
        original: ChatMessage,
        sender: UserProfile,
        targetUsers: List<UserProfile>,
        targetGroups: List<GroupChat>,
        friendUserIds: Set<String>
    ): Result<Int> {
        var successCount = 0
        val msgType = MessageType.fromRaw(original.messageType)
        for (target in targetUsers) {
            val chatRes = getOrCreateDirectChat(
                currentUser = sender,
                otherUser = target,
                isFriend = friendUserIds.contains(target.userId)
            )
            val chatId = chatRes.getOrNull() ?: continue
            val res = sendMessage(
                chatId = chatId,
                conversationType = "DIRECT",
                sender = sender,
                recipientId = target.userId,
                memberIds = listOf(sender.userId, target.userId),
                text = original.text,
                messageType = msgType,
                mediaData = original.mediaData,
                mediaDurationSec = original.mediaDurationSec,
                locationLat = if (msgType == MessageType.LOCATION) original.locationLat else null,
                locationLng = if (msgType == MessageType.LOCATION) original.locationLng else null,
                locationLive = false,
                forwarded = true
            )
            if (res.isSuccess) successCount++
        }
        for (group in targetGroups) {
            val res = sendMessage(
                chatId = group.id,
                conversationType = "GROUP",
                sender = sender,
                recipientId = "",
                memberIds = group.memberIds,
                text = original.text,
                messageType = msgType,
                mediaData = original.mediaData,
                mediaDurationSec = original.mediaDurationSec,
                locationLat = if (msgType == MessageType.LOCATION) original.locationLat else null,
                locationLng = if (msgType == MessageType.LOCATION) original.locationLng else null,
                locationLive = false,
                forwarded = true,
                groupName = group.name
            )
            if (res.isSuccess) successCount++
        }
        return if (successCount > 0) {
            Result.success(successCount)
        } else {
            Result.failure(IllegalStateException("Could not forward message to selected recipients."))
        }
    }

    // --- VOICE & VIDEO CALL SIGNALING ---

    fun observeCalls(uid: String): Flow<List<CallSession>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "calls")
                    }
                    throw error
                }
        )
    }

    suspend fun startCall(
        caller: UserProfile,
        callee: UserProfile,
        callType: CallType
    ): Result<CallSession> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        if (uid == callee.userId) return Result.failure(IllegalArgumentException("Cannot call yourself"))
        return try {
            val callRef = db.collection("calls").document()
            val batch = db.batch()
            batch.set(
                callRef,
                mapOf(
                    "callerId" to uid,
                    "callerName" to caller.displayName.take(60),
                    "callerAvatarUrl" to caller.avatarUrl.take(350000),
                    "calleeId" to callee.userId,
                    "calleeName" to callee.displayName.take(60),
                    "calleeAvatarUrl" to callee.avatarUrl.take(350000),
                    "participantIds" to listOf(uid, callee.userId),
                    "callType" to callType.name,
                    "status" to CallStatus.RINGING.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("notifications").document(),
                mapOf(
                    "recipientId" to callee.userId,
                    "senderId" to uid,
                    "senderName" to caller.displayName.take(60),
                    "type" to "INCOMING_CALL",
                    "title" to "Incoming ${if (callType == CallType.VIDEO) "Video" else "Voice"} Call",
                    "body" to "${caller.displayName} is calling you",
                    "targetId" to callRef.id,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(
                CallSession(
                    id = callRef.id,
                    callerId = uid,
                    callerName = caller.displayName,
                    callerAvatarUrl = caller.avatarUrl,
                    calleeId = callee.userId,
                    calleeName = callee.displayName,
                    calleeAvatarUrl = callee.avatarUrl,
                    participantIds = listOf(uid, callee.userId),
                    callType = callType.name,
                    status = CallStatus.RINGING.name
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "calls")
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(callId: String, status: CallStatus): Result<Unit> {
        return try {
            requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (status == CallStatus.ENDED || status == CallStatus.REJECTED || status == CallStatus.MISSED) {
                updates["endedAt"] = FieldValue.serverTimestamp()
            }
            db.collection("calls").document(callId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    fun observeCall(callId: String, uid: String): Flow<CallSession?> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("calls").document(callId)
                .snapshots()
                .map { snap ->
                    snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.GET, "calls/$callId")
                    }
                    throw error
                }
        )
    }

    suspend fun updateCallOffer(callId: String, sdp: String, type: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "offerSdp" to sdp.take(25000),
                    "offerType" to type.take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun updateCallAnswer(callId: String, sdp: String, type: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "answerSdp" to sdp.take(25000),
                    "answerType" to type.take(32),
                    "status" to CallStatus.ACTIVE.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun updateCallIceState(callId: String, iceState: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "iceState" to iceState.take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        isCaller: Boolean,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val subcollection = if (isCaller) "caller_candidates" else "callee_candidates"
        return try {
            db.collection("calls").document(callId)
                .collection(subcollection)
                .document()
                .set(
                    mapOf(
                        "callId" to callId,
                        "senderId" to uid,
                        "sdpMid" to sdpMid.take(64),
                        "sdpMLineIndex" to sdpMLineIndex,
                        "candidate" to candidate.take(2048),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "calls/$callId/$subcollection")
            Result.failure(e)
        }
    }

    fun observeIceCandidates(callId: String, fromCaller: Boolean, uid: String): Flow<List<IceCandidateRecord>> = flow {
        val current = requireUserId()
        check(current == uid)
        val subcollection = if (fromCaller) "caller_candidates" else "callee_candidates"
        emitAll(
            db.collection("calls").document(callId)
                .collection(subcollection)
                .snapshots()
                .map { snap ->
                    snap.toObjects(IceCandidateRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "calls/$callId/$subcollection")
                    }
                    throw error
                }
        )
    }
}
