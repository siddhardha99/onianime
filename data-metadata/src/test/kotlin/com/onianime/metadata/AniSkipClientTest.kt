package com.onianime.metadata

import org.junit.Assert.assertTrue
import org.junit.Test

/** Live test against AniSkip. Run: ./gradlew :data-metadata:test */
class AniSkipClientTest {

    @Test
    fun fetchesSkipTimes() {
        // Jujutsu Kaisen (MAL 40748) episode 1 — reliably has op/ed data in AniSkip.
        val intervals = AniSkipClient().skipTimes(malId = 40748, episode = 1)
        intervals.forEach { println("${it.type}: ${it.startMs}ms..${it.endMs}ms  (${it.label})") }

        assertTrue("expected AniSkip intervals for JJK ep1", intervals.isNotEmpty())
        assertTrue("intervals should be well-formed", intervals.all { it.endMs > it.startMs })
    }

    @Test
    fun unknownAnimeReturnsEmpty() {
        // Nonexistent MAL id -> graceful empty, not a crash.
        val intervals = AniSkipClient().skipTimes(malId = 999999999, episode = 1)
        assertTrue(intervals.isEmpty())
    }
}
