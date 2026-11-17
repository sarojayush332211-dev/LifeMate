package com.example.data.local

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val Context.lifeMateDataStore by preferencesDataStore(name = "lifemate_preferences")

class LocalPreferencesStore(private val context: Context) {

    private fun darkModeKey(uid: String) = booleanPreferencesKey("dark_mode_$uid")
    private fun localAvatarKey(uid: String) = stringPreferencesKey("local_avatar_$uid")
    private val lastThemeKey = booleanPreferencesKey("last_dark_mode")
    private val activeUserIdKey = stringPreferencesKey("active_user_id")

    private val safePreferencesData = context.lifeMateDataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val activeUserIdFlow: Flow<String?> = safePreferencesData.map { prefs ->
        prefs[activeUserIdKey]?.takeIf { it.isNotBlank() }
    }

    val lastDarkModeFlow: Flow<Boolean> = safePreferencesData.map { prefs ->
        prefs[lastThemeKey] ?: false
    }

    fun darkModeFlow(uid: String): Flow<Boolean?> = safePreferencesData.map { prefs ->
        prefs[darkModeKey(uid)]
    }

    fun localAvatarFlow(uid: String): Flow<String?> = safePreferencesData.map { prefs ->
        prefs[localAvatarKey(uid)]?.takeIf { path -> File(path).exists() }
    }

    suspend fun setActiveUserId(uid: String?) {
        context.lifeMateDataStore.edit { prefs ->
            if (uid.isNullOrBlank()) {
                prefs.remove(activeUserIdKey)
            } else {
                prefs[activeUserIdKey] = uid
            }
        }
    }

    suspend fun setDarkMode(uid: String?, enabled: Boolean) {
        context.lifeMateDataStore.edit { prefs ->
            prefs[lastThemeKey] = enabled
            if (!uid.isNullOrBlank()) {
                prefs[darkModeKey(uid)] = enabled
            }
        }
    }

    suspend fun savePickedAvatar(uid: String, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = File(context.filesDir, "avatar_$uid.jpg")
            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
            if (copied) {
                val absolutePath = targetFile.absolutePath
                context.lifeMateDataStore.edit { prefs ->
                    prefs[localAvatarKey(uid)] = absolutePath
                }
                absolutePath
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun resetLocalData(uid: String) {
        withContext(Dispatchers.IO) {
            val avatarFile = File(context.filesDir, "avatar_$uid.jpg")
            if (avatarFile.exists()) {
                avatarFile.delete()
            }
        }
        context.lifeMateDataStore.edit { prefs ->
            prefs.remove(darkModeKey(uid))
            prefs.remove(localAvatarKey(uid))
            prefs[lastThemeKey] = false
        }
    }
}
