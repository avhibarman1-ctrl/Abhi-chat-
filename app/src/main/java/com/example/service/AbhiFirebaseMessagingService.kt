package com.example.service

import android.util.Log
import com.example.R
import com.example.util.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AbhiFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val dbId = getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            db.collection("users").document(uid)
                .collection("private").document("info")
                .update(
                    mapOf(
                        "fcmToken" to token.take(512),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to persist new FCM token", e)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "New activity in ABHI CHAT"
        val notifId = (message.messageId ?: System.currentTimeMillis().toString()).hashCode()
        NotificationHelper.showNotification(
            context = applicationContext,
            id = notifId,
            title = title,
            body = body
        )
    }

    companion object {
        private const val TAG = "AbhiFcmService"
    }
}
