package com.onianime.hianime

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/** The source answered with a bot-check page (Cloudflare "Just a moment...") instead of content. */
class SourceBlockedException(message: String) : IOException(message)

/** An OkHttp client tuned for the source sites; share one between [ZokoResolver] and [HiAnimeClient]. */
fun sourceHttpClient(): OkHttpClient = Http.defaultClient()

/** Plain GETs with the configured User-Agent, mirroring ani-cli's `hianime_curl`. */
internal class Http(private val client: OkHttpClient, private val agent: String) {

    fun get(url: String, referer: String? = null): String {
        val request = Request.Builder().url(url).header("User-Agent", agent).get()
        if (!referer.isNullOrBlank()) request.header("Referer", referer)
        client.newCall(request.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (isChallenge(resp.code, body, resp.header("cf-mitigated"))) {
                throw SourceBlockedException("blocked by Cloudflare at ${hostOf(url)}")
            }
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from ${hostOf(url)}")
            return body
        }
    }

    companion object {
        fun isChallenge(code: Int, body: String, cfMitigated: String?): Boolean =
            cfMitigated.equals("challenge", ignoreCase = true) ||
                ((code == 403 || code == 503 || code == 429) &&
                    (body.contains("<title>Just a moment", ignoreCase = true) ||
                        body.contains("challenge-platform", ignoreCase = true) ||
                        body.contains("cf-chl-", ignoreCase = true))) ||
                body.contains("<title>Just a moment...</title>", ignoreCase = true)

        fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url

        /** "https://zokoanime.video/stream/..." -> "https://zokoanime.video" (ani-cli's referer). */
        fun originOf(url: String): String = runCatching {
            val u = URI(url)
            requireNotNull(u.scheme); requireNotNull(u.authority)
            "${u.scheme}://${u.authority}"
        }.getOrElse { Regex("^(https?://[^/?#]+)").find(url)?.value ?: url }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
