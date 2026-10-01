package com.onianime.catalog

import com.onianime.metadata.AniListMedia
import com.onianime.stream.Stream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live end-to-end: AniList search -> find on a stream source -> resolve a real stream. */
class CatalogRepositoryTest {

    private val repo = CatalogRepository()

    @Test
    fun aniListToSourceToStream() = runBlocking {
        val media = repo.search("frieren").firstOrNull { it.allTitles.any { t -> t.contains("Frieren", true) } }
        assertNotNull("AniList should return Frieren", media)
        media!!
        println("AniList: ${media.displayTitle}  episodes=${media.episodes}  aired=${media.airedEpisodes}  malId=${media.idMal}")

        val found = repo.findSource(media, "sub")
        found.errors.forEach { println("  find error: $it") }
        val show = found.show
        println("source: ${show?.provider} id=${show?.id} episodes=${show?.episodes?.size}")
        assertNotNull("a source should have the show", show)
        assertTrue("should list episodes", show!!.episodes.isNotEmpty())

        val result = repo.streams(media, show, "sub", show.episodes.first())
        result.errors.forEach { println("  error: $it") }
        result.streams.forEach { println("  ${it.quality} ${it.provider} subs=${it.subtitles.size} ${it.url.take(80)}") }
        assertTrue("should resolve a playable stream", result.streams.any { it.url.startsWith("http") })
    }

    @Test
    fun homeRowsPopulate() = runBlocking {
        val rows = repo.defaultHomeRows()
        rows.forEach { println("${it.title}: ${it.items.size} (${it.items.take(3).joinToString { m -> m.displayTitle }})") }
        assertTrue("should build several home rows", rows.size >= 3)
        assertTrue("trending row should have posters", rows.first().items.all { it.coverImage != null })
    }

    /** Offline: a broken first source falls through to the next one for the same episode. */
    @Test
    fun streamsFallBackToNextSource() = runBlocking {
        val good = Stream("auto", "https://ok.example/master.m3u8", "b", null, true)
        val broken = FakeSource("a", streams = emptyList(), fail = true)
        val working = FakeSource("b", streams = listOf(good))
        val offline = CatalogRepository(sources = listOf(broken, working))

        val show = offline.findSource(MEDIA, "sub").show!!
        assertEquals("a", show.provider)
        val result = offline.streams(MEDIA, show, "sub", "1")
        assertEquals("b", result.show.provider)
        assertEquals(listOf(good), result.streams)
        assertTrue(result.errors.single().startsWith("a: "))
    }

    private class FakeSource(
        override val name: String,
        private val streams: List<Stream>,
        private val fail: Boolean = false,
    ) : StreamSource {
        override suspend fun find(media: AniListMedia, mode: String) = SourceShow(name, "x", listOf("1", "2"))
        override suspend fun streams(show: SourceShow, media: AniListMedia, mode: String, episode: String): List<Stream> =
            if (fail) throw java.io.IOException("boom") else streams
    }

    private companion object {
        val MEDIA = AniListMedia(
            id = 154587, idMal = 52991, romaji = "Sousou no Frieren", english = "Frieren: Beyond Journey's End",
            native = null, synonyms = emptyList(), episodes = 28, format = "TV", status = "FINISHED",
            seasonYear = 2023, averageScore = 90, genres = emptyList(), description = null,
            coverImage = null, bannerImage = null, coverColor = null,
        )
    }
}
