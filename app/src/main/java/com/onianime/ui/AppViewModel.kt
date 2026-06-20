package com.onianime.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onianime.allanime.Stream
import com.onianime.catalog.CatalogRepository
import com.onianime.catalog.HomeRow
import com.onianime.data.ShowProgress
import com.onianime.data.WatchStore
import com.onianime.metadata.AniListMedia
import com.onianime.metadata.SkipInterval
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class Route { Home, Search, Detail, Player, MyList }

/**
 * Single source of truth for the UI. Holds navigation + the data each screen needs, loading from
 * [CatalogRepository] (browse/stream) and persisting watch progress + My List via [WatchStore].
 */
class AppViewModel(
    app: Application,
    private val repo: CatalogRepository,
) : AndroidViewModel(app) {

    // Real Application constructor so androidx viewModel() can instantiate it.
    constructor(app: Application) : this(app, CatalogRepository())

    private val store = WatchStore(app)

    var route by mutableStateOf(Route.Home)
        private set

    // Home / persisted
    val homeRows = mutableStateListOf<HomeRow>()
    val continueWatching = mutableStateListOf<AniListMedia>()
    val myList = mutableStateListOf<AniListMedia>()
    var progressByShow by mutableStateOf<Map<Int, ShowProgress>>(emptyMap())
        private set
    var homeLoading by mutableStateOf(true)
        private set

    // Search
    var query by mutableStateOf("")
        private set
    val results = mutableStateListOf<AniListMedia>()
    private var searchJob: Job? = null

    // Detail
    var detailMedia by mutableStateOf<AniListMedia?>(null)
        private set
    var mode by mutableStateOf("sub")
        private set
    var detailAllAnimeId by mutableStateOf<String?>(null)
        private set
    val episodes = mutableStateListOf<String>()
    var detailStatus by mutableStateOf("")
        private set

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

    var toast by mutableStateOf<String?>(null)

    init {
        loadHome()
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
        val media = detailMedia ?: return
        detailAllAnimeId = null
        episodes.clear()
        detailStatus = "Finding source…"
        viewModelScope.launch {
            val id = runCatching { repo.resolveAllAnimeId(media, mode) }.getOrNull()
            if (id == null) {
                detailStatus = "No source found for this title"
                return@launch
            }
            detailAllAnimeId = id
            val eps = runCatching { repo.episodes(id, mode) }.getOrDefault(emptyList())
            episodes.clear(); episodes.addAll(eps)
            detailStatus = if (eps.isEmpty()) "No episodes available" else ""
        }
    }

    fun toggleMode() {
        mode = if (mode == "sub") "dub" else "sub"
        if (route == Route.Detail) loadEpisodes()
    }

    fun search(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) { results.clear(); return }
        searchJob = viewModelScope.launch {
            val found = runCatching { repo.search(newQuery) }.getOrDefault(emptyList())
            results.clear(); results.addAll(found)
        }
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
        val id = detailAllAnimeId ?: return
        if (index !in episodes.indices) return
        playerMedia = media
        playerIndex = index
        playerStream = null
        playerStatus = "Resolving stream…"
        route = Route.Player

        // Register the show in Continue Watching without clobbering saved episode positions.
        viewModelScope.launch { store.markStarted(media, index, episodes[index]) }

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
        viewModelScope.launch {
            val streams = runCatching { repo.streams(id, mode, episodes[index]) }
                .getOrDefault(emptyList()).filter { it.url.startsWith("http") }
            val distinct = streams.distinctBy { it.heightOrZero }
            playerStreams.clear(); playerStreams.addAll(distinct)
            val best = distinct.firstOrNull()
            if (best == null) playerStatus = "No playable stream for episode ${episodes[index]}"
            else { playerStream = best; playerStatus = "" }
        }
    }

    /** Current quality label, e.g. "1080p" or "auto". */
    fun currentQualityLabel(): String =
        playerStream?.let { if (it.heightOrZero > 0) "${it.heightOrZero}p" else "auto" } ?: "—"

    /** Switch to the next available quality, preserving position via saved progress. */
    fun cycleQuality() {
        if (playerStreams.size <= 1) { toast = "Only one quality available"; return }
        val cur = playerStream ?: return
        val i = playerStreams.indexOfFirst { it.url == cur.url }.coerceAtLeast(0)
        val next = playerStreams[(i + 1) % playerStreams.size]
        playerStream = next
        toast = "Quality: ${if (next.heightOrZero > 0) "${next.heightOrZero}p" else "auto"}"
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
        Route.Player -> { route = Route.Detail; true }
        Route.Detail, Route.Search, Route.MyList -> { route = Route.Home; true }
        Route.Home -> false
    }
}
