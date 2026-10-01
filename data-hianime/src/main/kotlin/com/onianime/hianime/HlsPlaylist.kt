package com.onianime.hianime

/** One rendition of an HLS master playlist. */
data class HlsVariant(val height: Int, val bandwidth: Long, val url: String)

/**
 * Minimal HLS master-playlist reader: enough to offer fixed qualities next to the adaptive "Auto"
 * stream (ani-cli does the same with sed over `#EXT-X-STREAM-INF` lines).
 */
object HlsPlaylist {

    private val RESOLUTION = Regex("""RESOLUTION=\d+x(\d+)""")
    private val BANDWIDTH = Regex("""(?<![-\w])BANDWIDTH=(\d+)""")

    fun isPlaylist(text: String): Boolean = text.contains("#EXTM3U")

    fun isMaster(text: String): Boolean = text.contains("#EXT-X-STREAM-INF")

    /** Variants sorted best-first, one per height (the highest bandwidth wins a tie). */
    fun variants(text: String, playlistUrl: String): List<HlsVariant> {
        val out = mutableListOf<HlsVariant>()
        var pending: Pair<Int, Long>? = null
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    val h = RESOLUTION.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val bw = BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    pending = h to bw
                }
                pending != null && line.isNotEmpty() && !line.startsWith("#") -> {
                    out += HlsVariant(pending.first, pending.second, EmbedDecoder.resolve(playlistUrl, line))
                    pending = null
                }
            }
        }
        return out.sortedWith(compareByDescending<HlsVariant> { it.height }.thenByDescending { it.bandwidth })
            .distinctBy { it.height }
    }
}
