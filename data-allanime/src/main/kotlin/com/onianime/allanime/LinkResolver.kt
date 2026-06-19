package com.onianime.allanime

import com.onianime.config.AllAnimeConfig

/**
 * Port of ani-cli's get_links: turns one provider's (decoded) source URL into concrete playable
 * [Stream]s. Handles the direct providers (mp4upload, fast4speed) and the allanime clock.json
 * provider, then the .urlset / master.m3u8 post-processing.
 */
object LinkResolver {

    private val MP4UPLOAD_SRC = Regex("src: \"([^\"]*)\"")
    private val CLOCK_RES = Regex("link\":\"([^\"]*)\".*\"resolutionStr\":\"([^\"]*)\"")
    private val CLOCK_HLS = Regex("hls\",\"url\":\"([^\"]*)\".*\"hardsub_lang\":\"en-US\"")
    private val M3U8_REFERER = Regex("Referer\":\"([^\"]*)\"")
    private val MASTER_RESOLUTION = Regex("RESOLUTION=\\d+x(\\d+)")

    fun getLinks(
        providerId: String,
        providerKey: String,
        config: AllAnimeConfig,
        client: AllAnimeClient,
    ): List<Stream> = when {
        providerId.contains("mp4upload") -> {
            val body = client.getRaw(providerId, config.refr)
            MP4UPLOAD_SRC.find(body)?.groupValues?.get(1)?.let { link ->
                listOf(Stream("0", link, "mp4upload", "https://www.mp4upload.com", link.contains("m3u8")))
            } ?: emptyList()
        }

        providerId.contains("tools.fast4speed.rsvp") ->
            listOf(Stream("1080", providerId, "youtube", config.refr, providerId.contains("m3u8")))

        else -> {
            val response = client.getRaw("https://${config.base}$providerId", config.refr)
            val rawLinks = parseClockJson(response)
            when {
                rawLinks.isEmpty() -> emptyList()
                rawLinks.any { it.contains("master.m3u8") } ->
                    resolveMaster(rawLinks, response, providerKey, config, client)
                rawLinks.any { it.contains("repackager.wixmp.com") } ->
                    expandUrlset(rawLinks, config)
                else -> rawLinks.map { toStream(it, providerKey, config) }
            }
        }
    }

    /** Each clock.json entry -> "resolution >url" (mp4 list) or a bare hls url (en-US hardsub). */
    private fun parseClockJson(response: String): List<String> {
        val out = mutableListOf<String>()
        for (obj in response.replace("},{", "}\n{").split("\n")) {
            CLOCK_RES.find(obj)?.let { out.add("${it.groupValues[2]} >${it.groupValues[1]}") }
            CLOCK_HLS.find(obj)?.let { out.add(it.groupValues[1]) }
        }
        return out
    }

    /** Fetch the m3u8 master playlist and expand it into one [Stream] per variant resolution. */
    private fun resolveMaster(
        rawLinks: List<String>,
        response: String,
        providerKey: String,
        config: AllAnimeConfig,
        client: AllAnimeClient,
    ): List<Stream> {
        val refr = M3U8_REFERER.find(response)?.groupValues?.get(1) ?: config.refr
        val masterUrl = rawLinks.first().substringAfter(">").trim()
        val relative = masterUrl.substringBeforeLast("/", "") + "/"
        val playlist = client.getRaw(masterUrl, refr)
        if (!playlist.contains("EXTM3U")) {
            return listOf(Stream("0", masterUrl, providerKey, refr, isHls = true))
        }

        val streams = mutableListOf<Stream>()
        var pendingHeight: String? = null
        for (rawLine in playlist.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    pendingHeight = if (line.contains("I-FRAME")) null
                    else MASTER_RESOLUTION.find(line)?.groupValues?.get(1) ?: "0"
                }
                pendingHeight != null && line.isNotEmpty() && !line.startsWith("#") -> {
                    val url = if (line.startsWith("http")) line else relative + line
                    streams.add(Stream(pendingHeight!!, url, providerKey, refr, isHls = true))
                    pendingHeight = null
                }
            }
        }
        // Fall back to the master itself if no variants were parsed (ExoPlayer can adapt from it).
        return streams.ifEmpty { listOf(Stream("0", masterUrl, providerKey, refr, isHls = true)) }
            .sortedByDescending { it.heightOrZero }
    }

    /** Legacy wixmp packed multi-bitrate url (".urlset/.../,480,720,1080,/mp4/file.urlset/..."). */
    private fun expandUrlset(rawLinks: List<String>, config: AllAnimeConfig): List<Stream> {
        val link = rawLinks.first().substringAfter(">").trim()
        val base = link.replace("repackager.wixmp.com/", "").replace(Regex("\\.urlset.*"), "")
        val heights = Regex(".*/,([^/]*),/mp4").find(link)?.groupValues?.get(1)
            ?.split(",")?.filter { it.isNotEmpty() } ?: return emptyList()
        return heights.map { h ->
            val url = base.replace(Regex(",[^/]*"), h)
            Stream(h.filter { it.isDigit() }.ifEmpty { "0" }, url, "wixmp", config.refr, url.contains("m3u8"))
        }.sortedByDescending { it.heightOrZero }
    }

    private fun toStream(line: String, providerKey: String, config: AllAnimeConfig): Stream {
        val hasArrow = line.contains(">")
        val quality = if (hasArrow) line.substringBefore(">").trim() else "0"
        val url = if (hasArrow) line.substringAfter(">").trim() else line.trim()
        val referer = if (providerKey == "sharepoint") null else config.refr
        return Stream(quality, url, providerKey, referer, url.contains("m3u8"))
    }
}
