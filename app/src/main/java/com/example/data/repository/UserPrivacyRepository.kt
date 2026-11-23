package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AppNotification
import com.example.data.model.BlockedUser
import com.example.data.model.EmergencyContact
import com.example.data.model.FriendEdge
import com.example.data.model.FriendRequestItem
import com.example.data.model.FriendRequestPrivacy
import com.example.data.model.PrivacyLevel
import com.example.data.model.PrivacySettings
import com.example.data.model.SosAlert
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserPrivacyRepository(
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

    suspend fun ensureCurrentUserInitialized(): Result<Unit> {
        val currentUser = auth.currentUser ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val uid = currentUser.uid
        return try {
            val userRef = db.collection("users").document(uid)
            val existingUser = userRef.get().await()
            val displayName = currentUser.displayName?.takeIf { it.isNotBlank() }?.take(60)
                ?: currentUser.email?.substringBefore("@")?.take(60)
                ?: "Abhi User"
            val handleBase = (currentUser.email?.substringBefore("@") ?: displayName)
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "")
                .ifBlank { "user" }
                .take(24)
            val username = "${handleBase}_${uid.take(4).lowercase()}".take(40)
            val avatarUrl = currentUser.photoUrl?.toString()?.take(500).orEmpty()

            if (!existingUser.exists()) {
                val userPayload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "username" to username,
                    "bio" to "Hey there! I am using ABHI CHAT.",
                    "avatarUrl" to avatarUrl,
                    "online" to true,
                    "profileVisibility" to PrivacyLevel.EVERYONE.name,
                    "hasActiveStory" to false,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(userPayload).await()
            } else {
                userRef.update(
                    mapOf(
                        "online" to true,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Isolated PII doc (email + FCM token)
            val email = currentUser.email.orEmpty().take(254)
            val fcmToken = try {
                FirebaseMessaging.getInstance().token.await().take(512)
            } catch (_: Exception) {
                ""
            }
            val privatePayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "email" to email,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (fcmToken.isNotBlank()) {
                privatePayload["fcmToken"] = fcmToken
            }
            db.collection("users").document(uid)
                .collection("private").document("info")
                .set(privatePayload).await()

            // Default privacy settings
            val privacyRef = db.collection("privacy_settings").document(uid)
            if (!privacyRef.get().await().exists()) {
                privacyRef.set(
                    mapOf(
                        "userId" to uid,
                        "profileVisibility" to PrivacyLevel.EVERYONE.name,
                        "lastSeenPrivacy" to PrivacyLevel.EVERYONE.name,
                        "onlineStatusPrivacy" to PrivacyLevel.EVERYONE.name,
                        "profilePhotoPrivacy" to PrivacyLevel.EVERYONE.name,
                        "bioPrivacy" to PrivacyLevel.EVERYONE.name,
                        "statusPrivacy" to PrivacyLevel.FRIENDS.name,
                        "storyPrivacy" to PrivacyLevel.EVERYONE.name,
                        "whoCanMessageMe" to PrivacyLevel.EVERYONE.name,
                        "whoCanSendFriendRequests" to FriendRequestPrivacy.EVERYONE.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(online: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "online" to online,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateFcmToken(token: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val clean = token.trim().take(512)
        if (clean.isEmpty()) return Result.success(Unit)
        return try {
            db.collection("users").document(uid)
                .collection("private").document("info")
                .update(
                    mapOf(
                        "fcmToken" to clean,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/private/info")
            Result.failure(e)
        }
    }

    fun observeMyProfile(uid: String): Flow<UserProfile?> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.GET, "users/$uid")
                    }
                    throw error
                }
        )
    }

    fun observeDiscoverableUsers(uid: String): Flow<List<UserProfile>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("users")
                .whereIn("profileVisibility", listOf(PrivacyLevel.EVERYONE.name, PrivacyLevel.FRIENDS.name))
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "users")
                    }
                    throw error
                }
        )
    }

    fun observeMyPrivacySettings(uid: String): Flow<PrivacySettings?> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("privacy_settings").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(PrivacySettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.GET, "privacy_settings/$uid")
                    }
                    throw error
                }
        )
    }

    suspend fun getPrivacySettingsForUser(targetUserId: String): PrivacySettings? {
        return try {
            requireUserId()
            db.collection("privacy_settings").document(targetUserId)
                .get()
                .await()
                .toObject(PrivacySettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "privacy_settings/$targetUserId")
            null
        }
    }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        bio: String,
        avatarUrl: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val cleanName = displayName.trim().take(60)
            val cleanUser = username.trim().lowercase().replace(Regex("[^a-z0-9_.]"), "").take(40)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty" }
            require(cleanUser.length >= 2) { "Username must be at least 2 characters" }
            val payload = mutableMapOf<String, Any>(
                "displayName" to cleanName,
                "username" to cleanUser,
                "bio" to bio.trim().take(200),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarUrl != null) {
                payload["avatarUrl"] = avatarUrl.take(350000)
            }
            db.collection("users").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val batch = db.batch()
            val privRef = db.collection("privacy_settings").document(uid)
            batch.update(
                privRef,
                mapOf(
                    "profileVisibility" to settings.profileVisibility,
                    "lastSeenPrivacy" to settings.lastSeenPrivacy,
                    "onlineStatusPrivacy" to settings.onlineStatusPrivacy,
                    "profilePhotoPrivacy" to settings.profilePhotoPrivacy,
                    "bioPrivacy" to settings.bioPrivacy,
                    "statusPrivacy" to settings.statusPrivacy,
                    "storyPrivacy" to settings.storyPrivacy,
                    "whoCanMessageMe" to settings.whoCanMessageMe,
                    "whoCanSendFriendRequests" to settings.whoCanSendFriendRequests,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "profileVisibility" to settings.profileVisibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "privacy_settings/$uid")
            Result.failure(e)
        }
    }

    // --- FRIEND REQUESTS & FRIENDS ---

    fun observeFriendRequests(uid: String): Flow<List<FriendRequestItem>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("friend_requests")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FriendRequestItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "friend_requests")
                    }
                    throw error
                }
        )
    }

    fun observeFriends(uid: String): Flow<List<FriendEdge>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("friends")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FriendEdge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "friends")
                    }
                    throw error
                }
        )
    }

    suspend fun sendFriendRequest(sender: UserProfile, receiver: UserProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        if (uid == receiver.userId) return Result.failure(IllegalArgumentException("Cannot add yourself"))
        return try {
            val receiverPrivacy = getPrivacySettingsForUser(receiver.userId)
            if (receiverPrivacy?.whoCanSendFriendRequests == FriendRequestPrivacy.NOBODY.name) {
                return Result.failure(IllegalStateException("${receiver.displayName} is not accepting friend requests."))
            }
            val reqId = "${uid}_${receiver.userId}"
            val reqRef = db.collection("friend_requests").document(reqId)
            val existing = reqRef.get().await()
            val batch = db.batch()
            if (existing.exists()) {
                batch.update(
                    reqRef,
                    mapOf(
                        "status" to "PENDING",
                        "senderName" to sender.displayName.take(60),
                        "senderAvatarUrl" to sender.avatarUrl.take(350000),
                        "receiverName" to receiver.displayName.take(60),
                        "receiverAvatarUrl" to receiver.avatarUrl.take(350000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    reqRef,
                    mapOf(
                        "senderId" to uid,
                        "senderName" to sender.displayName.take(60),
                        "senderAvatarUrl" to sender.avatarUrl.take(350000),
                        "receiverId" to receiver.userId,
                        "receiverName" to receiver.displayName.take(60),
                        "receiverAvatarUrl" to receiver.avatarUrl.take(350000),
                        "status" to "PENDING",
                        "participantIds" to listOf(uid, receiver.userId),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val notifRef = db.collection("notifications").document()
            batch.set(
                notifRef,
                mapOf(
                    "recipientId" to receiver.userId,
                    "senderId" to uid,
                    "senderName" to sender.displayName.take(60),
                    "type" to "FRIEND_REQUEST",
                    "title" to "New Friend Request",
                    "body" to "${sender.displayName} sent you a friend request",
                    "targetId" to reqId,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "friend_requests")
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(request: FriendRequestItem): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val pair = listOf(request.senderId, request.receiverId).sorted()
            val friendDocId = "${pair[0]}_${pair[1]}"
            val batch = db.batch()
            batch.update(
                db.collection("friend_requests").document(request.id),
                mapOf(
                    "status" to "ACCEPTED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("friends").document(friendDocId),
                mapOf(
                    "participantIds" to pair,
                    "userNames" to mapOf(
                        request.senderId to request.senderName,
                        request.receiverId to request.receiverName
                    ),
                    "userAvatars" to mapOf(
                        request.senderId to request.senderAvatarUrl,
                        request.receiverId to request.receiverAvatarUrl
                    ),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val notifRef = db.collection("notifications").document()
            batch.set(
                notifRef,
                mapOf(
                    "recipientId" to request.senderId,
                    "senderId" to uid,
                    "senderName" to request.receiverName.take(60),
                    "type" to "FRIEND_ACCEPTED",
                    "title" to "Friend Request Accepted",
                    "body" to "${request.receiverName} accepted your friend request",
                    "targetId" to friendDocId,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "friend_requests/${request.id}")
            Result.failure(e)
        }
    }

    suspend fun rejectFriendRequest(requestId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("friend_requests").document(requestId).update(
                mapOf(
                    "status" to "REJECTED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "friend_requests/$requestId")
            Result.failure(e)
        }
    }

    suspend fun cancelFriendRequest(requestId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("friend_requests").document(requestId).update(
                mapOf(
                    "status" to "CANCELLED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "friend_requests/$requestId")
            Result.failure(e)
        }
    }

    suspend fun removeFriend(friendEdgeId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("friends").document(friendEdgeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "friends/$friendEdgeId")
            Result.failure(e)
        }
    }

    // --- BLOCK & REPORT ---

    fun observeBlockedUsers(uid: String): Flow<List<BlockedUser>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("blocked_users")
                .whereEqualTo("blockerId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(BlockedUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "blocked_users")
                    }
                    throw error
                }
        )
    }

    suspend fun isBlockedByTarget(targetUserId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val doc = db.collection("blocked_users").document("${targetUserId}_${uid}").get().await()
            doc.exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun blockUser(target: UserProfile, reason: String = "Blocked by user"): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        if (uid == target.userId) return Result.failure(IllegalArgumentException("Cannot block yourself"))
        return try {
            val blockId = "${uid}_${target.userId}"
            db.collection("blocked_users").document(blockId).set(
                mapOf(
                    "blockerId" to uid,
                    "blockedId" to target.userId,
                    "blockedName" to target.displayName.take(60),
                    "blockedAvatarUrl" to target.avatarUrl.take(350000),
                    "reason" to reason.take(200),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "blocked_users")
            Result.failure(e)
        }
    }

    suspend fun unblockUser(blockedUserId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val blockId = "${uid}_${blockedUserId}"
        return try {
            db.collection("blocked_users").document(blockId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "blocked_users/$blockId")
            Result.failure(e)
        }
    }

    suspend fun reportUser(target: UserProfile, reason: String, details: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        if (uid == target.userId) return Result.failure(IllegalArgumentException("Cannot report yourself"))
        return try {
            require(reason.trim().length >= 2) { "Please select a report reason" }
            db.collection("reports").document().set(
                mapOf(
                    "reporterId" to uid,
                    "reportedUserId" to target.userId,
                    "reportedUserName" to target.displayName.take(60),
                    "reason" to reason.trim().take(100),
                    "details" to details.trim().take(1000),
                    "status" to "SUBMITTED",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reports")
            Result.failure(e)
        }
    }

    // --- NOTIFICATIONS ---

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("notifications")
                .whereEqualTo("recipientId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "notifications")
                    }
                    throw error
                }
        )
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("notifications").document(notificationId).update("read", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications/$notificationId")
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(notifications: List<AppNotification>): Result<Unit> {
        return try {
            requireUserId()
            val unread = notifications.filter { !it.read && it.id.isNotBlank() }
            if (unread.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            unread.take(400).forEach { notif ->
                batch.update(db.collection("notifications").document(notif.id), "read", true)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications")
            Result.failure(e)
        }
    }

    // --- EMERGENCY CONTACTS & IN-CALL SOS LIVE LOCATION ---

    fun observeEmergencyContacts(uid: String): Flow<List<EmergencyContact>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("users").document(uid).collection("emergency_contacts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(EmergencyContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "users/$uid/emergency_contacts")
                    }
                    throw error
                }
        )
    }

    suspend fun addEmergencyContact(
        name: String,
        phoneNumber: String,
        relationship: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val cleanName = name.trim().take(60)
        val cleanPhone = phoneNumber.trim().take(32)
        val cleanRel = relationship.trim().take(40)
        return try {
            require(cleanName.isNotEmpty()) { "Contact name cannot be empty" }
            require(cleanPhone.count { it.isDigit() } >= 3) { "Enter a valid phone number" }
            db.collection("users").document(uid).collection("emergency_contacts").document().set(
                mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "phoneNumber" to cleanPhone,
                    "relationship" to cleanRel,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/emergency_contacts")
            Result.failure(e)
        }
    }

    suspend fun deleteEmergencyContact(contactId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            require(contactId.isNotBlank()) { "Contact ID required" }
            db.collection("users").document(uid).collection("emergency_contacts").document(contactId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/emergency_contacts/$contactId")
            Result.failure(e)
        }
    }

    fun observeSosAlerts(uid: String): Flow<List<SosAlert>> = flow {
        val current = requireUserId()
        check(current == uid)
        emitAll(
            db.collection("sos_alerts")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SosAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, OperationType.LIST, "sos_alerts")
                    }
                    throw error
                }
        )
    }

    suspend fun createSosAlert(
        userName: String,
        callId: String,
        latitude: Double,
        longitude: Double,
        recipientPhones: List<String>
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val docRef = db.collection("sos_alerts").document()
            val cleanPhones = recipientPhones.map { it.trim().take(32) }.filter { it.isNotBlank() }.take(20)
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "userName" to userName.ifBlank { "Abhi User" }.take(60),
                    "callId" to callId.take(128),
                    "latitude" to latitude.coerceIn(-90.0, 90.0),
                    "longitude" to longitude.coerceIn(-180.0, 180.0),
                    "active" to true,
                    "recipientPhones" to cleanPhones,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "sos_alerts")
            Result.failure(e)
        }
    }

    suspend fun updateSosAlertLocation(
        alertId: String,
        latitude: Double,
        longitude: Double
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            require(alertId.isNotBlank()) { "Alert ID required" }
            db.collection("sos_alerts").document(alertId).update(
                mapOf(
                    "latitude" to latitude.coerceIn(-90.0, 90.0),
                    "longitude" to longitude.coerceIn(-180.0, 180.0),
                    "active" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sos_alerts/$alertId")
            Result.failure(e)
        }
    }

    suspend fun deactivateSosAlert(alertId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            require(alertId.isNotBlank()) { "Alert ID required" }
            db.collection("sos_alerts").document(alertId).update(
                mapOf(
                    "active" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sos_alerts/$alertId")
            Result.failure(e)
        }
    }
}
