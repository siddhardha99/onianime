package com.onianime.allanime

import com.onianime.config.AllAnimeConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0 end-to-end proof: hits the real allanime API, decrypts the response, decodes the provider
 * obfuscation, and resolves an actual playable stream URL — the make-or-break of the whole port.
 *
 * Requires network. Run with:  ./gradlew :data-allanime:test --tests "*LiveScrapeTest*"
 */
class LiveScrapeTest {

    @Test
    fun resolvesRealStreamForOnePiece() = runBlocking {
        val config = AllAnimeConfig.BAKED_IN
        val client = AllAnimeClient(config)

        val results = client.search("one piece", "sub")
        println("search returned ${results.size} shows")
        assertTrue("search should return results", results.isNotEmpty())

        // Prefer the long-running series (most episodes) to dodge specials/movies.
        val show = results.maxByOrNull { it.episodes.toIntOrNull() ?: 0 } ?: results.first()
        println("picked: ${show.title}  id=${show.id}  episodes=${show.episodes}")

        val episodes = client.episodesList(show.id, "sub")
        println("episode count: ${episodes.size}  first=${episodes.firstOrNull()}")
        assertTrue("episode list should not be empty", episodes.isNotEmpty())

        val ep = episodes.first()
        println("resolving streams for episode $ep ...")
        val streams = StreamResolver(client, config).resolve(show.id, "sub", ep)

        println("resolved ${streams.size} streams:")
        streams.forEach { println("  ${it.quality}  ${it.provider}  hls=${it.isHls}  refr=${it.referer}\n     ${it.url}") }

        assertTrue(
            "expected at least one http(s) stream URL",
            streams.any { it.url.startsWith("http") },
        )
    }
}
