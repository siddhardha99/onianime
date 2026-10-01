package com.onianime.config

import org.junit.Assert.assertEquals
import org.junit.Test

class OniConfigTest {

    /** The shape of onianime.json in the onianime-config repo. */
    private val remoteJson = """
        {
          "schemaVersion": 2,
          "updatedAt": "2026-10-01",
          "agent": "TestAgent/1.0",
          "providers": ["hianime", "zoko", "bogus", "ZOKO"],
          "zoko": {
            "embed": "https://new-zoko.example/e/mal/{mal}/{ep}/{mode}",
            "blobVar": "__Q",
            "xorKey": "otaku-embed-v2",
            "referer": "https://new-zoko.example/"
          },
          "hianime": {
            "base": "https://hianime.example/",
            "search": "/find?q={q}",
            "episodes": "/ajax/eps/{id}",
            "servers": "/ajax/servers?ep={ep}",
            "server": "HD-9"
          },
          "allanime": { "api": "https://api.allanime.example", "keySeed": "seed2" }
        }
    """.trimIndent()

    @Test
    fun fromJson_readsEverySection() {
        val cfg = OniConfig.fromJson(remoteJson)
        assertEquals("TestAgent/1.0", cfg.agent)
        assertEquals(listOf("hianime", "zoko"), cfg.providers) // unknown dropped, case-folded, deduped
        assertEquals(ZokoConfig("https://new-zoko.example/e/mal/{mal}/{ep}/{mode}", "__Q", "otaku-embed-v2", "https://new-zoko.example/"), cfg.zoko)
        assertEquals(HiAnimeConfig("https://hianime.example", "/find?q={q}", "/ajax/eps/{id}", "/ajax/servers?ep={ep}", "HD-9"), cfg.hianime)
        assertEquals("https://api.allanime.example", cfg.allanime.api)
        assertEquals("seed2", cfg.allanime.keySeed)
        assertEquals(AllAnimeConfig.BAKED_IN.refr, cfg.allanime.refr) // missing -> baked-in
    }

    @Test
    fun fromJson_partialOverrideKeepsTheRest() {
        val cfg = OniConfig.fromJson("""{"zoko":{"xorKey":"k2"},"providers":[]}""")
        assertEquals("k2", cfg.zoko.xorKey)
        assertEquals(OniConfig.BAKED_IN.zoko.embed, cfg.zoko.embed)
        assertEquals(OniConfig.BAKED_IN.providers, cfg.providers) // empty list ignored
        assertEquals(OniConfig.BAKED_IN.hianime, cfg.hianime)
        assertEquals(OniConfig.BAKED_IN.agent, cfg.agent)
    }

    @Test
    fun fromJson_wrongTypesAndGarbageFallBack() {
        assertEquals(OniConfig.BAKED_IN, OniConfig.fromJson("not json at all"))
        assertEquals(OniConfig.BAKED_IN, OniConfig.fromJson("[1,2,3]"))
        val cfg = OniConfig.fromJson("""{"agent": 42, "zoko": "nope", "providers": "zoko"}""")
        assertEquals(OniConfig.BAKED_IN, cfg)
    }

    @Test
    fun bakedInMatchesAniCli() {
        // ani-cli 5.1.4: base_api="https://hianime.at", deobfuscate key "otaku-embed-v1", server ZokoAnime.
        assertEquals("https://hianime.at", OniConfig.BAKED_IN.hianime.base)
        assertEquals("otaku-embed-v1", OniConfig.BAKED_IN.zoko.xorKey)
        assertEquals("ZokoAnime", OniConfig.BAKED_IN.hianime.server)
        assertEquals(listOf(OniConfig.ZOKO, OniConfig.HIANIME), OniConfig.BAKED_IN.providers)
    }
}
