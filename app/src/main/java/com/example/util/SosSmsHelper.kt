package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.Locale

object SosSmsHelper {

    fun buildSosMessage(senderName: String, latitude: Double?, longitude: Double?): String {
        val cleanName = senderName.ifBlank { "ABHI CHAT user" }
        return if (latitude != null && longitude != null) {
            val coords = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
            val mapsUrl = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
            "EMERGENCY SOS from $cleanName (via ABHI CHAT): I need help! Live GPS coordinates: $coords — Track on map: $mapsUrl"
        } else {
            "EMERGENCY SOS from $cleanName (via ABHI CHAT): I need help! Acquiring live GPS location — please contact me immediately."
        }
    }

    fun launchEmergencySms(
        context: Context,
        phoneNumbers: List<String>,
        message: String
    ): Boolean {
        val sanitizedRecipients = phoneNumbers
            .map { raw -> raw.filter { it.isDigit() || it == '+' } }
            .filter { it.length >= 3 }
            .distinct()

        val smstoUri = Uri.parse("smsto:${sanitizedRecipients.joinToString(";")}")
        val sendToIntent = Intent(Intent.ACTION_SENDTO, smstoUri).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(sendToIntent)
            true
        } catch (e: Exception) {
            Log.w("SosSmsHelper", "ACTION_SENDTO smsto failed, trying ACTION_VIEW fallback", e)
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${sanitizedRecipients.joinToString(",")}")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
                true
            } catch (fallbackEx: Exception) {
                Log.e("SosSmsHelper", "Unable to open SMS app", fallbackEx)
                false
            }
        }
    }
}
