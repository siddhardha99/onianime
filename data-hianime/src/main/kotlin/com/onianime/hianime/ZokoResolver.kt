package com.onianime.hianime

import com.onianime.config.OniConfig
import com.onianime.stream.Stream
import com.onianime.stream.SubtitleTrack
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * ZokoAnime embed page -> playable streams. This is the back half of ani-cli 5.x's `hianime_m3u8`:
 * fetch the embed, decode `window.__P`, take the m3u8 + subtitles, expand the master playlist.
 *
 * The embed URL can come from hianime.at's server list or, much faster, straight from the MAL id via
 * [embedUrl] (the URLs hianime hands out are just `.../stream/mal/<malId>/<ep>/<sub|dub>`).
 */
class ZokoResolver(
    private val config: OniConfig,
    httpClient: OkHttpClient = Http.defaultClient(),
) {
    private val http = Http(httpClient, config.agent)

    /** Fills the configured embed template for one episode. */
    fun embedUrl(malId: Int, episode: String, mode: String, anilistId: Int? = null): String =
        config.zoko.embed
            .replace("{mal}", malId.toString())
            .replace("{ep}", episode.trim())
            .replace("{mode}", mode.lowercase())
            .replace("{anilist}", anilistId?.toString().orEmpty())

    /** Resolves an embed page into streams ("auto" master first, then fixed qualities). */
    fun resolve(embedUrl: String): List<Stream> {
        val page = http.get(embedUrl)
        val blob = EmbedDecoder.extractBlob(page, config.zoko.blobVar)
            ?: throw IOException("no '${config.zoko.blobVar}' player config on ${Http.hostOf(embedUrl)} (episode missing?)")
        val playerJson = runCatching { EmbedDecoder.deobfuscate(blob, config.zoko.xorKey) }
            .getOrElse { throw IOException("couldn't decode the player config (key changed?)") }
        val sources = EmbedDecoder.parse(playerJson, embedUrl)
        if (sources.videoUrls.isEmpty()) throw IOException("player config has no stream (key changed?)")

        // ani-cli: refr = embed origin + "/" (the stream host checks it).
        val referer = config.zoko.referer.ifBlank { Http.originOf(embedUrl) + "/" }
        var lastError: Throwable? = null
        for (video in sources.videoUrls) {
            val streams = runCatching { expand(video, referer, sources.subtitles) }
                .onFailure { lastError = it }.getOrNull()
            if (!streams.isNullOrEmpty()) return streams
        }
        throw IOException("stream playlist unavailable: ${lastError?.message ?: "empty"}")
    }

    private fun expand(videoUrl: String, referer: String, subs: List<SubtitleTrack>): List<Stream> {
        val isHls = videoUrl.substringBefore('?').contains(".m3u8", ignoreCase = true)
        if (!isHls) return listOf(Stream("0", videoUrl, PROVIDER, referer, false, subs))

        val playlist = http.get(videoUrl, referer)
        if (!HlsPlaylist.isPlaylist(playlist)) throw IOException("not an HLS playlist from ${Http.hostOf(videoUrl)}")
        // The master itself is the adaptive "Auto" choice ExoPlayer can switch bitrate on.
        val auto = Stream("auto", videoUrl, PROVIDER, referer, true, subs)
        if (!HlsPlaylist.isMaster(playlist)) return listOf(auto)
        val variants = HlsPlaylist.variants(playlist, videoUrl)
            .map { Stream(if (it.height > 0) it.height.toString() else "0", it.url, PROVIDER, referer, true, subs) }
        return listOf(auto) + variants
    }

    companion object {
        const val PROVIDER = "zoko"
    }
}
