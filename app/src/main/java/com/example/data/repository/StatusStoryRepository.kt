package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AudiencePrivacy
import com.example.data.model.StatusItem
import com.example.data.model.StatusView
import com.example.data.model.StoryItem
import com.example.data.model.StoryView
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StatusStoryRepository(
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

    // --- WHATSAPP STYLE STATUS SYSTEM ---

    fun observeActiveStatuses(uid: String): Flow<List<StatusItem>> = flow {
        val current = requireUserId()
        check(current == uid)
        val ownFlow = db.collection("statuses")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { it.toObjects(StatusItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val publicFlow = db.collection("statuses")
            .whereEqualTo("privacy", AudiencePrivacy.EVERYONE.name)
            .snapshots()
            .map { it.toObjects(StatusItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val friendsFlow = db.collection("statuses")
            .whereEqualTo("privacy", AudiencePrivacy.FRIENDS.name)
            .whereArrayContains("allowedViewerIds", uid)
            .snapshots()
            .map { it.toObjects(StatusItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(ownFlow, publicFlow, friendsFlow) { own, pub, friends ->
                val nowMs = System.currentTimeMillis()
                (own + pub + friends)
                    .distinctBy { it.id }
                    .filter { it.expiresAtMillis > nowMs }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }.catch { error ->
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, "statuses")
                }
                throw error
            }
        )
    }

    fun observeMyStatusViewReceipts(uid: String): Flow<List<StatusView>> = flow {
        val current = requireUserId()
        check(current == uid)
        val viewedByMe = db.collection("status_views")
            .whereEqualTo("viewerId", uid)
            .snapshots()
            .map { it.toObjects(StatusView::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val viewsOnMine = db.collection("status_views")
            .whereEqualTo("statusOwnerId", uid)
            .snapshots()
            .map { it.toObjects(StatusView::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(viewedByMe, viewsOnMine) { mine, owners ->
                (mine + owners).distinctBy { it.id }
            }.catch { error ->
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, "status_views")
                }
                throw error
            }
        )
    }

    suspend fun postStatus(
        user: UserProfile,
        mediaType: String,
        text: String,
        backgroundColorHex: String,
        mediaData: String,
        privacy: AudiencePrivacy,
        friendIds: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val expiresAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            val allowedIds = when (privacy) {
                AudiencePrivacy.FRIENDS -> friendIds.distinct().take(200)
                else -> emptyList()
            }
            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "userName" to user.displayName.take(60),
                "userAvatarUrl" to user.avatarUrl.take(350000).takeIf { it.isNotEmpty() },
                "mediaType" to mediaType,
                "text" to text.trim().take(500),
                "backgroundColorHex" to backgroundColorHex.take(16),
                "mediaData" to mediaData.take(350000).takeIf { it.isNotEmpty() },
                "privacy" to privacy.name,
                "allowedViewerIds" to allowedIds,
                "viewerCount" to 0L,
                "expiresAtMillis" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            db.collection("statuses").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "statuses")
            Result.failure(e)
        }
    }

    suspend fun markStatusViewed(status: StatusItem, viewer: UserProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        if (status.userId == uid) return Result.success(Unit)
        val viewId = "${status.id}_$uid"
        return try {
            val viewRef = db.collection("status_views").document(viewId)
            val existing = viewRef.get().await()
            if (!existing.exists()) {
                val batch = db.batch()
                batch.set(
                    viewRef,
                    mapOf(
                        "statusId" to status.id,
                        "statusOwnerId" to status.userId,
                        "viewerId" to uid,
                        "viewerName" to viewer.displayName.take(60),
                        "viewerAvatarUrl" to viewer.avatarUrl.take(350000),
                        "viewedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    db.collection("statuses").document(status.id),
                    mapOf(
                        "viewerCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "status_views/$viewId")
            Result.failure(e)
        }
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("statuses").document(statusId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "statuses/$statusId")
            Result.failure(e)
        }
    }

    // --- FACEBOOK STYLE STORY SYSTEM ---

    fun observeActiveStories(uid: String): Flow<List<StoryItem>> = flow {
        val current = requireUserId()
        check(current == uid)
        val ownFlow = db.collection("stories")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { it.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val publicFlow = db.collection("stories")
            .whereEqualTo("privacy", AudiencePrivacy.EVERYONE.name)
            .snapshots()
            .map { it.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val friendsFlow = db.collection("stories")
            .whereEqualTo("privacy", AudiencePrivacy.FRIENDS.name)
            .whereArrayContains("allowedViewerIds", uid)
            .snapshots()
            .map { it.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(ownFlow, publicFlow, friendsFlow) { own, pub, friends ->
                val nowMs = System.currentTimeMillis()
                (own + pub + friends)
                    .distinctBy { it.id }
                    .filter { it.expiresAtMillis > nowMs }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }.catch { error ->
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, "stories")
                }
                throw error
            }
        )
    }

    fun observeStoryViewReceipts(uid: String): Flow<List<StoryView>> = flow {
        val current = requireUserId()
        check(current == uid)
        val viewedByMe = db.collection("story_views")
            .whereEqualTo("viewerId", uid)
            .snapshots()
            .map { it.toObjects(StoryView::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val viewsOnMine = db.collection("story_views")
            .whereEqualTo("storyOwnerId", uid)
            .snapshots()
            .map { it.toObjects(StoryView::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(viewedByMe, viewsOnMine) { mine, owners ->
                (mine + owners).distinctBy { it.id }
            }.catch { error ->
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, "story_views")
                }
                throw error
            }
        )
    }

    suspend fun postStory(
        user: UserProfile,
        mediaType: String,
        caption: String,
        overlayText: String,
        backgroundColorHex: String,
        mediaData: String,
        privacy: AudiencePrivacy,
        friendIds: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val expiresAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            val allowedIds = when (privacy) {
                AudiencePrivacy.FRIENDS -> friendIds.distinct().take(200)
                else -> emptyList()
            }
            val storyRef = db.collection("stories").document()
            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "userName" to user.displayName.take(60),
                "userAvatarUrl" to user.avatarUrl.take(350000).takeIf { it.isNotEmpty() },
                "mediaType" to mediaType,
                "caption" to caption.trim().take(500),
                "overlayText" to overlayText.trim().take(300),
                "backgroundColorHex" to backgroundColorHex.take(16),
                "mediaData" to mediaData.take(350000).takeIf { it.isNotEmpty() },
                "privacy" to privacy.name,
                "allowedViewerIds" to allowedIds,
                "reactionCounts" to emptyMap<String, Long>(),
                "userReactions" to emptyMap<String, String>(),
                "viewerCount" to 0L,
                "replyCount" to 0L,
                "expiresAtMillis" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val batch = db.batch()
            batch.set(storyRef, payload)
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "hasActiveStory" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "stories")
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(story: StoryItem, viewer: UserProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        if (story.userId == uid) return Result.success(Unit)
        val viewId = "${story.id}_$uid"
        return try {
            val viewRef = db.collection("story_views").document(viewId)
            val existing = viewRef.get().await()
            if (!existing.exists()) {
                val batch = db.batch()
                batch.set(
                    viewRef,
                    mapOf(
                        "storyId" to story.id,
                        "storyOwnerId" to story.userId,
                        "viewerId" to uid,
                        "viewerName" to viewer.displayName.take(60),
                        "viewerAvatarUrl" to viewer.avatarUrl.take(350000),
                        "reaction" to "",
                        "replyText" to "",
                        "viewedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    db.collection("stories").document(story.id),
                    mapOf(
                        "viewerCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "story_views/$viewId")
            Result.failure(e)
        }
    }

    suspend fun reactToStory(story: StoryItem, viewer: UserProfile, emoji: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        return try {
            val prevEmoji = story.userReactions[uid]
            val updatedUserReactions = story.userReactions.toMutableMap().apply { put(uid, emoji) }
            val updatedCounts = story.reactionCounts.toMutableMap()
            if (prevEmoji != null && prevEmoji != emoji) {
                val prevCount = (updatedCounts[prevEmoji] ?: 1L) - 1L
                if (prevCount <= 0L) updatedCounts.remove(prevEmoji) else updatedCounts[prevEmoji] = prevCount
            }
            if (prevEmoji != emoji) {
                updatedCounts[emoji] = (updatedCounts[emoji] ?: 0L) + 1L
            }

            val batch = db.batch()
            batch.update(
                db.collection("stories").document(story.id),
                mapOf(
                    "reactionCounts" to updatedCounts,
                    "userReactions" to updatedUserReactions,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val viewId = "${story.id}_$uid"
            val viewRef = db.collection("story_views").document(viewId)
            val existing = viewRef.get().await()
            if (existing.exists()) {
                batch.update(
                    viewRef,
                    mapOf(
                        "reaction" to emoji,
                        "viewedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    viewRef,
                    mapOf(
                        "storyId" to story.id,
                        "storyOwnerId" to story.userId,
                        "viewerId" to uid,
                        "viewerName" to viewer.displayName.take(60),
                        "viewerAvatarUrl" to viewer.avatarUrl.take(350000),
                        "reaction" to emoji,
                        "replyText" to "",
                        "viewedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "stories/${story.id}")
            Result.failure(e)
        }
    }

    suspend fun replyToStory(
        story: StoryItem,
        viewer: UserProfile,
        replyText: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Unauthenticated"))
        val clean = replyText.trim().take(500)
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Reply cannot be empty"))
        return try {
            val viewId = "${story.id}_$uid"
            val viewRef = db.collection("story_views").document(viewId)
            val existing = viewRef.get().await()
            val batch = db.batch()
            if (existing.exists()) {
                batch.update(
                    viewRef,
                    mapOf(
                        "replyText" to clean,
                        "viewedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    viewRef,
                    mapOf(
                        "storyId" to story.id,
                        "storyOwnerId" to story.userId,
                        "viewerId" to uid,
                        "viewerName" to viewer.displayName.take(60),
                        "viewerAvatarUrl" to viewer.avatarUrl.take(350000),
                        "reaction" to "",
                        "replyText" to clean,
                        "viewedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                db.collection("stories").document(story.id),
                mapOf(
                    "replyCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (story.userId != uid) {
                batch.set(
                    db.collection("notifications").document(),
                    mapOf(
                        "recipientId" to story.userId,
                        "senderId" to uid,
                        "senderName" to viewer.displayName.take(60),
                        "type" to "STORY_REPLY",
                        "title" to "${viewer.displayName} replied to your story",
                        "body" to clean.take(300),
                        "targetId" to story.id,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "stories/${story.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("stories").document(storyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "stories/$storyId")
            Result.failure(e)
        }
    }

    suspend fun cleanupExpiredOwnItems(uid: String) {
        val current = auth.currentUser?.uid ?: return
        if (current != uid) return
        try {
            val nowMs = System.currentTimeMillis()
            val myStatuses = db.collection("statuses").whereEqualTo("userId", uid).get().await()
                .toObjects(StatusItem::class.java)
            myStatuses.filter { it.expiresAtMillis in 1..nowMs }.forEach { expired ->
                db.collection("statuses").document(expired.id).delete().await()
            }
            val myStories = db.collection("stories").whereEqualTo("userId", uid).get().await()
                .toObjects(StoryItem::class.java)
            val activeStories = myStories.filter { it.expiresAtMillis > nowMs }
            myStories.filter { it.expiresAtMillis in 1..nowMs }.forEach { expired ->
                db.collection("stories").document(expired.id).delete().await()
            }
            db.collection("users").document(uid).update(
                mapOf(
                    "hasActiveStory" to activeStories.isNotEmpty(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }
}
