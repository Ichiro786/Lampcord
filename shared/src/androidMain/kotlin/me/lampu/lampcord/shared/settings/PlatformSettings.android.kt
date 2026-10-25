@file:Suppress("DEPRECATION")

package me.lampu.lampcord.shared.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

private var appContext: Context? = null

fun initSettings(context: Context) {
    appContext = context.applicationContext
}

actual fun createSettings(): Settings {
    val context = appContext ?: throw IllegalStateException("Settings not initialized with context")

    val delegate = try {
        openEncryptedPrefs(context)
    } catch (e: Exception) {
        // The file can't be decrypted (restored from another device, Keystore wiped or
        // corrupted). Crashing in Application.onCreate is unrecoverable for the user, so start
        // over with fresh settings; they'll simply need to sign in again.
        android.util.Log.e("Settings", "Encrypted settings unreadable, resetting", e)
        context.deleteSharedPreferences(PREFS_NAME)
        openEncryptedPrefs(context)
    }

    return SharedPreferencesSettings(delegate)
}

private const val PREFS_NAME = "lampcord_encrypted_settings"

private fun openEncryptedPrefs(context: Context): android.content.SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}
