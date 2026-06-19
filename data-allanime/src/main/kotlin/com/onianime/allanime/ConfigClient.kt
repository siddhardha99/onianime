package com.onianime.allanime

import com.onianime.config.AllAnimeConfig
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the remote escape-hatch config (key/hash/etc.) from the onianime-config GitHub repo.
 * Always degrades to [fallback] on any error, so the app never hard-fails on a config miss.
 *
 * Wire this into app startup (async) and, crucially, call it with a forced refresh after a scrape
 * fails so a freshly-pushed fix self-heals on the user's retry.
 */
class ConfigClient(
    private val http: OkHttpClient = AllAnimeClient.defaultClient(),
) {
    fun fetch(
        url: String = AllAnimeConfig.REMOTE_URL,
        fallback: AllAnimeConfig = AllAnimeConfig.BAKED_IN,
    ): AllAnimeConfig = runCatching {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return fallback
            AllAnimeConfig.fromJson(resp.body?.string().orEmpty(), fallback)
        }
    }.getOrDefault(fallback)
}
