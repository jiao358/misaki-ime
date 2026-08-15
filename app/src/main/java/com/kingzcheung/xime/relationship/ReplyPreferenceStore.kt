package com.kingzcheung.xime.relationship

import android.content.Context

data class ReplyPreferenceProfile(
    val naturalScore: Int,
    val humorousScore: Int,
    val measuredScore: Int,
    val sampleCount: Int,
) {
    fun score(style: String): Int = when (style) {
        "humorous" -> humorousScore
        "measured" -> measuredScore
        else -> naturalScore
    }
}

object ReplyPreferenceStore {
    private const val PREFS_NAME = "relationship_reply_preferences"

    fun profile(context: Context, personId: String): ReplyPreferenceProfile {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ReplyPreferenceProfile(
            naturalScore = prefs.getInt(key(personId, "natural"), 0),
            humorousScore = prefs.getInt(key(personId, "humorous"), 0),
            measuredScore = prefs.getInt(key(personId, "measured"), 0),
            sampleCount = prefs.getInt(key(personId, "samples"), 0),
        )
    }

    fun record(context: Context, personId: String, style: String, positive: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scoreKey = key(personId, style)
        val sampleKey = key(personId, "samples")
        val delta = if (positive) 2 else -3
        prefs.edit()
            .putInt(scoreKey, (prefs.getInt(scoreKey, 0) + delta).coerceIn(-20, 20))
            .putInt(sampleKey, prefs.getInt(sampleKey, 0) + 1)
            .apply()
    }

    private fun key(personId: String, field: String) = "$personId.$field"
}

object ReplyStyleRanker {
    fun <T> rank(
        candidates: List<T>,
        styleOf: (T) -> String,
        profile: ReplyPreferenceProfile,
    ): List<T> = candidates.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<T>> { profile.score(styleOf(it.value)) }
                .thenBy { it.index },
        )
        .map { it.value }
}
