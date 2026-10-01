package com.onianime.hianime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsers run against responses captured from hianime.at (see src/test/resources). */
class HiAnimeParserTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader.getResource(name)) { "missing test resource $name" }.readText()

    @Test
    fun search_readsResultsAndIgnoresSidebar() {
        val shows = HiAnimeParser.search(resource("hianime_search.html"))
        assertEquals(listOf("frieren-beyond-journeys-end-481", "frieren-beyond-journeys-end-season-2-808"), shows.map { it.slug })
        assertEquals("Frieren: Beyond Journey's End", shows[0].title) // &#039; decoded
        assertEquals("481", shows[0].id)
        assertEquals("808", shows[1].id)
    }

    @Test
    fun episodes_readsNumberAndId() {
        val eps = HiAnimeParser.episodes(resource("hianime_episodes.json"))
        assertEquals(2, eps.size)
        assertEquals(HiAnimeEpisode("1", "9227", "The Journey's End"), eps[0])
        assertEquals("2", eps[1].number)
        assertEquals("9228", eps[1].id)
    }

    @Test
    fun servers_decodesEmbedHashes() {
        val servers = HiAnimeParser.servers(resource("hianime_servers.json"))
        assertEquals(8, servers.size)
        val zokoSub = servers.first { it.type == "sub" && it.name == "ZokoAnime" }
        assertEquals("https://zokoanime.video/stream/mal/52991/1/sub", zokoSub.embedUrl)
        val zokoDub = servers.first { it.type == "dub" && it.name == "ZokoAnime" }
        assertEquals("https://zokoanime.video/stream/mal/52991/1/dub", zokoDub.embedUrl)
        assertTrue(servers.any { it.name == "HD-1" && it.embedUrl.startsWith("https://megaplay.buzz/") })
    }

    @Test
    fun malIdFromEmbed() {
        assertEquals(52991, HiAnimeParser.malIdFromEmbed("https://zokoanime.video/stream/mal/52991/1/sub"))
        assertNull(HiAnimeParser.malIdFromEmbed("https://megaplay.buzz/stream/s-2/107257/sub?s=tcdn"))
    }

    @Test
    fun zokoTemplateMatchesWhatHianimeHandsOut() {
        // The baked-in template must produce exactly the URL hianime.at's server list gives for
        // Frieren (MAL 52991) episode 1, or the direct-by-MAL shortcut would be pointing elsewhere.
        val resolver = ZokoResolver(com.onianime.config.OniConfig.BAKED_IN)
        assertEquals("https://zokoanime.video/stream/mal/52991/1/sub", resolver.embedUrl(52991, "1", "sub"))
        assertEquals("https://zokoanime.video/stream/mal/52991/1/dub", resolver.embedUrl(52991, "1", "DUB"))
    }

    @Test
    fun attr_handlesQuotesAndPrefixes() {
        val attrs = """title='A "quoted" name' data-title="nope" class="x" data-id="7""""
        assertEquals("A \"quoted\" name", HiAnimeParser.attr(attrs, "title"))
        assertEquals("7", HiAnimeParser.attr(attrs, "data-id"))
        assertNull(HiAnimeParser.attr(attrs, "id"))
    }
}
