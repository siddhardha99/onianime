package com.onianime.metadata

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AniList GraphQL client (https://anilist.co/graphiql). Public, no auth. Provides the rich metadata
 * the allanime scrape can't: real titles, posters/banners, synopsis, scores, MAL id (for AniSkip).
 */
class AniListClient(
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    /** Free-text search, ordered by relevance. */
    fun search(query: String, perPage: Int = 25): List<AniListMedia> =
        page(search = query, sort = "SEARCH_MATCH", perPage = perPage)

    /**
     * A page of shows for a home row. [sort] is an AniList MediaSort enum value
     * (TRENDING_DESC, POPULARITY_DESC, SCORE_DESC, UPDATED_AT_DESC...). [genre] filters by genre.
     */
    fun page(
        search: String? = null,
        sort: String = "TRENDING_DESC",
        genre: String? = null,
        perPage: Int = 20,
    ): List<AniListMedia> {
        val variables = buildJsonObject {
            search?.let { put("search", it) }
            putJsonArray("sort") { add(sort) }
            genre?.let { put("genre", it) }
            put("perPage", perPage)
        }
        val body = buildJsonObject {
            put("query", QUERY)
            put("variables", variables)
        }.toString()

        val resp = http.newCall(
            Request.Builder().url(ENDPOINT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(body.toRequestBody(mediaType))
                .build()
        ).execute().use { it.body?.string().orEmpty() }

        return parse(resp)
    }

    private fun parse(resp: String): List<AniListMedia> {
        if (resp.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(resp).jsonObject }.getOrNull() ?: return emptyList()
        val media = root["data"]?.jsonObject
            ?.get("Page")?.jsonObject
            ?.get("media") as? JsonArray ?: return emptyList()
        return media.mapNotNull { runCatching { it.jsonObject.toMedia() }.getOrNull() }
    }

    private fun JsonObject.toMedia(): AniListMedia {
        val title = this["title"]?.jsonObject
        return AniListMedia(
            id = this["id"]!!.jsonPrimitive.int,
            idMal = this["idMal"]?.jsonPrimitive?.intOrNull(),
            romaji = title?.str("romaji"),
            english = title?.str("english"),
            native = title?.str("native"),
            synonyms = (this["synonyms"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull() } ?: emptyList(),
            episodes = this["episodes"]?.jsonPrimitive?.intOrNull(),
            format = this.str("format"),
            status = this.str("status"),
            seasonYear = this["seasonYear"]?.jsonPrimitive?.intOrNull(),
            averageScore = this["averageScore"]?.jsonPrimitive?.intOrNull(),
            genres = (this["genres"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull() } ?: emptyList(),
            duration = this["duration"]?.jsonPrimitive?.intOrNull(),
            description = this.str("description"),
            coverImage = this["coverImage"]?.jsonObject?.let { it.str("extraLarge") ?: it.str("large") },
            bannerImage = this.str("bannerImage"),
            coverColor = this["coverImage"]?.jsonObject?.str("color"),
        )
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull()

    private fun kotlinx.serialization.json.JsonPrimitive.intOrNull(): Int? =
        runCatching { this.int }.getOrNull()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (this.toString() == "null") null else this.content

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co"

        private val QUERY = """
            query (${'$'}search: String, ${'$'}sort: [MediaSort], ${'$'}genre: String, ${'$'}perPage: Int) {
              Page(perPage: ${'$'}perPage) {
                media(search: ${'$'}search, sort: ${'$'}sort, genre: ${'$'}genre, type: ANIME, isAdult: false) {
                  id idMal
                  title { romaji english native }
                  synonyms episodes format status seasonYear averageScore genres duration
                  description(asHtml: false)
                  coverImage { extraLarge large color }
                  bannerImage
                }
              }
            }
        """.trimIndent()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
