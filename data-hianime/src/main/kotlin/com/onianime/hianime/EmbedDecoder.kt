package com.onianime.hianime

import com.onianime.stream.SubtitleTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.net.URI

/** What the ZokoAnime player config says to play. */
data class EmbedSources(
    /** Video URLs (m3u8 preferred), best guess first. */
    val videoUrls: List<String>,
    val subtitles: List<SubtitleTrack>,
)

/**
 * Port of ani-cli 5.x's ZokoAnime handling (`deobfuscate_blob` + the sed in `hianime_m3u8`).
 *
 * The embed page ships its player config as `window.__P="<base64>"`, where the base64 decodes to the
 * config JSON XOR-ed with a repeating key ("otaku-embed-v1"). The JSON holds the m3u8 `src` and a
 * `subtitles` array whose `default` entry is the English track.
 *
 * Everything here is pure (no network) so it can be unit-tested against captured pages.
 */
object EmbedDecoder {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val VIDEO_KEYS = setOf("src", "file", "url", "source", "hls", "m3u8", "stream", "link", "playlist")
    private val SUBTITLE_KEYS = setOf("subtitles", "subtitle", "tracks", "captions", "subs")
    private val SUBTITLE_KINDS = setOf("captions", "caption", "subtitles", "subtitle")
    private val LANGUAGE_CODE = Regex("""[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*""")

    /** Pulls the obfuscated player config out of the embed page (`window.__P="..."`). */
    fun extractBlob(html: String, blobVar: String): String? {
        val name = Regex.escape(blobVar.removePrefix("window."))
        val patterns = listOf(
            Regex("""window\s*\.\s*$name\s*=\s*(["'`])(.*?)\1""", RegexOption.DOT_MATCHES_ALL),
            Regex("""window\s*\[\s*["']$name["']\s*]\s*=\s*(["'`])(.*?)\1""", RegexOption.DOT_MATCHES_ALL),
            Regex("""(?<![\w$.])$name\s*=\s*(["'`])(.*?)\1""", RegexOption.DOT_MATCHES_ALL),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(2)?.trim()?.takeIf(String::isNotEmpty) }
    }

