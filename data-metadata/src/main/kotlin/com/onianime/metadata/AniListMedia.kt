package com.onianime.metadata

/** A show as described by AniList — the source of real titles, posters, synopsis and MAL id. */
data class AniListMedia(
    val id: Int,
    val idMal: Int?,
    val romaji: String?,
    val english: String?,
    val native: String?,
    val synonyms: List<String>,
    val episodes: Int?,
    val format: String?,
    val status: String?,
    val seasonYear: Int?,
    /** AniList average score out of 100 (e.g. 91). */
    val averageScore: Int?,
    val genres: List<String>,
    val description: String?,
    val coverImage: String?,
    val bannerImage: String?,
    val coverColor: String?,
) {
    val displayTitle: String get() = english ?: romaji ?: native ?: "Unknown"

    /** All title variants, used to match this show against allanime's (often garbage) names. */
    val allTitles: List<String>
        get() = listOfNotNull(romaji, english, native).plus(synonyms).distinct()

    val scoreOutOfTen: Double? get() = averageScore?.let { it / 10.0 }

    /** Synopsis with AniList's HTML tags stripped. */
    val plainDescription: String?
        get() = description?.replace(Regex("<br\\s*/?>"), " ")
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace("&quot;", "\"")?.replace("&amp;", "&")
            ?.replace("&#039;", "'")?.replace(Regex("\\s+"), " ")?.trim()

    val statusLabel: String
        get() = when (status) {
            "RELEASING" -> "Airing"
            "FINISHED" -> "Completed"
            "NOT_YET_RELEASED" -> "Upcoming"
            "CANCELLED" -> "Cancelled"
            "HIATUS" -> "Hiatus"
            else -> status ?: ""
        }
}
