package com.cheatcrusher.util

import android.content.Context

import android.provider.Settings

import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceUtils @Inject constructor(
    private val context: Context
) {
    
    /**
     * Get device ID using Android ID as primary method
     * Falls back to Firebase Installations ID if Android ID is not available
     */
    suspend fun getDeviceId(): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            
            if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
                // Android ID is null or the emulator default, use Firebase Installations ID
                FirebaseInstallations.getInstance().id.await()
            } else {
                androidId
            }
        } catch (e: Exception) {
            // Fallback to Firebase Installations ID
            try {
                FirebaseInstallations.getInstance().id.await()
            } catch (firebaseException: Exception) {
                // Last resort: generate a random ID (not recommended for production)
                "device_${System.currentTimeMillis()}_${(0..9999).random()}"
            }
        }
    }

}