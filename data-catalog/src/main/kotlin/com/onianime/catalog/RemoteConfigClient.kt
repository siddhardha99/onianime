package com.onianime.catalog

import com.onianime.config.OniConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches the remote escape-hatch config (`onianime.json` in the onianime-config repo).
 * Always degrades to [OniConfig.BAKED_IN] on any error, so the app never hard-fails on a config miss.
 *
 * The app calls this on launch and again after a scrape fails, so a freshly pushed fix self-heals
 * on the user's retry.
 */
class RemoteConfigClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    fun fetch(
        url: String = OniConfig.REMOTE_URL,
        fallback: OniConfig = OniConfig.BAKED_IN,
    ): OniConfig = runCatching {
        // Skip caches: a fix pushed a minute ago should be picked up on the next retry.
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").get().build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return fallback
            OniConfig.fromJson(resp.body?.string().orEmpty(), fallback)
        }
    }.getOrDefault(fallback)
}
