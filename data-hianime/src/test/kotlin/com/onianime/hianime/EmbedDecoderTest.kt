package com.onianime.hianime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedDecoderTest {

    private val key = "otaku-embed-v1"
    private val page = "https://zokoanime.video/stream/mal/52991/1/sub"

    /** Player config in the shape ani-cli 5.x reads: an m3u8 "src" and a "subtitles" array. */
    private val playerJson = """
        {"id":"52991-1-sub","sources":[{"src":"https://cdn.example.net/hls/abc/master.m3u8","type":"hls"}],
         "subtitles":[
           {"src":"https://cdn.example.net/subs/abc/thumbnails.vtt","label":"thumbnails","kind":"thumbnails"},
           {"src":"https://cdn.example.net/subs/abc/eng.vtt","label":"English [CC]","default":true},
           {"src":"/subs/abc/spa.vtt","label":"Spanish","default":false}
         ],
         "intro":{"start":0,"end":90}}
    """.trimIndent()

    /** The inverse of EmbedDecoder.deobfuscate, to build realistic embed pages. */
    private fun obfuscate(text: String, xorKey: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val k = xorKey.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor k[i % k.size].toInt()).toByte() }
        return java.util.Base64.getEncoder().encodeToString(out)
    }

    @Test
    fun deobfuscate_isRepeatingXorOverBase64() {
        // Byte-level check against ani-cli's key: 'o' (111) XOR 'o' = 0, 't' XOR 't' = 0, ...
        val blob = java.util.Base64.getEncoder().encodeToString(ByteArray(14))
        assertEquals(key, EmbedDecoder.deobfuscate(blob, key))
        assertEquals(playerJson, EmbedDecoder.deobfuscate(obfuscate(playerJson, key), key))
    }

    @Test
    fun extractBlob_findsWindowVariable() {
        val blob = obfuscate(playerJson, key)
        val html = """<!doctype html><html><head><title>Player</title></head><body><div id="player"></div>
            <script>var x__P = "nope"; window.__P="$blob";window.__S=1;</script>
            <script src="/static/player.js"></script></body></html>"""
        assertEquals(blob, EmbedDecoder.extractBlob(html, "__P"))
        assertEquals(blob, EmbedDecoder.extractBlob("<script>window['__P'] = '$blob'</script>", "__P"))
        assertEquals(blob, EmbedDecoder.extractBlob("<script>window . __P = `$blob`</script>", "window.__P"))
        assertNull(EmbedDecoder.extractBlob("<script>window.__Q=\"abc\"</script>", "__P"))
    }

    @Test
    fun decodeBase64_toleratesUrlSafeMissingPaddingAndEscapes() {
        val bytes = byteArrayOf(-5, -17, -1, 0, 65, 66)
        val std = java.util.Base64.getEncoder().encodeToString(bytes) // "++//AEFC"
        assertTrue(bytes.contentEquals(EmbedDecoder.decodeBase64(std)))
        assertTrue(bytes.contentEquals(EmbedDecoder.decodeBase64(std.replace("/", "\\/"))))
        val url = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertTrue(bytes.contentEquals(EmbedDecoder.decodeBase64(url)))
    }

    @Test
    fun decodeBase64_matchesJdkOnRandomData() {
        val rnd = java.util.Random(7)
        repeat(300) { len ->
            val bytes = ByteArray(len).also(rnd::nextBytes)
            val std = java.util.Base64.getEncoder().encodeToString(bytes)
            val url = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            assertTrue("std len=$len", bytes.contentEquals(EmbedDecoder.decodeBase64(std)))
            assertTrue("url len=$len", bytes.contentEquals(EmbedDecoder.decodeBase64(url)))
        }
    }

    @Test
    fun parse_languageNamesBecomeCodes() {
        val j = """{"src":"https://h.example/m.m3u8","subtitles":[
            {"src":"https://h.example/a.vtt","label":"English","lang":"English"},
            {"src":"https://h.example/b.vtt","label":"Portuguese (Brazil)","srclang":"pt-BR"},
            {"src":"https://h.example/c.vtt","label":"Subs","language":"eng"}]}"""
        val subs = EmbedDecoder.parse(j, page).subtitles
        assertEquals(listOf("en", "pt-br", "eng"), subs.map { it.language })
    }

    @Test
    fun parse_findsStreamAndSubtitles() {
        val src = EmbedDecoder.parse(playerJson, page)
        assertEquals(listOf("https://cdn.example.net/hls/abc/master.m3u8"), src.videoUrls)
        assertEquals(2, src.subtitles.size) // thumbnails track dropped
        val eng = src.subtitles.first { it.isDefault }
        assertEquals("https://cdn.example.net/subs/abc/eng.vtt", eng.url)
        assertEquals("en", eng.language)
        assertEquals("text/vtt", eng.mimeType)
        assertEquals("https://zokoanime.video/subs/abc/spa.vtt", src.subtitles.first { !it.isDefault }.url)
    }

    @Test
    fun parse_flatLayoutAndRegexFallback() {
        val flat = """{"src":"https://h.example/v/1/index.m3u8?t=9&e=1","subtitles":[{"file":"https://h.example/s/en.vtt","label":"English"}]}"""
        val a = EmbedDecoder.parse(flat, page)
        assertEquals(listOf("https://h.example/v/1/index.m3u8?t=9&e=1"), a.videoUrls)
        assertEquals("en", a.subtitles.single().language)

        // Not valid JSON (trailing junk): fall back to ani-cli's regex.
        val broken = """{"src":"https:\/\/h.example\/v\/2\/master.m3u8","x":}}"""
        assertEquals(listOf("https://h.example/v/2/master.m3u8"), EmbedDecoder.parse(broken, page).videoUrls)
    }

    @Test
    fun parse_prefersLastM3u8LikeAniCli() {
        val two = """{"sources":[{"src":"https://a.example/1.m3u8"},{"src":"https://b.example/2.m3u8"}]}"""
        assertEquals(listOf("https://b.example/2.m3u8", "https://a.example/1.m3u8"), EmbedDecoder.parse(two, page).videoUrls)
    }
}
