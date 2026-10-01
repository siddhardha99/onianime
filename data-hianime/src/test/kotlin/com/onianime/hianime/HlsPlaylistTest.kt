package com.onianime.hianime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistTest {

    private val masterUrl = "https://cdn.example.net/hls/abc/master.m3u8?token=x"

    private val master = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
        360/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
        https://cdn2.example.net/hls/abc/1080/index.m3u8
        #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=2000000,BANDWIDTH=2500000,RESOLUTION=1280x720
        /hls/abc/720/index.m3u8
        #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=100000,RESOLUTION=1280x720,URI="iframes.m3u8"
    """.trimIndent()

    @Test
    fun variants_sortedAndResolved() {
        assertTrue(HlsPlaylist.isPlaylist(master))
        assertTrue(HlsPlaylist.isMaster(master))
        val v = HlsPlaylist.variants(master, masterUrl)
        assertEquals(listOf(1080, 720, 360), v.map { it.height })
        assertEquals("https://cdn2.example.net/hls/abc/1080/index.m3u8", v[0].url)
        assertEquals("https://cdn.example.net/hls/abc/720/index.m3u8", v[1].url)
        assertEquals("https://cdn.example.net/hls/abc/360/index.m3u8", v[2].url)
        assertEquals(2_500_000L, v[1].bandwidth) // BANDWIDTH, not AVERAGE-BANDWIDTH
    }

    @Test
    fun mediaPlaylistIsNotMaster() {
        val media = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg0.ts\n#EXT-X-ENDLIST\n"
        assertTrue(HlsPlaylist.isPlaylist(media))
        assertFalse(HlsPlaylist.isMaster(media))
        assertTrue(HlsPlaylist.variants(media, masterUrl).isEmpty())
    }
}