    /** base64 -> bytes XOR the repeating [xorKey] -> UTF-8 text (ani-cli `deobfuscate_blob`). */
    fun deobfuscate(blob: String, xorKey: String): String {
        require(xorKey.isNotEmpty()) { "xorKey must not be empty" }
        val bytes = decodeBase64(blob)
        val key = xorKey.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor key[i % key.size].toInt()).toByte() }
        return String(out, Charsets.UTF_8)
    }

    /**
     * Lenient base64: standard or URL-safe alphabet, with or without padding, JS-escaped slashes.
     * Hand-rolled because java.util.Base64 only exists from Android 8 (API 26) and minSdk is 23.
     */
    fun decodeBase64(raw: String): ByteArray {
        val cleaned = raw.replace("\\u002F", "/").replace("\\u002f", "/")
            .replace("\\", "").filterNot { it.isWhitespace() }.trimEnd('=')
        require(cleaned.length % 4 != 1) { "invalid base64 length" }
        val out = ByteArray(cleaned.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var n = 0
        for (c in cleaned) {
            val v = when (c) {
                in 'A'..'Z' -> c - 'A'
                in 'a'..'z' -> c - 'a' + 26
                in '0'..'9' -> c - '0' + 52
                '+', '-' -> 62
                '/', '_' -> 63
                else -> throw IllegalArgumentException("invalid base64 character '$c'")
            }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[n++] = (buffer shr bits).toByte()
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return if (n == out.size) out else out.copyOf(n)
    }

    /**
     * Reads the decoded player config. [pageUrl] resolves any relative URLs. Walks the whole JSON
     * tree rather than assuming one layout, then falls back to ani-cli's plain regex.
     */
    fun parse(playerJson: String, pageUrl: String): EmbedSources {
        val videos = mutableListOf<String>()
        val subs = mutableListOf<SubtitleTrack>()
        runCatching { json.parseToJsonElement(playerJson) }.getOrNull()?.let { walk(it, null, pageUrl, videos, subs) }

        if (videos.isEmpty()) {
            // ani-cli: sed -nE 's|.*"src":"([^"]*\.m3u8[^"]*)".*|\1|p'
            Regex(""""(?:src|file|url)"\s*:\s*"([^"]*\.m3u8[^"]*)"""").findAll(playerJson)
                .forEach { videos += resolve(pageUrl, unescape(it.groupValues[1])) }
        }

        val hls = videos.filter { it.contains(".m3u8", ignoreCase = true) }
        // ani-cli's greedy sed keeps the LAST m3u8 in the config, so prefer that one, then the rest.
        val ordered = (hls.reversed() + videos.filterNot { it in hls }).distinct()
        val tracks = subs.distinctBy { it.url }
        return EmbedSources(ordered, tracks)
    }

    private fun walk(
        el: JsonElement,
        key: String?,
        pageUrl: String,
        videos: MutableList<String>,
        subs: MutableList<SubtitleTrack>,
    ) {
        when (el) {
            is JsonObject -> el.forEach { (k, v) ->
                val lower = k.lowercase()
                if (lower in SUBTITLE_KEYS && (v is JsonArray || v is JsonObject)) {
                    val items = if (v is JsonArray) v else JsonArray(listOf(v))
                    items.forEach { item -> (item as? JsonObject)?.let { subtitleFrom(it, pageUrl) }?.let(subs::add) }
                } else {
                    walk(v, lower, pageUrl, videos, subs)
                }
            }
            is JsonArray -> el.forEach { walk(it, key, pageUrl, videos, subs) }
            is JsonPrimitive -> {
                if (!el.isString) return
                val value = el.content.trim()
                val path = value.substringBefore('?').lowercase()
                // Any m3u8 counts wherever it sits; a bare mp4 only under a video-ish key.
                if (path.contains(".m3u8") || (path.endsWith(".mp4") && key in VIDEO_KEYS)) {
                    videos += resolve(pageUrl, value)
                }
            }
        }
    }

    private fun subtitleFrom(o: JsonObject, pageUrl: String): SubtitleTrack? {
        val kind = o.str("kind")?.lowercase()
        if (kind != null && kind !in SUBTITLE_KINDS) return null // e.g. "thumbnails", "chapters"
        val url = o.str("src") ?: o.str("file") ?: o.str("url") ?: return null
        if (url.contains("thumbnail", ignoreCase = true) || url.contains("sprite", ignoreCase = true)) return null
        val label = o.str("label") ?: o.str("name") ?: o.str("language") ?: o.str("lang") ?: "Subtitles"
        val declared = o.str("srclang") ?: o.str("lang") ?: o.str("language")
        // Only real codes ("en", "pt-BR") count; a name like "English" would never match the
        // player's preferred "en", so map names through guessLanguage instead.
        val lang = declared?.takeIf { LANGUAGE_CODE.matches(it) }?.lowercase()
            ?: declared?.let(::guessLanguage)
            ?: guessLanguage(label)
        val isDefault = (o["default"] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.content == "true") } ?: false
        return SubtitleTrack(resolve(pageUrl, unescape(url)), label, lang, isDefault)
    }

    private fun guessLanguage(label: String): String? = when {
        label.contains("english", ignoreCase = true) -> "en"
        label.contains("español", ignoreCase = true) || label.contains("spanish", ignoreCase = true) -> "es"
        label.contains("portugu", ignoreCase = true) -> "pt"
        label.contains("french", ignoreCase = true) || label.contains("français", ignoreCase = true) -> "fr"
        label.contains("german", ignoreCase = true) || label.contains("deutsch", ignoreCase = true) -> "de"
        label.contains("arabic", ignoreCase = true) -> "ar"
        else -> null
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun unescape(s: String): String =
        s.replace("\\/", "/").replace("\\u0026", "&").replace("\\u003D", "=").replace("\\u003d", "=")

    /** Resolves [url] against [base] (handles absolute, protocol-relative and relative links). */
    fun resolve(base: String, url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return runCatching { URI(base).resolve(url.replace(" ", "%20")).toString() }.getOrDefault(url)
    }
}
