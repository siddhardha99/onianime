package com.onianime.metadata

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** An opening/ending skip interval from AniSkip, in milliseconds. */
data class SkipInterval(
    val type: String, // "op" or "ed"
    val startMs: Long,
    val endMs: Long,
) {
    val label: String get() = if (type == "op") "Skip Intro" else "Skip Outro"
    fun contains(positionMs: Long): Boolean = positionMs in startMs..endMs
}

/**
 * AniSkip API client (https://api.aniskip.com) — community-sourced op/ed timestamps keyed by MAL id.
 * This is what powers auto-skip; mpv-based clients (like ani-cli's --skip) use the same database.
 */
class AniSkipClient(
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns op/ed intervals for [malId] episode [episode]. Empty if AniSkip has no data. */
    fun skipTimes(malId: Int, episode: Int, episodeLengthSec: Int = 0): List<SkipInterval> {
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode".toHttpUrl().newBuilder()
            .addQueryParameter("types[]", "op")
            .addQueryParameter("types[]", "ed")
            .addQueryParameter("episodeLength", episodeLengthSec.toString())
            .build()

        val resp = http.newCall(
            Request.Builder().url(url).header("User-Agent", "onianime").get().build()
        ).execute().use { it.body?.string().orEmpty() }

        return parse(resp)
    }

    private fun parse(resp: String): List<SkipInterval> {
        if (resp.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(resp).jsonObject }.getOrNull() ?: return emptyList()
        if (root["found"]?.jsonPrimitive?.booleanOrNull != true) return emptyList()
        val results = root["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { el ->
            val o = el.jsonObject
            val type = o["skipType"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val interval = o["interval"]?.jsonObject ?: return@mapNotNull null
            val start = interval["startTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val end = interval["endTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            SkipInterval(type, (start * 1000).toLong(), (end * 1000).toLong())
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
