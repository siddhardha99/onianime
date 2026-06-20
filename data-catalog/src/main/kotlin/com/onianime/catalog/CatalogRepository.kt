package com.onianime.catalog

import com.onianime.allanime.AllAnimeClient
import com.onianime.allanime.Stream
import com.onianime.allanime.StreamResolver
import com.onianime.config.AllAnimeConfig
import com.onianime.metadata.AniListClient
import com.onianime.metadata.AniListMedia
import com.onianime.metadata.AniSkipClient
import com.onianime.metadata.SkipInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The bridge between AniList (browse/metadata) and allanime (streams).
 *
 * Matching is deliberately AniList -> allanime: we take AniList's reliable titles and search
 * allanime for each, confirming the right entry by episode count — because allanime's own names are
 * unreliable (it stores One Piece as "1P"). Returns the allanime show id to feed the scraper.
 */
class CatalogRepository(
    private val config: AllAnimeConfig = AllAnimeConfig.BAKED_IN,
    private val aniList: AniListClient = AniListClient(),
    private val allClient: AllAnimeClient = AllAnimeClient(config),
    private val aniSkip: AniSkipClient = AniSkipClient(),
) {
    private val resolver = StreamResolver(allClient, config)

    // ---- Browse (AniList) ----

    suspend fun search(query: String): List<AniListMedia> =
        withContext(Dispatchers.IO) { aniList.search(query) }

    suspend fun homeRow(sort: String, genre: String? = null, perPage: Int = 20): List<AniListMedia> =
        withContext(Dispatchers.IO) { aniList.page(sort = sort, genre = genre, perPage = perPage) }

    /** The standard onianime home rows (Continue Watching is layered on top by the app from history). */
    suspend fun defaultHomeRows(): List<HomeRow> = withContext(Dispatchers.IO) {
        listOf(
            HomeRow("Trending Now", aniList.page(sort = "TRENDING_DESC", perPage = 20)),
            HomeRow("Popular", aniList.page(sort = "POPULARITY_DESC", perPage = 20)),
            HomeRow("Recently Updated", aniList.page(sort = "UPDATED_AT_DESC", perPage = 20)),
            HomeRow("Action", aniList.page(sort = "POPULARITY_DESC", genre = "Action", perPage = 20)),
            HomeRow("Romance", aniList.page(sort = "POPULARITY_DESC", genre = "Romance", perPage = 20)),
        ).filter { it.items.isNotEmpty() }
    }

    // ---- Match + stream (allanime) ----

    /** Resolve an AniList show to its allanime id, confirming by episode count. */
    suspend fun resolveAllAnimeId(media: AniListMedia, mode: String): String? = withContext(Dispatchers.IO) {
        val targetEps = media.episodes
        val tried = HashSet<String>()
        for (title in media.allTitles) {
            val q = sanitize(title)
            if (q.length < 2 || !tried.add(q)) continue
            val results = runCatching { allClient.search(q, mode) }.getOrDefault(emptyList())
            if (results.isEmpty()) continue
            return@withContext if (targetEps != null) {
                results.minByOrNull { abs((it.episodes.toIntOrNull() ?: 0) - targetEps) }!!.id
            } else {
                results.first().id // allanime returns by relevance
            }
        }
        null
    }

    suspend fun episodes(allAnimeId: String, mode: String): List<String> =
        withContext(Dispatchers.IO) { allClient.episodesList(allAnimeId, mode) }

    suspend fun streams(allAnimeId: String, mode: String, episode: String): List<Stream> =
        resolver.resolve(allAnimeId, mode, episode)

    /** AniSkip op/ed intervals for a show's episode (empty if AniList has no MAL id or no data). */
    suspend fun skipTimes(malId: Int, episode: Int, episodeLengthSec: Int = 0): List<SkipInterval> =
        withContext(Dispatchers.IO) {
            runCatching { aniSkip.skipTimes(malId, episode, episodeLengthSec) }.getOrDefault(emptyList())
        }

    private fun sanitize(title: String): String =
        title.replace(Regex("[^A-Za-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

data class HomeRow(val title: String, val items: List<AniListMedia>)
