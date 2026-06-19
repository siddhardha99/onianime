package com.onianime.allanime

/** A show returned by search (ani-cli's search_anime output line). */
data class SearchResult(
    val id: String,
    val title: String,
    val episodes: String,
)

/** A raw (sourceName, sourceUrl) pair parsed out of the decrypted episode response. */
data class Source(
    val name: String,
    val url: String,
)

/** A resolved, playable stream. */
data class Stream(
    /** numeric height as a string, e.g. "1080"; "0" when unknown */
    val quality: String,
    val url: String,
    val provider: String,
    /** Referer header the player must send, or null if none required (e.g. sharepoint). */
    val referer: String?,
    val isHls: Boolean,
) {
    val heightOrZero: Int get() = quality.filter { it.isDigit() }.toIntOrNull() ?: 0
}
