package com.onianime.catalog

import com.onianime.allanime.AllAnimeClient
import com.onianime.allanime.StreamResolver
import com.onianime.config.AllAnimeConfig
import com.onianime.config.OniConfig
import com.onianime.hianime.HiAnimeClient
import com.onianime.hianime.HiAnimeParser
import com.onianime.hianime.HiAnimeShow
import com.onianime.hianime.SourceBlockedException
import com.onianime.hianime.ZokoResolver
import com.onianime.metadata.AniListMedia
import com.onianime.stream.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** A show as found on one source: its id there and the episode labels that source has. */
data class SourceShow(
    /** Which source this came from ([OniConfig.ZOKO], [OniConfig.HIANIME], ...). */
    val provider: String,
    val id: String,
    val episodes: List<String>,
    /** Source-specific episode ids keyed by episode label (hianime needs them to list servers). */
    val episodeIds: Map<String, String> = emptyMap(),
)

/** One place streams can come from. Implementations do blocking I/O on [Dispatchers.IO]. */
interface StreamSource {
    val name: String

    /** Finds [media] on this source with its episode list, or null if it isn't there. */
    suspend fun find(media: AniListMedia, mode: String): SourceShow?

    /** Playable streams for [episode] of a show this source found. */
    suspend fun streams(show: SourceShow, media: AniListMedia, mode: String, episode: String): List<Stream>
}

/**
 * ZokoAnime straight from the MAL id AniList already gives us: no search, no title matching.
 * The episode list is what AniList says has aired; an episode the player doesn't have yet just
 * fails at play time and falls through to the next source.
 */
class ZokoSource(private val zoko: ZokoResolver) : StreamSource {
    override val name = OniConfig.ZOKO

    override suspend fun find(media: AniListMedia, mode: String): SourceShow? {
        val mal = media.idMal ?: return null
        val aired = media.airedEpisodes?.takeIf { it > 0 } ?: return null
        return SourceShow(name, mal.toString(), (1..aired).map(Int::toString))
    }

    override suspend fun streams(show: SourceShow, media: AniListMedia, mode: String, episode: String): List<Stream> =
        withContext(Dispatchers.IO) {
            val mal = show.id.toIntOrNull() ?: return@withContext emptyList()
            zoko.resolve(zoko.embedUrl(mal, episode, mode, media.id))
        }
}

/**
 * hianime.at, exactly as ani-cli 5.x uses it: search the title, list episodes, then take the
 * configured server's embed for the episode. Candidates are confirmed by the MAL id inside their
 * ZokoAnime embed URL, so a same-named show can't be picked by mistake.
 */
class HiAnimeSource(
    private val client: HiAnimeClient,
    private val zoko: ZokoResolver,
) : StreamSource {
    override val name = OniConfig.HIANIME

    override suspend fun find(media: AniListMedia, mode: String): SourceShow? = withContext(Dispatchers.IO) {
        val tried = HashSet<String>()
        for (title in media.searchTitles.take(MAX_TITLES)) {
            val query = sanitize(title)
            if (query.length < 2 || !tried.add(query.lowercase())) continue
            ensureActive() // these are blocking calls: stop between them once the user has moved on
            val results = try {
                client.search(query)
            } catch (e: SourceBlockedException) {
                throw e // no point trying other titles; let the caller report it
            } catch (e: Exception) {
                emptyList()
            }
            for (show in rank(results, media).take(MAX_CANDIDATES)) {
                ensureActive()
                val eps = catching { client.episodes(show) }.orEmpty()
                if (eps.isEmpty()) continue
                val mal = media.idMal
                if (mal != null) {
                    val embedMal = catching { client.servers(eps.first().id) }.orEmpty()
                        .firstNotNullOfOrNull { HiAnimeParser.malIdFromEmbed(it.embedUrl) }
                    if (embedMal != null && embedMal != mal) continue // a different show with a similar name
                }
                return@withContext SourceShow(
                    provider = name,
                    id = show.slug,
                    episodes = eps.map { it.number },
                    episodeIds = eps.associate { it.number to it.id },
                )
            }
        }
        null
    }

    override suspend fun streams(show: SourceShow, media: AniListMedia, mode: String, episode: String): List<Stream> =
        withContext(Dispatchers.IO) {
            val episodeId = show.episodeIds[episode] ?: return@withContext emptyList()
            val embed = client.embedFor(episodeId, mode) ?: return@withContext emptyList()
            zoko.resolve(embed)
        }

    /** Exact title matches first, then prefix/contains matches, otherwise the site's own order. */
    private fun rank(results: List<HiAnimeShow>, media: AniListMedia): List<HiAnimeShow> {
        val wanted = media.allTitles.map(::norm).filter { it.isNotEmpty() }.toSet()
        fun score(s: HiAnimeShow): Int {
            val t = norm(s.title)
            return when {
                t in wanted -> 3
                wanted.any { w -> t.startsWith(w) || w.startsWith(t) } -> 2
                wanted.any { w -> t.contains(w) || w.contains(t) } -> 1
                else -> 0
            }
        }
        return results.withIndex().sortedWith(compareByDescending<IndexedValue<HiAnimeShow>> { score(it.value) }.thenBy { it.index })
            .map { it.value }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun sanitize(title: String): String =
        title.replace(Regex("[^A-Za-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val MAX_TITLES = 4
        const val MAX_CANDIDATES = 3
    }
}

/**
 * The original AllAnime scraper (ani-cli 4.14). AllAnime stopped serving streams on its website in
 * summer 2026, so this is off by default; list "allanime" in the config's providers to try it.
 */
class AllAnimeSource(config: AllAnimeConfig) : StreamSource {
    override val name = OniConfig.ALLANIME
    private val client = AllAnimeClient(config)
    private val resolver = StreamResolver(client, config)

    override suspend fun find(media: AniListMedia, mode: String): SourceShow? = withContext(Dispatchers.IO) {
        val targetEps = media.episodes
        val tried = HashSet<String>()
        for (title in media.allTitles) {
            val q = title.replace(Regex("[^A-Za-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
            if (q.length < 2 || !tried.add(q)) continue
            val results = catching { client.search(q, mode) }.orEmpty()
            if (results.isEmpty()) continue
            // allanime's names are unreliable ("1P" for One Piece), so confirm by episode count.
            val pick = if (targetEps != null) {
                results.minByOrNull { abs((it.episodes.toIntOrNull() ?: 0) - targetEps) }!!
            } else {
                results.first()
            }
            val eps = catching { client.episodesList(pick.id, mode) }.orEmpty()
            if (eps.isNotEmpty()) return@withContext SourceShow(name, pick.id, eps)
        }
        null
    }

    override suspend fun streams(show: SourceShow, media: AniListMedia, mode: String, episode: String): List<Stream> =
        resolver.resolve(show.id, mode, episode)
}
