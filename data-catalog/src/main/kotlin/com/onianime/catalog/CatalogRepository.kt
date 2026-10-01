package com.onianime.catalog

import com.onianime.config.OniConfig
import com.onianime.hianime.HiAnimeClient
import com.onianime.hianime.ZokoResolver
import com.onianime.hianime.sourceHttpClient
import com.onianime.metadata.AniListClient
import com.onianime.metadata.AniListMedia
import com.onianime.metadata.AniSkipClient
import com.onianime.metadata.SkipInterval
import com.onianime.stream.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** The source that has a show (null if none did) and why the others didn't. */
data class FoundSource(
    val show: SourceShow?,
    val errors: List<String> = emptyList(),
)

/** Streams for one episode plus the source that produced them (and why the others failed). */
data class ResolvedStreams(
    val show: SourceShow,
    val streams: List<Stream>,
    val errors: List<String> = emptyList(),
)

/**
 * The bridge between AniList (browse/metadata) and the stream sources.
 *
 * Sources are tried in the order `providers` lists them in the remote config. By default that is
 * ZokoAnime by MAL id first (no title matching needed), then hianime.at the way ani-cli does it.
 */
class CatalogRepository(
    private val config: OniConfig = OniConfig.BAKED_IN,
    private val aniList: AniListClient = AniListClient(),
    private val aniSkip: AniSkipClient = AniSkipClient(),
    sources: List<StreamSource>? = null,
) {
    private val sources: List<StreamSource> = sources ?: buildSources(config)

    // ---- Browse (AniList) ----

    suspend fun search(query: String): List<AniListMedia> =
        withContext(Dispatchers.IO) { aniList.search(query) }

    suspend fun homeRow(sort: String, genre: String? = null, perPage: Int = 20): List<AniListMedia> =
        withContext(Dispatchers.IO) { aniList.page(sort = sort, genre = genre, perPage = perPage) }

    /** Fresh AniList data for one show (saved shows are snapshots; airing counts go stale). */
    suspend fun media(id: Int): AniListMedia? = withContext(Dispatchers.IO) { aniList.byId(id) }

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

    // ---- Match + stream ----

    /** The show on the first source (in config order) that has it with at least one episode. */
    suspend fun findSource(media: AniListMedia, mode: String): FoundSource {
        val errors = mutableListOf<String>()
        for (source in sources) {
            val show = catching({ errors += describe(source, it) }) { source.find(media, mode) }
            if (show != null && show.episodes.isNotEmpty()) return FoundSource(show, errors)
        }
        return FoundSource(null, errors)
    }

    /**
     * Streams for [episode]: from [show]'s own source first, then from every other source in
     * order, so one source breaking doesn't stop playback if another still works.
     */
    suspend fun streams(media: AniListMedia, show: SourceShow, mode: String, episode: String): ResolvedStreams {
        val errors = mutableListOf<String>()
        val ordered = sources.sortedBy { if (it.name == show.provider) 0 else 1 }
        for (source in ordered) {
            val target = if (source.name == show.provider) show else {
                catching({ errors += describe(source, it) }) { source.find(media, mode) }
                    ?.takeIf { episode in it.episodes }
                    ?: continue
            }
            val streams = catching({ errors += describe(source, it) }) { source.streams(target, media, mode, episode) }
                .orEmpty()
                .filter { it.url.startsWith("http") }
            if (streams.isNotEmpty()) return ResolvedStreams(target, streams, errors)
        }
        return ResolvedStreams(show, emptyList(), errors)
    }

    /** AniSkip op/ed intervals for a show's episode (empty if AniList has no MAL id or no data). */
    suspend fun skipTimes(malId: Int, episode: Int, episodeLengthSec: Int = 0): List<SkipInterval> =
        withContext(Dispatchers.IO) {
            runCatching { aniSkip.skipTimes(malId, episode, episodeLengthSec) }.getOrDefault(emptyList())
        }

    private fun describe(source: StreamSource, e: Throwable) =
        "${source.name}: ${e.message ?: e::class.simpleName}"

    companion object {
        /** Sources in the config's order, sharing one HTTP client. */
        fun buildSources(config: OniConfig): List<StreamSource> {
            val http = sourceHttpClient()
            val zoko = ZokoResolver(config, http)
            return config.providers.mapNotNull { name ->
                when (name) {
                    OniConfig.ZOKO -> ZokoSource(zoko)
                    OniConfig.HIANIME -> HiAnimeSource(HiAnimeClient(config, http), zoko)
                    OniConfig.ALLANIME -> AllAnimeSource(config.allanime)
                    else -> null
                }
            }
        }
    }
}

data class HomeRow(val title: String, val items: List<AniListMedia>)

/**
 * runCatching that lets coroutine cancellation through, so a cancelled lookup stops instead of
 * carrying on to the next source with stale input.
 */
internal inline fun <T> catching(onError: (Throwable) -> Unit = {}, block: () -> T): T? =
    try {
        block()
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        onError(e)
        null
    }
