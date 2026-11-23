package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): Coordinates? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val fresh = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            val loc = fresh ?: client.lastLocation.await()
            loc?.let { Coordinates(it.latitude, it.longitude) }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(
        context: Context,
        intervalMs: Long = 8_000L
    ): Flow<Coordinates> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2L).coerceAtLeast(3_000L))
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(Coordinates(loc.latitude, loc.longitude))
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        awaitClose {
            try {
                client.removeLocationUpdates(callback)
            } catch (_: Exception) {
            }
        }
    }

    fun formatCoordinates(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lng)

    fun formatRemainingDuration(
        expiresAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        if (expiresAtMillis <= 0L) return "Active"
        val remainingMs = expiresAtMillis - nowMillis
        if (remainingMs <= 0L) return "Expired"
        val totalMinutes = remainingMs / 60_000L
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        val secs = (remainingMs % 60_000L) / 1000L
        return when {
            hours > 0L -> "${hours}h ${mins}m left"
            totalMinutes > 0L -> "${mins}m left"
            else -> "${secs}s left"
        }
    }

    fun openInMaps(context: Context, lat: Double, lng: Double, label: String) {
        val encodedLabel = Uri.encode(label)
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val webUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

