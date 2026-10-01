package com.onianime.hianime

import com.onianime.config.OniConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end against the real sites: proves the current config still resolves a playable stream.
 * Requires network. Run with:  ./gradlew :data-hianime:test --tests "*LiveZokoTest*"
 *
 * If this fails after a site change, fix the matching field in onianime-config's onianime.json
 * (and BAKED_IN) and re-run.
 */
class LiveZokoTest {

    private val config = OniConfig.BAKED_IN
    private val http = sourceHttpClient()

    @Test
    fun zokoByMalId_resolvesFrierenEpisode1() {
        val zoko = ZokoResolver(config, http)
        val embed = zoko.embedUrl(malId = 52991, episode = "1", mode = "sub")
        println("embed: $embed")
        val streams = zoko.resolve(embed)
        streams.forEach { println("  ${it.quality}  hls=${it.isHls}  refr=${it.referer}\n     ${it.url}") }
        streams.firstOrNull()?.subtitles?.forEach { println("  sub: ${it.label} (${it.language}) default=${it.isDefault} ${it.url}") }
        assertTrue("expected an http(s) stream", streams.any { it.url.startsWith("http") })
    }

    @Test
    fun hianimeSearch_findsZokoEmbed() {
        val site = HiAnimeClient(config, http)
        val show = site.search("Frieren").firstOrNull()
        println("search -> $show")
        assertTrue("search should find Frieren", show != null)
        val eps = site.episodes(show!!)
        println("episodes: ${eps.size} (first=${eps.firstOrNull()})")
        assertTrue("episode list should not be empty", eps.isNotEmpty())
        val embed = site.embedFor(eps.first().id, "sub")
        println("embed: $embed")
        assertTrue("server list should have a ${config.hianime.server} embed", embed != null)
    }
}
