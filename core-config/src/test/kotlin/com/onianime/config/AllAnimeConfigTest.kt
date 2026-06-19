package com.onianime.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AllAnimeConfigTest {

    /** The exact JSON committed to siddhardha99/onianime-config. */
    private val remoteJson = """
        {
          "schemaVersion": 1,
          "updatedAt": "2026-06-19",
          "agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0",
          "allanime": {
            "base": "allanime.day",
            "api": "https://api.allanime.day",
            "refr": "https://youtu-chan.com",
            "keySeed": "Xot36i3lK3:v1",
            "queryHash": "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec",
            "providerPatterns": {
              "wixmp": "Default", "youtube": "Yt-mp4", "sharepoint": "S-mp4", "mp4upload": "Mp4"
            }
          }
        }
    """.trimIndent()

    @Test
    fun fromJson_parsesRemoteConfig() {
        val cfg = AllAnimeConfig.fromJson(remoteJson)
        assertEquals("allanime.day", cfg.base)
        assertEquals("https://api.allanime.day", cfg.api)
        assertEquals("https://youtu-chan.com", cfg.refr)
        assertEquals("Xot36i3lK3:v1", cfg.keySeed)
        assertEquals(
            "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec",
            cfg.queryHash,
        )
        assertEquals("Default", cfg.providerPatterns["wixmp"])
        assertEquals("Mp4", cfg.providerPatterns["mp4upload"])
    }

    @Test
    fun fromJson_fallsBackOnMissingFields() {
        val cfg = AllAnimeConfig.fromJson("""{"keySeed":"newseed"}""")
        assertEquals("newseed", cfg.keySeed)                       // overridden
        assertEquals(AllAnimeConfig.BAKED_IN.base, cfg.base)        // fell back
        assertEquals(AllAnimeConfig.BAKED_IN.providerPatterns, cfg.providerPatterns)
    }

    @Test
    fun fromJson_garbageReturnsBakedIn() {
        assertEquals(AllAnimeConfig.BAKED_IN, AllAnimeConfig.fromJson("not json at all"))
    }
}
