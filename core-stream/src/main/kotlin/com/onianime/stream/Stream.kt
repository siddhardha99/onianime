package com.onianime.stream

/**
 * A subtitle file that is delivered next to the video (not burned in), e.g. the English WebVTT
 * track the ZokoAnime player ships. The player side-loads these so the CC button can toggle them.
 */
data class SubtitleTrack(
    val url: String,
    /** Human label shown in the player, e.g. "English". */
    val label: String,
    /** Language code if known, e.g. "en". */
    val language: String? = null,
    /** The track the source marks as its default (ani-cli picks this one). */
    val isDefault: Boolean = false,
) {
    /** MIME type guessed from the file extension; WebVTT unless it is clearly something else. */
    val mimeType: String
        get() {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return when {
                path.endsWith(".srt") -> "application/x-subrip"
                path.endsWith(".ass") || path.endsWith(".ssa") -> "text/x-ssa"
                path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp") -> "application/ttml+xml"
                else -> "text/vtt"
            }
        }
}

/** A resolved, playable stream, independent of which source produced it. */
data class Stream(
    /** "auto" for an adaptive master playlist, otherwise the height as a string, e.g. "1080"; "0" when unknown. */
    val quality: String,
    val url: String,
    /** Which source/host produced it (e.g. "zoko", "wixmp"). Informational only. */
    val provider: String,
    /** Referer header the player must send, or null if none is required. */
    val referer: String?,
    val isHls: Boolean,
    /** Side-loaded subtitle files for this stream (empty when subtitles are burned in). */
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** Any extra HTTP headers the player must send with every request (e.g. Origin). */
    val headers: Map<String, String> = emptyMap(),
) {
    val heightOrZero: Int get() = quality.filter { it.isDigit() }.toIntOrNull() ?: 0

    /** Referer + extra headers, ready to hand to the player's HTTP data source. */
    val requestHeaders: Map<String, String>
        get() = buildMap {
            referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            putAll(headers)
        }
}
