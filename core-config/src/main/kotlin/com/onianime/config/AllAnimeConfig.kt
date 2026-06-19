package com.onianime.config

/**
 * All the volatile constants ani-cli has historically had to patch ("fix: update allanime key",
 * "update allanime key and ct_len", etc.). Centralising them here means a breakage is a one-line
 * change — and the [BAKED_IN] values can be overridden at runtime by [fromJson] fetched from the
 * onianime-config GitHub repo, so installed apps self-heal without an APK update.
 *
 * Remote JSON schema (hosted at e.g.
 * https://raw.githubusercontent.com/<you>/onianime-config/main/allanime.json):
 *
 *   {
 *     "schemaVersion": 1,
 *     "agent": "...",
 *     "allanime": {
 *       "base": "allanime.day",
 *       "api": "https://api.allanime.day",
 *       "refr": "https://youtu-chan.com",
 *       "keySeed": "Xot36i3lK3:v1",
 *       "queryHash": "d405...fec",
 *       "providerPatterns": { "wixmp": "Default", "youtube": "Yt-mp4",
 *                              "sharepoint": "S-mp4", "mp4upload": "Mp4" }
 *     }
 *   }
 */
data class AllAnimeConfig(
    val agent: String,
    val base: String,
    val api: String,
    val refr: String,
    val keySeed: String,
    val queryHash: String,
    /** ordered map: provider key -> the sourceName allanime uses for it */
    val providerPatterns: Map<String, String>,
) {
    companion object {
        /** Current working values, mirrored from ani-cli 4.14.1. */
        val BAKED_IN = AllAnimeConfig(
            agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0",
            base = "allanime.day",
            api = "https://api.allanime.day",
            refr = "https://youtu-chan.com",
            keySeed = "Xot36i3lK3:v1",
            queryHash = "d405d0edd690624b66baba3068e0edc3ac90f1597d898a1ec8db4e5c43c00fec",
            providerPatterns = linkedMapOf(
                "wixmp" to "Default",
                "youtube" to "Yt-mp4",
                "sharepoint" to "S-mp4",
                "mp4upload" to "Mp4",
            ),
        )

        /** Default location of the remote escape-hatch config. */
        const val REMOTE_URL =
            "https://raw.githubusercontent.com/siddhardha99/onianime-config/main/allanime.json"

        /**
         * Parses the remote JSON, falling back to [BAKED_IN] for any field that is missing or
         * unparseable. Deliberately regex-based (like ani-cli) so this module stays dependency-free
         * and never hard-fails on an unexpected response.
         */
        fun fromJson(json: String, fallback: AllAnimeConfig = BAKED_IN): AllAnimeConfig {
            fun str(key: String): String? =
                Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)

            val patterns = Regex("\"providerPatterns\"\\s*:\\s*\\{([^}]*)\\}")
                .find(json)?.groupValues?.get(1)
                ?.let { body ->
                    Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"").findAll(body)
                        .associate { it.groupValues[1] to it.groupValues[2] }
                        .takeIf { it.isNotEmpty() }
                }

            return fallback.copy(
                agent = str("agent") ?: fallback.agent,
                base = str("base") ?: fallback.base,
                api = str("api") ?: fallback.api,
                refr = str("refr") ?: fallback.refr,
                keySeed = str("keySeed") ?: fallback.keySeed,
                queryHash = str("queryHash") ?: fallback.queryHash,
                providerPatterns = patterns ?: fallback.providerPatterns,
            )
        }
    }
}
