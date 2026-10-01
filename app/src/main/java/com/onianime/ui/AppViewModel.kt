package com.onianime.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onianime.catalog.CatalogRepository
import com.onianime.catalog.HomeRow
import com.onianime.catalog.RemoteConfigClient
import com.onianime.catalog.SourceShow
import com.onianime.config.OniConfig
import com.onianime.data.Settings
import com.onianime.data.ShowProgress
import com.onianime.data.WatchStore
import com.onianime.metadata.AniListMedia
import com.onianime.metadata.SkipInterval
import com.onianime.stream.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Route { Home, Search, Detail, Player, MyList, Settings }

/**
 * Single source of truth for the UI. Holds navigation + the data each screen needs, loading from
 * [CatalogRepository] (browse/stream) and persisting watch progress + My List via [WatchStore].
 */
class AppViewModel(
    app: Application,
    private var repo: CatalogRepository,
) : AndroidViewModel(app) {

    // Real Application constructor so androidx viewModel() can instantiate it.
    constructor(app: Application) : this(app, CatalogRepository())

    private val store = WatchStore(app)
    private val configClient = RemoteConfigClient()
    private var currentConfig = OniConfig.BAKED_IN

    /** User-Agent the player sends; follows the remote config (stream hosts can check it). */
    var userAgent by mutableStateOf(OniConfig.BAKED_IN.agent)
        private set

    var route by mutableStateOf(Route.Home)
        private set

    // Home / persisted
    val homeRows = mutableStateListOf<HomeRow>()
    val continueWatching = mutableStateListOf<AniListMedia>()
    val myList = mutableStateListOf<AniListMedia>()
    var progressByShow by mutableStateOf<Map<Int, ShowProgress>>(emptyMap())
        private set
    var settings by mutableStateOf(Settings())
        private set
    private var settingsLoaded = false
    var homeLoading by mutableStateOf(true)
        private set

    // Search
    var query by mutableStateOf("")
        private set
    val results = mutableStateListOf<AniListMedia>()
    val recentSearches = mutableStateListOf<String>()
    var searchLoading by mutableStateOf(false)
        private set
    var activeGenre by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    // Detail
    var detailMedia by mutableStateOf<AniListMedia?>(null)
        private set
    var mode by mutableStateOf("sub")
        private set
    /** Where this show's streams come from (which source + its id/episodes there). */
    var detailSource by mutableStateOf<SourceShow?>(null)
        private set
    val episodes = mutableStateListOf<String>()
    var detailStatus by mutableStateOf("")
        private set
    private var loadJob: Job? = null

    // Player
    private var playerMedia: AniListMedia? = null
    var playerStream by mutableStateOf<Stream?>(null)
        private set
    var playerIndex by mutableStateOf(0)
        private set
    var playerStatus by mutableStateOf("")
        private set
    val skipIntervals = mutableStateListOf<SkipInterval>()
    val playerStreams = mutableStateListOf<Stream>() // distinct qualities for the current episode
    private var playJob: Job? = null

    /** The viewer's CC choice this session; null = follow the audio (on for SUB, off for DUB). */
    private var subtitlesChoice by mutableStateOf<Boolean?>(null)
    val subtitlesOn: Boolean get() = subtitlesChoice ?: (mode == "sub")

    var toast by mutableStateOf<String?>(null)

    init {
        loadHome()
        viewModelScope.launch { refreshConfig() } // pull the latest source config from GitHub
        viewModelScope.launch {
            store.progress.collect { list ->
                progressByShow = list.associateBy { it.media.id }
                continueWatching.clear()
                continueWatching.addAll(list.map { it.media })
            }
        }
        viewModelScope.launch {
            store.myList.collect { list -> myList.clear(); myList.addAll(list) }
        }
        viewModelScope.launch {
            store.settings.collect { s ->
                settings = s
                if (!settingsLoaded) { mode = s.defaultMode; settingsLoaded = true }
            }
        }
        viewModelScope.launch {
            store.recentSearches.collect { list -> recentSearches.clear(); recentSearches.addAll(list) }
        }
    }

    fun goSettings() { route = Route.Settings }

    fun updateSettings(s: Settings) {
        settings = s
        viewModelScope.launch { store.saveSettings(s) }
    }

    /** Self-heal: fetch the latest onianime.json from the onianime-config repo; rebuild the repo if it changed. */
    private suspend fun refreshConfig(): Boolean {
        // Fall back to what we already have, so a failed fetch never discards a fix pulled earlier.
        val fresh = withContext(Dispatchers.IO) { configClient.fetch(fallback = currentConfig) }
        if (fresh != currentConfig) {
            currentConfig = fresh
            userAgent = fresh.agent
            repo = CatalogRepository(fresh)
            return true
        }
        return false
    }

    fun loadHome() {
        homeLoading = true
        viewModelScope.launch {
            runCatching { repo.defaultHomeRows() }
                .onSuccess { rows -> homeRows.clear(); homeRows.addAll(rows) }
                .onFailure { toast = "Couldn't load home: ${it.message}" }
            homeLoading = false
        }
    }

    fun goHome() { route = Route.Home }
    fun goSearch() { route = Route.Search }
    fun goMyList() { route = Route.MyList }

    fun openDetail(media: AniListMedia) {
        detailMedia = media
        route = Route.Detail
        loadEpisodes()
    }

    private fun loadEpisodes() {
        val snapshot = detailMedia ?: return
        val requestMode = mode
        detailSource = null
        episodes.clear()
        detailStatus = "Finding source…"
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Only write results while this is still the page on screen (a newer load may have started).
            fun stillCurrent() = isActive && detailMedia?.id == snapshot.id && mode == requestMode

            // Shows opened from Continue Watching / My List are saved snapshots: refresh them so an
            // airing show's episode count is current. Falls back to the snapshot when offline.
            val media = attempt { repo.media(snapshot.id) } ?: snapshot
            if (!stillCurrent()) return@launch
            detailMedia = media

            var found = attempt { repo.findSource(media, requestMode) }
            // self-heal: a source may have moved — refresh config and retry once
            if (found?.show == null && refreshConfig()) {
                found = attempt { repo.findSource(media, requestMode) }
            }
            if (!stillCurrent()) return@launch
            val show = found?.show
            if (show == null) {
                val why = found?.errors?.lastOrNull()?.let { " ($it)" }.orEmpty()
                detailStatus = if (media.airedEpisodes == 0) "No episodes have aired yet" else "No source found for this title$why"
                return@launch
            }
            detailSource = show
            episodes.clear(); episodes.addAll(show.episodes)
            detailStatus = ""
        }
    }

    /** runCatching that lets coroutine cancellation through, so a cancelled load stops instead of writing stale results. */
    private inline fun <T> attempt(block: () -> T): T? =
        try {
            block()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            null
        }

    fun toggleMode() {
        mode = if (mode == "sub") "dub" else "sub"
        if (route == Route.Detail) loadEpisodes()
    }

    fun search(newQuery: String) {
        query = newQuery
        activeGenre = null
        searchJob?.cancel()
        if (newQuery.isBlank()) { results.clear(); searchLoading = false; return }
        searchLoading = true
        searchJob = viewModelScope.launch {
            delay(300) // debounce rapid typing
            val found = runCatching { repo.search(newQuery) }.getOrDefault(emptyList())
            results.clear(); results.addAll(found)
            searchLoading = false
        }
    }

    /** Browse by genre (toggles off if the same genre is tapped again). */
    fun selectGenre(genre: String) {
        searchJob?.cancel()
        if (activeGenre == genre) { activeGenre = null; results.clear(); searchLoading = false; return }
        activeGenre = genre
        query = ""
        searchLoading = true
        searchJob = viewModelScope.launch {
            val found = runCatching { repo.homeRow(sort = "POPULARITY_DESC", genre = genre, perPage = 40) }.getOrDefault(emptyList())
            results.clear(); results.addAll(found)
            searchLoading = false
        }
    }

    /** Remember the current query (called when the user opens a search result). */
    fun recordSearch() {
        val q = query
        if (q.isNotBlank()) viewModelScope.launch { store.addRecentSearch(q) }
    }

    // ---- My List ----

    fun isInMyList(id: Int): Boolean = myList.any { it.id == id }

    fun toggleMyList() {
        val m = detailMedia ?: return
        viewModelScope.launch {
            val added = store.toggleMyList(m)
            toast = if (added) "Added to My List" else "Removed from My List"
        }
    }

    // ---- Progress / resume ----

    fun progressFor(showId: Int): ShowProgress? = progressByShow[showId]

    fun episodeFraction(showId: Int, episodeIndex: Int): Float =
        progressByShow[showId]?.episodes?.get(episodeIndex)?.fraction ?: 0f

    /** Long-press toggle: mark an episode watched, or clear it back to unwatched. */
    fun toggleEpisodeWatched(index: Int) {
        val media = detailMedia ?: return
        val label = episodes.getOrNull(index) ?: return
        val isWatched = progressByShow[media.id]?.episodes?.get(index)?.finished == true
        viewModelScope.launch {
            store.setEpisodeWatched(media, index, label, !isWatched)
            toast = if (!isWatched) "Episode $label marked watched" else "Episode $label marked unwatched"
        }
    }

    /**
     * Episode index to resume [showId] at. Matched by the saved episode label first, because a
     * different source can number its list differently (e.g. AllAnime's "0"/"12.5" entries).
     */
    fun resumeIndex(showId: Int): Int {
        val p = progressByShow[showId] ?: return 0
        val byLabel = episodes.indexOf(p.lastEpisodeLabel)
        return if (byLabel >= 0) byLabel else p.lastEpisodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
    }

    /** Saved resume position (ms) for the currently-playing episode; 0 if none or finished. */
    fun resumePositionMs(): Long {
        val media = playerMedia ?: return 0
        val ep = progressByShow[media.id]?.episodes?.get(playerIndex) ?: return 0
        return if (ep.finished) 0 else ep.positionMs
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val media = playerMedia ?: return
        val label = episodes.getOrNull(playerIndex) ?: return
        viewModelScope.launch { store.saveProgress(media, playerIndex, label, positionMs, durationMs) }
    }

    // ---- Playback ----

    /** Resolve and start playback of [index] in the current detail's episode list. */
    fun playEpisode(index: Int) {
        val media = detailMedia ?: return
        val show = detailSource ?: return
        if (index !in episodes.indices) return
        val label = episodes[index]
        val requestMode = mode
        playerMedia = media
        playerIndex = index
        playerStream = null
        playerStatus = "Resolving stream…"
        route = Route.Player

        // Register the show in Continue Watching without clobbering saved episode positions.
        viewModelScope.launch { store.markStarted(media, index, label) }

        // AniSkip op/ed times (needs MAL id + an integer episode number)
        skipIntervals.clear()
        val malId = media.idMal
        val epNum = episodes[index].toFloatOrNull()?.toInt()
        if (malId != null && epNum != null) {
            viewModelScope.launch {
                val times = runCatching { repo.skipTimes(malId, epNum) }.getOrDefault(emptyList())
                if (playerIndex == index) { skipIntervals.clear(); skipIntervals.addAll(times) }
            }
        }
        playerStreams.clear()
        playJob?.cancel()
        playJob = viewModelScope.launch {
            suspend fun resolve() = attempt { repo.streams(media, detailSource ?: show, requestMode, label) }
            var result = resolve()
            if (result?.streams.isNullOrEmpty() && refreshConfig()) result = resolve() // self-heal retry
            // The viewer may have moved on (another episode/show) while this was resolving.
            if (!isActive || playerMedia?.id != media.id || playerIndex != index) return@launch
            // Another source rescued this episode: start there next time too.
            if (result != null && result.streams.isNotEmpty() && detailMedia?.id == media.id &&
                result.show.provider != detailSource?.provider
            ) {
                detailSource = result.show
            }
            val distinct = result?.streams.orEmpty().distinctBy { it.heightOrZero }
            playerStreams.clear(); playerStreams.addAll(distinct)
            val best = pickPreferredStream(distinct)
            if (best == null) {
                val why = result?.errors?.lastOrNull()?.let { " ($it)" }.orEmpty()
                playerStatus = "No playable ${requestMode.uppercase()} stream for episode $label$why"
            } else {
                playerStream = best; playerStatus = ""
            }
        }
    }

    /** CC button: show/hide the side-loaded subtitle track. */
    fun toggleSubtitles() {
        if (playerStream?.subtitles.isNullOrEmpty()) {
            toast = "No separate subtitles for this stream"
            return
        }
        subtitlesChoice = !subtitlesOn
        toast = if (subtitlesOn) "Subtitles on" else "Subtitles off"
    }

    /**
     * The player couldn't play [failed]. A subtitle file that won't load fails the whole source, so
     * first retry the same quality without subtitles; then fall back to the next quality; else explain.
     */
    fun onPlaybackError(failed: Stream, reason: String) {
        val current = playerStream ?: return
        if (current != failed) return // an error from a player that was already being replaced
        if (current.subtitles.isNotEmpty()) {
            toast = "Retrying without subtitles"
            playerStream = current.copy(subtitles = emptyList())
            return
        }
        val next = playerStreams.dropWhile { it.url != current.url }.drop(1).firstOrNull()
        if (next != null) {
            toast = "Couldn't play ${qualityLabel(current)}, trying ${qualityLabel(next)}"
            playerStream = next
        } else {
            playerStream = null
            playerStatus = "Couldn't play episode ${episodes.getOrNull(playerIndex) ?: ""} ($reason)"
        }
    }

    /** Choose a stream matching the user's preferred quality, else the best available. */
    private fun pickPreferredStream(streams: List<Stream>): Stream? {
        if (streams.isEmpty()) return null
        return when (val q = settings.preferredQuality) {
            "best" -> streams.firstOrNull { it.quality == "auto" } ?: streams.first() // Auto = adaptive
            "worst" -> streams.lastOrNull { it.heightOrZero > 0 } ?: streams.last()
            else -> streams.firstOrNull { it.heightOrZero == q.toIntOrNull() }
                ?: streams.firstOrNull { it.quality == "auto" } ?: streams.first()
        }
    }

    private fun qualityLabel(s: Stream): String =
        when { s.quality == "auto" -> "Auto"; s.heightOrZero > 0 -> "${s.heightOrZero}p"; else -> "auto" }

    /** Current quality label, e.g. "Auto", "1080p". */
    fun currentQualityLabel(): String = playerStream?.let { qualityLabel(it) } ?: "—"

    /** Switch to the next available quality, preserving position via saved progress. */
    fun cycleQuality() {
        if (playerStreams.size <= 1) { toast = "Only one quality available"; return }
        val cur = playerStream ?: return
        val i = playerStreams.indexOfFirst { it.url == cur.url }.coerceAtLeast(0)
        val next = playerStreams[(i + 1) % playerStreams.size]
        playerStream = next
        toast = "Quality: ${qualityLabel(next)}"
    }

    /** Toggle SUB/DUB and re-resolve the current episode in the new mode. */
    fun switchAudio() {
        mode = if (mode == "sub") "dub" else "sub"
        toast = "Audio: ${mode.uppercase()}"
        playEpisode(playerIndex)
    }

    fun nextEpisode() { if (playerIndex + 1 in episodes.indices) playEpisode(playerIndex + 1) }
    fun prevEpisode() { if (playerIndex - 1 in episodes.indices) playEpisode(playerIndex - 1) }

    /** Hardware/remote Back. Returns false when already at Home (let the system handle exit). */
    fun back(): Boolean = when (route) {
        Route.Player -> { playJob?.cancel(); route = Route.Detail; true }
        Route.Detail, Route.Search, Route.MyList, Route.Settings -> { route = Route.Home; true }
        Route.Home -> false
    }
}
