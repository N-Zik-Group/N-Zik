package app.n_zik.android.components.ui.screens.artist

internal fun artistOverviewSongListIndex(
    mediaId: String,
    hasInformation: Boolean,
    sections: List<Pair<List<String>, Int>>,
): Int {
    // Header, actions and search are fixed. Each section starts with its heading.
    var rowsBeforeSection = if (hasInformation) 4 else 3
    for ((songIds, carouselCount) in sections) {
        val trackIndex = songIds.indexOf(mediaId)
        if (trackIndex >= 0) return rowsBeforeSection + 1 + trackIndex
        rowsBeforeSection += 1 + songIds.size + carouselCount
    }
    return -1
}
