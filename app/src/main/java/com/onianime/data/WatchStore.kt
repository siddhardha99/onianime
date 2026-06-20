package com.onianime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.onianime.metadata.AniListMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "onianime")

/** Resume progress for a single episode. */
@Serializable
data class EpProgress(val positionMs: Long, val durationMs: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs * 0.92f
}

/** What the user has watched of a show: last episode + per-episode positions. */
@Serializable
data class ShowProgress(
    val media: AniListMedia,
    val lastEpisodeIndex: Int,
    val lastEpisodeLabel: String,
    val episodes: Map<Int, EpProgress> = emptyMap(),
    val updatedAt: Long = 0L,
)

/**
 * Persists watch progress (Continue Watching / resume) and My List to DataStore as JSON.
 * Lightweight — no Room/KSP — and survives app restarts.
 */
class WatchStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val progressKey = stringPreferencesKey("progress")
    private val myListKey = stringPreferencesKey("my_list")

    /** Shows with progress, most-recently-watched first. */
    val progress: Flow<List<ShowProgress>> = context.dataStore.data.map { prefs ->
        decodeProgress(prefs[progressKey]).values.sortedByDescending { it.updatedAt }
    }

    val myList: Flow<List<AniListMedia>> = context.dataStore.data.map { prefs ->
        decodeList(prefs[myListKey])
    }

    suspend fun saveProgress(
        media: AniListMedia,
        episodeIndex: Int,
        episodeLabel: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        context.dataStore.edit { prefs ->
            val map = decodeProgress(prefs[progressKey]).toMutableMap()
            val eps = (map[media.id]?.episodes ?: emptyMap()).toMutableMap()
            eps[episodeIndex] = EpProgress(positionMs, durationMs)
            map[media.id] = ShowProgress(media, episodeIndex, episodeLabel, eps, System.currentTimeMillis())
            prefs[progressKey] = json.encodeToString(map)
        }
    }

    /** Marks a show as started (updates last episode + recency) but keeps existing episode positions. */
    suspend fun markStarted(media: AniListMedia, episodeIndex: Int, episodeLabel: String) {
        context.dataStore.edit { prefs ->
            val map = decodeProgress(prefs[progressKey]).toMutableMap()
            val existing = map[media.id]
            map[media.id] = ShowProgress(
                media, episodeIndex, episodeLabel,
                existing?.episodes ?: emptyMap(),
                System.currentTimeMillis(),
            )
            prefs[progressKey] = json.encodeToString(map)
        }
    }

    /** Adds or removes the show from My List; returns true if it is now in the list. */
    suspend fun toggleMyList(media: AniListMedia): Boolean {
        var inList = false
        context.dataStore.edit { prefs ->
            val list = decodeList(prefs[myListKey]).toMutableList()
            val idx = list.indexOfFirst { it.id == media.id }
            if (idx >= 0) list.removeAt(idx) else { list.add(0, media); inList = true }
            prefs[myListKey] = json.encodeToString(list)
        }
        return inList
    }

    private fun decodeProgress(s: String?): Map<Int, ShowProgress> =
        if (s.isNullOrBlank()) emptyMap()
        else runCatching { json.decodeFromString<Map<Int, ShowProgress>>(s) }.getOrDefault(emptyMap())

    private fun decodeList(s: String?): List<AniListMedia> =
        if (s.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<AniListMedia>>(s) }.getOrDefault(emptyList())
}
