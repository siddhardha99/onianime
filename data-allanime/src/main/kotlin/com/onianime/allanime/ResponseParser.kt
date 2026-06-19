package com.onianime.allanime

import com.onianime.config.AllAnimeConfig
import com.onianime.crypto.AllAnimeCrypto

/**
 * ani-cli's process_response + the sourceUrl/sourceName extraction in get_episode_url.
 */
object ResponseParser {

    private val TOBEPARSED = Regex("\"tobeparsed\":\"([^\"]*)\"")
    private val SOURCE = Regex("sourceUrl\":\"([^\"]*)\".*sourceName\":\"([^\"]*)\"")

    /** Decrypts the "tobeparsed" blob if present; otherwise returns [raw] unchanged. */
    fun process(raw: String, config: AllAnimeConfig): String {
        val blob = TOBEPARSED.find(raw)?.groupValues?.get(1) ?: return raw
        return runCatching {
            AllAnimeCrypto.decryptToBeParsed(blob, config.keySeed)
        }.getOrDefault(raw)
    }

    /**
     * Parses the (decrypted) response into (sourceName, sourceUrl) pairs. Mirrors ani-cli:
     * un-escape / and stray backslashes, split on braces, then match sourceUrl..sourceName.
     */
    fun parseSources(processed: String): List<Source> {
        val cleaned = processed.replace("\\u002F", "/").replace("\\", "")
        return cleaned.replace('{', '\n').replace('}', '\n').split('\n')
            .mapNotNull { segment ->
                SOURCE.find(segment)?.let { m ->
                    Source(name = m.groupValues[2], url = m.groupValues[1])
                }
            }
    }
}
