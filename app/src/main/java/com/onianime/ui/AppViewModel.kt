package com.onianime.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onianime.allanime.Stream
import com.onianime.catalog.CatalogRepository
import com.onianime.catalog.HomeRow
import com.onianime.metadata.AniListMedia
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class Route { Home, Search, Detail, Player, MyList }

/**
 * Single source of truth for the UI. Holds navigation + the data each screen needs, loading from
 * [CatalogRepository] (AniList browse/search + allanime stream resolution).
 */
class AppViewModel(
    private val repo: CatalogRepository,
) : ViewModel() {

    // Real no-arg constructor so androidx viewModel() can instantiate it.
    constructor() : this(CatalogRepository())

    var route by mutableStateOf(Route.Home)
        private set

    // Home
    val homeRows = mutableStateListOf<HomeRow>()
    val continueWatching = mutableStateListOf<AniListMedia>()
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
    var playerStream by mutableStateOf<Stream?>(null)
        private set
    var playerIndex by mutableStateOf(0)
        private set
    var playerStatus by mutableStateOf("")
        private set

    var toast by mutableStateOf<String?>(null)

    init {
        loadHome()
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

    /** Resolve and start playback of [index] in the current detail's episode list. */
    fun playEpisode(index: Int) {
        val media = detailMedia ?: return
        val id = detailAllAnimeId ?: return
        if (index !in episodes.indices) return
        playerIndex = index
        playerStream = null
        playerStatus = "Resolving stream…"
        route = Route.Player
        if (continueWatching.none { it.id == media.id }) continueWatching.add(0, media)
        viewModelScope.launch {
            val streams = runCatching { repo.streams(id, mode, episodes[index]) }.getOrDefault(emptyList())
            val best = streams.firstOrNull { it.url.startsWith("http") }
            if (best == null) playerStatus = "No playable stream for episode ${episodes[index]}"
            else { playerStream = best; playerStatus = "" }
        }
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
