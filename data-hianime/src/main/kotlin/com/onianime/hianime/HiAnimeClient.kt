package com.onianime.hianime

import com.onianime.config.OniConfig
import okhttp3.OkHttpClient
import java.net.URLEncoder

/**
 * hianime.at, the site ani-cli 5.x scrapes: search -> episode list -> per-episode server list,
 * where the configured server's `data-hash` is the base64 of its embed URL.
 */
class HiAnimeClient(
    private val config: OniConfig,
    httpClient: OkHttpClient = Http.defaultClient(),
) {
    private val http = Http(httpClient, config.agent)
    private val site get() = config.hianime

    fun search(query: String): List<HiAnimeShow> =
        HiAnimeParser.search(http.get(site.base + site.searchPath.replace("{q}", URLEncoder.encode(query.trim(), "UTF-8"))))

    fun episodes(show: HiAnimeShow): List<HiAnimeEpisode> =
        HiAnimeParser.episodes(http.get(site.base + site.episodesPath.replace("{id}", show.id)))

    fun servers(episodeId: String): List<HiAnimeServer> =
        HiAnimeParser.servers(http.get(site.base + site.serversPath.replace("{ep}", episodeId)))

    /** Embed URL of the configured server for [episodeId] in [mode] (sub/dub), or null if it has none. */
    fun embedFor(episodeId: String, mode: String): String? {
        val all = servers(episodeId).filter { it.type == mode.lowercase() }
        return (all.firstOrNull { it.name.equals(site.server, ignoreCase = true) }
            // A renamed server still works as long as it is the same player (its URL has the MAL id).
            ?: all.firstOrNull { HiAnimeParser.malIdFromEmbed(it.embedUrl) != null })?.embedUrl
    }
}
