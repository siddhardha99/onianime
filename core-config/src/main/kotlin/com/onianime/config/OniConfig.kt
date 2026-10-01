package com.onianime.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The ZokoAnime player: the stream host ani-cli 5.x ends up playing (hianime.at's "ZokoAnime"
 * server). Its embed page is keyed by MyAnimeList id, so the app can go AniList -> MAL id -> stream
 * without matching titles at all.
 */
data class ZokoConfig(
    /** Embed page template. Placeholders: {mal} MAL id, {ep} episode, {mode} sub|dub, {anilist} AniList id. */
    val embed: String,
    /** JS variable on the embed page holding the obfuscated player config (ani-cli: `window.__P`). */
    val blobVar: String,
    /** Repeating XOR key the player config is obfuscated with (ani-cli `deobfuscate_blob`). */
    val xorKey: String,
    /** Referer the stream host wants. Blank = the embed page's origin, which is what ani-cli sends. */
    val referer: String,
)

/** hianime.at, the site ani-cli 5.x scrapes to find a show's ZokoAnime embed. */
data class HiAnimeConfig(
    val base: String,
    /** `{q}` = URL-encoded search text. */
    val searchPath: String,
    /** `{id}` = numeric show id (the number at the end of the show's slug). */
    val episodesPath: String,
    /** `{ep}` = episode id from the episode list. */
    val serversPath: String,
    /** `data-server-name` to use. Only ZokoAnime's embed format is understood. */
    val server: String,
)

/**
 * Everything volatile about where streams come from, overridable at runtime by `onianime.json`
 * fetched from the onianime-config repo, so a source rotating a domain/key/name is a config push
 * rather than an APK update.
 *
 * `providers` is the order sources are tried in; the first one that finds the show wins, and the
 * others are tried for an episode whose streams fail.
 */
data class OniConfig(
    /** User-Agent for the zoko/hianime requests and the player. */
    val agent: String,
    val providers: List<String>,
    val zoko: ZokoConfig,
    val hianime: HiAnimeConfig,
    /** Legacy AllAnime settings (only used if "allanime" is listed in [providers]). */
    val allanime: AllAnimeConfig,
) {
    companion object {
        const val ZOKO = "zoko"
        const val HIANIME = "hianime"
        const val ALLANIME = "allanime"
        val KNOWN_PROVIDERS = setOf(ZOKO, HIANIME, ALLANIME)

        /** Working values, mirrored from ani-cli 5.1.4 (hianime.at + ZokoAnime). */
        val BAKED_IN = OniConfig(
            agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            providers = listOf(ZOKO, HIANIME),
            zoko = ZokoConfig(
                embed = "https://zokoanime.video/stream/mal/{mal}/{ep}/{mode}",
                blobVar = "__P",
                xorKey = "otaku-embed-v1",
                referer = "",
            ),
            hianime = HiAnimeConfig(
                base = "https://hianime.at",
                searchPath = "/search?keyword={q}",
                episodesPath = "/api/theme/episode/list/{id}",
                serversPath = "/api/theme/episode/servers?episodeId={ep}",
                server = "ZokoAnime",
            ),
            allanime = AllAnimeConfig.BAKED_IN,
        )

        /** Where installed apps read the remote config from. */
        const val REMOTE_URL =
            "https://raw.githubusercontent.com/siddhardha99/onianime-config/main/onianime.json"

        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        /**
         * Parses the remote JSON. Any field that is missing, blank or the wrong type keeps its
         * [fallback] value, and unparseable input returns [fallback] unchanged, so a typo in the
         * config can never take the app down.
         */
        fun fromJson(text: String, fallback: OniConfig = BAKED_IN): OniConfig {
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: return fallback

            val providers = (root["providers"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.lowercase() }
                ?.filter { it in KNOWN_PROVIDERS }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }

            val zoko = (root["zoko"] as? JsonObject)?.let { z ->
                ZokoConfig(
                    embed = z.str("embed") ?: fallback.zoko.embed,
                    blobVar = z.str("blobVar") ?: fallback.zoko.blobVar,
                    xorKey = z.str("xorKey") ?: fallback.zoko.xorKey,
                    referer = z.str("referer") ?: fallback.zoko.referer,
                )
            } ?: fallback.zoko

            val hianime = (root["hianime"] as? JsonObject)?.let { h ->
                HiAnimeConfig(
                    base = h.str("base")?.trimEnd('/') ?: fallback.hianime.base,
                    searchPath = h.str("search") ?: fallback.hianime.searchPath,
                    episodesPath = h.str("episodes") ?: fallback.hianime.episodesPath,
                    serversPath = h.str("servers") ?: fallback.hianime.serversPath,
                    server = h.str("server") ?: fallback.hianime.server,
                )
            } ?: fallback.hianime

            val allanime = (root["allanime"] as? JsonObject)
                ?.let { AllAnimeConfig.fromJson(it.toString(), fallback.allanime) }
                ?: fallback.allanime

            return OniConfig(
                agent = root.str("agent") ?: fallback.agent,
                providers = providers ?: fallback.providers,
                zoko = zoko,
                hianime = hianime,
                allanime = allanime,
            )
        }

        private fun JsonObject.str(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    }
}
