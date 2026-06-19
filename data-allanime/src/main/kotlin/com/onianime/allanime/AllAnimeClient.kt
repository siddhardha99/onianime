package com.onianime.allanime

import com.onianime.config.AllAnimeConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to the allanime GraphQL API. Direct port of ani-cli's search_anime / episodes_list /
 * get_episode_url curl calls. Parsing mirrors ani-cli's sed expressions (regex over the raw JSON)
 * rather than a real JSON parser, so it tolerates the same quirks ani-cli does.
 */
class AllAnimeClient(
    private val config: AllAnimeConfig,
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = "application/json".toMediaType()

    /** ani-cli search_anime. Returns shows matching [query] for the given [mode] (sub/dub). */
    fun search(query: String, mode: String): List<SearchResult> {
        val body = """{"variables":{"search":{"allowAdult":false,"allowUnknown":false,"query":"$query"},"limit":40,"page":1,"translationType":"$mode","countryOrigin":"ALL"},"query":"$SEARCH_GQL"}"""
        val resp = post("${config.api}/api", body)
        // Anchor name on the following "availableEpisodes" key so a greedy match can't swallow it.
        val re = Regex("_id\":\"([^\"]*)\",\"name\":\"(.*?)\",\"availableEpisodes\".*?\"$mode\":([1-9][^,]*)")
        return resp.split("Show").mapNotNull { seg ->
            re.find(seg)?.let { m ->
                SearchResult(
                    id = m.groupValues[1],
                    title = m.groupValues[2].replace("\\\"", "").trim(),
                    episodes = m.groupValues[3].trim(),
                )
            }
        }
    }

    /** ani-cli episodes_list. Returns the sorted episode-number strings for the show. */
    fun episodesList(showId: String, mode: String): List<String> {
        val body = """{"variables":{"showId":"$showId"},"query":"$EPISODES_GQL"}"""
        val resp = post("${config.api}/api", body)
        val arr = Regex("\"$mode\":\\[([0-9.\",]*)\\]").find(resp)?.groupValues?.get(1) ?: return emptyList()
        return arr.split(",")
            .map { it.replace("\"", "").trim() }
            .filter { it.isNotEmpty() }
            .sortedBy { it.toFloatOrNull() ?: Float.MAX_VALUE }
    }

    /**
     * ani-cli get_episode_url: tries the persisted-query GET first, falls back to a full POST if the
     * response is empty or missing the encrypted "tobeparsed" marker. Returns the raw response body
     * (still encrypted) for [ResponseParser] to decrypt.
     */
    fun episodeSources(showId: String, mode: String, ep: String): String {
        val vars = """{"showId":"$showId","translationType":"$mode","episodeString":"$ep"}"""
        val ext = """{"persistedQuery":{"version":1,"sha256Hash":"${config.queryHash}"}}"""

        val url = "${config.api}/api".toHttpUrl().newBuilder()
            .addQueryParameter("variables", vars)
            .addQueryParameter("extensions", ext)
            .build()
        val getResp = runCatching {
            http.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", config.agent)
                    .header("Referer", config.refr)
                    .header("Origin", config.refr)
                    .get().build()
            ).execute().use { it.body?.string().orEmpty() }
        }.getOrDefault("")

        if (getResp.isNotEmpty() && getResp.contains("tobeparsed")) return getResp

        val body = """{"variables":{"showId":"$showId","translationType":"$mode","episodeString":"$ep"},"query":"$EPISODE_GQL"}"""
        return post("${config.api}/api", body)
    }

    /** Arbitrary GET with the allanime user-agent + an optional referer (clock.json, m3u8, embeds). */
    fun getRaw(url: String, referer: String? = config.refr): String {
        val builder = Request.Builder().url(url).header("User-Agent", config.agent).get()
        if (referer != null) builder.header("Referer", referer)
        return http.newCall(builder.build()).execute().use { it.body?.string().orEmpty() }
    }

    private fun post(url: String, body: String): String =
        http.newCall(
            Request.Builder().url(url)
                .header("User-Agent", config.agent)
                .header("Referer", config.refr)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(json))
                .build()
        ).execute().use { it.body?.string().orEmpty() }

    companion object {
        // GraphQL documents lifted verbatim from ani-cli (single-line, no embedded double quotes).
        private const val SEARCH_GQL =
            "query( \$search: SearchInput \$limit: Int \$page: Int \$translationType: VaildTranslationTypeEnumType \$countryOrigin: VaildCountryOriginEnumType ) { shows( search: \$search limit: \$limit page: \$page translationType: \$translationType countryOrigin: \$countryOrigin ) { edges { _id name availableEpisodes __typename } }}"

        private const val EPISODES_GQL =
            "query (\$showId: String!) { show( _id: \$showId ) { _id availableEpisodesDetail }}"

        private const val EPISODE_GQL =
            "query (\$showId: String!, \$translationType: VaildTranslationTypeEnumType!, \$episodeString: String!) { episode( showId: \$showId translationType: \$translationType episodeString: \$episodeString ) { episodeString sourceUrls }}"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
