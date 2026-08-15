package com.kingzcheung.xime.relationship

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class CurrentPersonSelection(
    val id: String,
    val alias: String,
    val expiresAt: Long,
)

object CurrentPersonStore {
    private const val PREFS_NAME = "relationship_session"
    private const val KEY_ID = "current_person_id"
    private const val KEY_ALIAS = "current_person_alias"
    private const val KEY_EXPIRES_AT = "current_person_expires_at"
    private const val SESSION_DURATION_MS = 30 * 60 * 1000L

    fun select(context: Context, id: String, alias: String) {
        prefs(context).edit()
            .putString(KEY_ID, id)
            .putString(KEY_ALIAS, alias)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + SESSION_DURATION_MS)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_ID)
            .remove(KEY_ALIAS)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    fun current(context: Context): CurrentPersonSelection? {
        val preferences = prefs(context)
        val id = preferences.getString(KEY_ID, null) ?: return null
        val alias = preferences.getString(KEY_ALIAS, null) ?: return null
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt <= System.currentTimeMillis()) {
            clear(context)
            return null
        }
        return CurrentPersonSelection(id, alias, expiresAt)
    }

    fun observe(context: Context): Flow<CurrentPersonSelection?> = callbackFlow {
        val preferences = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ID || key == KEY_ALIAS || key == KEY_EXPIRES_AT) {
                trySend(current(context))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(current(context))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
