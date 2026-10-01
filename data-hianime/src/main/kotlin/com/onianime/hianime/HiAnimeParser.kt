package com.onianime.hianime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A show in hianime.at search results, e.g. slug "frieren-beyond-journeys-end-481". */
data class HiAnimeShow(val slug: String, val title: String) {
    /** The numeric id the episode-list API wants (ani-cli: `${1##*-}`). */
    val id: String get() = slug.substringAfterLast('-')
}

/** One entry of a show's episode list. [number] is the label ("1", "2", ...), [id] the episode id. */
data class HiAnimeEpisode(val number: String, val id: String, val title: String? = null)

/** A video server for one episode, with its embed URL already base64-decoded from `data-hash`. */
data class HiAnimeServer(val type: String, val name: String, val embedUrl: String)

/**
 * Pure parsers for hianime.at's pages, ported from ani-cli 5.x (`hianime_search`,
 * `hianime_episodes`, `hianime_m3u8`). Attribute-based rather than position-based so a reordered
 * or extra attribute doesn't break them.
 */
object HiAnimeParser {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val FILM_NAME = Regex("""<h3[^>]*\bfilm-name\b[^>]*>\s*<a\s([^>]*)>""")
    private val EP_ITEM = Regex("""<a\s([^>]*\bep-item\b[^>]*)>""")
    private val SERVER_ITEM = Regex("""<div\s([^>]*\bserver-item\b[^>]*)>""")
    private val MAL_IN_EMBED = Regex("""/mal/(\d+)(?:/|$|\?)""")

    /** Search results page -> shows, in the site's relevance order. */
    fun search(html: String): List<HiAnimeShow> {
        // The "top 10" sidebar repeats the result markup; ani-cli cuts the page off before it.
        val main = html.substringBefore("id=\"main-sidebar\"")
        return FILM_NAME.findAll(main).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val href = attr(attrs, "href") ?: return@mapNotNull null
            val slug = href.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
            if (slug.isEmpty() || !slug.substringAfterLast('-').all(Char::isDigit)) return@mapNotNull null
            val title = attr(attrs, "title") ?: attr(attrs, "data-jname") ?: slug
            HiAnimeShow(slug, decodeEntities(title).trim())
        }.distinctBy { it.slug }.toList()
    }

    /** `/api/theme/episode/list/<id>` response -> episodes in site order. */
    fun episodes(body: String): List<HiAnimeEpisode> =
        EP_ITEM.findAll(apiHtml(body)).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val number = attr(attrs, "data-number")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val id = attr(attrs, "data-id")?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
                ?: return@mapNotNull null
            HiAnimeEpisode(number, id, attr(attrs, "title")?.let(::decodeEntities))
        }.distinctBy { it.id }.toList()

    /** `/api/theme/episode/servers?episodeId=<id>` response -> servers with decoded embed URLs. */
    fun servers(body: String): List<HiAnimeServer> =
        SERVER_ITEM.findAll(apiHtml(body)).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val type = attr(attrs, "data-type")?.trim()?.lowercase() ?: return@mapNotNull null
            val name = attr(attrs, "data-server-name")?.trim() ?: return@mapNotNull null
            val hash = attr(attrs, "data-hash")?.trim() ?: return@mapNotNull null
            val url = runCatching { String(EmbedDecoder.decodeBase64(hash), Charsets.UTF_8).trim() }.getOrNull()
                ?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            HiAnimeServer(type, name, url)
        }.toList()

    /** The MyAnimeList id a ZokoAnime embed URL carries (".../stream/mal/52991/1/sub"). */
    fun malIdFromEmbed(url: String): Int? = MAL_IN_EMBED.find(url)?.groupValues?.get(1)?.toIntOrNull()

    /** The `html` field of hianime's `{"status":true,"html":"..."}` API responses. */
    fun apiHtml(body: String): String {
        val html = runCatching { (json.parseToJsonElement(body) as? JsonObject)?.get("html") as? JsonPrimitive }
            .getOrNull()?.takeIf { it.isString }?.content
        return html ?: body.replace("\\\"", "\"").replace("\\/", "/").replace("\\n", "\n")
    }

    /** Value of attribute [name] inside a tag's attribute text (either quote style). */
    fun attr(attrs: String, name: String): String? {
        val m = Regex("""(?:^|\s)${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)')""").find(attrs) ?: return null
        return m.groups[1]?.value ?: m.groups[2]?.value
    }

    fun decodeEntities(s: String): String =
        s.replace(Regex("""&#(\d+);""")) { it.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: it.value }
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { it.groupValues[1].toIntOrNull(16)?.let { c -> String(Character.toChars(c)) } ?: it.value }
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&nbsp;", " ").replace("&amp;", "&")
}
