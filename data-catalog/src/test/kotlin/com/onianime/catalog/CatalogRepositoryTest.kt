package com.onianime.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live end-to-end: AniList search -> match to allanime -> resolve a real stream. */
class CatalogRepositoryTest {

    private val repo = CatalogRepository()

    @Test
    fun aniListToAllAnimeToStream() = runBlocking {
        val media = repo.search("frieren").firstOrNull { it.allTitles.any { t -> t.contains("Frieren", true) } }
        assertNotNull("AniList should return Frieren", media)
        media!!
        println("AniList: ${media.displayTitle}  episodes=${media.episodes}  malId=${media.idMal}")

        val allId = repo.resolveAllAnimeId(media, "sub")
        println("matched allanime id: $allId")
        assertNotNull("should match an allanime show", allId)

        val eps = repo.episodes(allId!!, "sub")
        println("allanime episodes: ${eps.size}")
        assertTrue("should list episodes", eps.isNotEmpty())

        val streams = repo.streams(allId, "sub", eps.first())
        streams.forEach { println("  ${it.quality} ${it.provider} ${it.url.take(70)}") }
        assertTrue("should resolve a playable stream", streams.any { it.url.startsWith("http") })
    }

    @Test
    fun homeRowsPopulate() = runBlocking {
        val rows = repo.defaultHomeRows()
        rows.forEach { println("${it.title}: ${it.items.size} (${it.items.take(3).joinToString { m -> m.displayTitle }})") }
        assertTrue("should build several home rows", rows.size >= 3)
        assertTrue("trending row should have posters", rows.first().items.all { it.coverImage != null })
    }
}
