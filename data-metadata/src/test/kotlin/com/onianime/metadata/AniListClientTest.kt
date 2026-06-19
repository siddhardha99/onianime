package com.onianime.metadata

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live test against AniList. Run: ./gradlew :data-metadata:test */
class AniListClientTest {

    private val client = AniListClient()

    @Test
    fun search_returnsRichMetadata() {
        val results = client.search("frieren")
        println("search 'frieren' -> ${results.size} results")
        assertTrue("expected search results", results.isNotEmpty())

        val frieren = results.first()
        println("  ${frieren.displayTitle}  malId=${frieren.idMal}  score=${frieren.scoreOutOfTen}")
        println("  poster=${frieren.coverImage}")
        assertNotNull("should have a poster image", frieren.coverImage)
        assertNotNull("should have a MAL id (needed for AniSkip)", frieren.idMal)
        assertTrue("title should mention Frieren", frieren.allTitles.any { it.contains("Frieren", true) })
    }

    @Test
    fun trending_populatesHomeRow() {
        val trending = client.page(sort = "TRENDING_DESC", perPage = 12)
        println("trending -> ${trending.size}: ${trending.take(5).joinToString { it.displayTitle }}")
        assertTrue("trending row should be populated", trending.size >= 5)
        assertTrue("trending shows should have posters", trending.all { it.coverImage != null })
    }

    @Test
    fun genreRow_filters() {
        val action = client.page(sort = "POPULARITY_DESC", genre = "Action", perPage = 10)
        println("action -> ${action.take(5).joinToString { it.displayTitle }}")
        assertTrue(action.isNotEmpty())
        assertTrue("every show should carry the Action genre", action.all { "Action" in it.genres })
    }
}
