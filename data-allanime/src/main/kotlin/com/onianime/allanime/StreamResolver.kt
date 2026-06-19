package com.onianime.allanime

import com.onianime.config.AllAnimeConfig
import com.onianime.crypto.ProviderDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * End-to-end resolver: episode id -> sorted list of playable [Stream]s.
 *
 * Mirrors ani-cli's get_episode_url: fetch sources, decrypt, then resolve every provider
 * concurrently (ani-cli backgrounds each `generate_link` and `wait`s) and merge the results.
 */
class StreamResolver(
    private val client: AllAnimeClient,
    private val config: AllAnimeConfig,
) {
    suspend fun resolve(showId: String, mode: String, episode: String): List<Stream> = coroutineScope {
        val raw = withContext(Dispatchers.IO) { client.episodeSources(showId, mode, episode) }
        val sources = ResponseParser.parseSources(ResponseParser.process(raw, config))

        config.providerPatterns.map { (providerKey, sourceName) ->
            async(Dispatchers.IO) {
                val source = sources.firstOrNull { it.name == sourceName }
                    ?: return@async emptyList<Stream>()
                val providerId = ProviderDecoder.decode(source.url)
                runCatching { LinkResolver.getLinks(providerId, providerKey, config, client) }
                    .getOrElse { emptyList() }
            }
        }.awaitAll().flatten().sortedByDescending { it.heightOrZero }
    }
}
