package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import android.os.Looper
import com.example.data.DecryptedLocation
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class LocationHelper(context: Context) {
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): DecryptedLocation? {
        val loc: android.location.Location = suspendCancellableCoroutine<android.location.Location?> { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { current ->
                    if (current != null) {
                        cont.resume(current)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    fusedClient.lastLocation
                        .addOnSuccessListener { last -> cont.resume(last) }
                        .addOnFailureListener { cont.resume(null) }
                }
        } ?: return null

        val address = resolveAddress(loc.latitude, loc.longitude)
        return DecryptedLocation(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = loc.accuracy,
            addressLabel = address
        )
    }

    @SuppressLint("MissingPermission")
    fun observeRealtimeLocation(intervalMs: Long = 4000L): Flow<DecryptedLocation> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(2500L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    DecryptedLocation(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = loc.accuracy,
                        addressLabel = String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
                    )
                )
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveAddress(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                return@withContext String.format(Locale.US, "%.5f, %.5f", lat, lng)
            }
            val geocoder = Geocoder(appContext, Locale("id", "ID"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val addr = addresses.firstOrNull()
                        val line = listOfNotNull(
                            addr?.thoroughfare,
                            addr?.subLocality,
                            addr?.locality,
                            addr?.adminArea
                        ).distinct().joinToString(", ")
                        cont.resume(line.ifBlank { String.format(Locale.US, "%.5f, %.5f", lat, lng) })
                    }
                }
            } else {
                val list = geocoder.getFromLocation(lat, lng, 1)
                val addr = list?.firstOrNull()
                val line = listOfNotNull(
                    addr?.thoroughfare,
                    addr?.subLocality,
                    addr?.locality,
                    addr?.adminArea
                ).distinct().joinToString(", ")
                line.ifBlank { String.format(Locale.US, "%.5f, %.5f", lat, lng) }
            }
        } catch (_: Exception) {
            String.format(Locale.US, "%.5f, %.5f", lat, lng)
        }
    }
}
