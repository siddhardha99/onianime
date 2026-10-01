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

/** A resolved, playable stream. Shared with the other sources, so it now lives in :core-stream. */
typealias Stream = com.onianime.stream.Stream
