package app.n_zik.android.playback.utils

import android.os.Bundle
import androidx.media3.common.MediaItem
import app.n_zik.android.models.Song
import kotlin.random.Random

private const val SMART_SHUFFLE_RECOMMENDATION_EXTRA = "smartShuffleRecommendation"
private const val SMART_SHUFFLE_QUEUE_OWNER_EXTRA = "smartShuffleQueueOwner"
private const val SMART_SHUFFLE_TITLE_PREFIX = "\uD83D\uDD00\u2728 "
private const val EXPLICIT_TITLE_PREFIX = "\uD83C\uDD74 "

internal fun smartShuffleSessionTitle(title: String, explicit: Boolean): String =
    SMART_SHUFFLE_TITLE_PREFIX + (if (explicit) EXPLICIT_TITLE_PREFIX else "") + title

internal fun String.withoutSmartShuffleSessionPrefix(): String = removePrefix(SMART_SHUFFLE_TITLE_PREFIX)

internal val MediaItem.isSmartShuffleRecommendation: Boolean
    get() = mediaMetadata.extras?.getBoolean(SMART_SHUFFLE_RECOMMENDATION_EXTRA) == true

internal val MediaItem.smartShuffleQueueOwner: String?
    get() = mediaMetadata.extras?.getString(SMART_SHUFFLE_QUEUE_OWNER_EXTRA)

internal fun MediaItem.asSmartShuffleRecommendation(recommended: Boolean, queueOwner: String? = null): MediaItem {
    if (!recommended || (isSmartShuffleRecommendation && queueOwner == null)) return this
    val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
        putBoolean(SMART_SHUFFLE_RECOMMENDATION_EXTRA, true)
        if (queueOwner != null) putString(SMART_SHUFFLE_QUEUE_OWNER_EXTRA, queueOwner)
    }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}

internal fun smartShuffleRecommendationIds(
    songs: List<Song>,
    recommendations: Map<Song, Int>,
): Set<String> {
    if (recommendations.isEmpty()) return emptySet()
    val originalIds = songs.mapTo(HashSet(), Song::id)
    return recommendations.keys.asSequence().map(Song::id).filterNot(originalIds::contains).toSet()
}

/** Use an even spacing with a random offset over the gaps before, between and after songs. */
private fun recommendationGaps(songCount: Int, recommendationCount: Int, random: Random): List<Int> {
    val gapCount = songCount + 1L
    val offset = random.nextLong(gapCount)
    return List(recommendationCount) { index ->
        ((index * gapCount + offset) / recommendationCount).toInt()
    }
}

/** Descending original gaps keep sequential insertion indices valid for the UI and Bridge. */
internal fun <T> smartShuffleInsertionPositions(
    songCount: Int,
    recommendations: List<T>,
    random: Random = Random.Default,
): Map<T, Int> {
    if (recommendations.isEmpty()) return emptyMap()
    val gaps = recommendationGaps(songCount, recommendations.size, random)
    return recommendations.shuffled(random).zip(gaps).asReversed().toMap()
}

/** Keep both groups in their randomized order while spreading recommendations through the queue. */
internal fun spreadSmartShuffleRecommendations(
    shuffledItems: List<MediaItem>,
    random: Random = Random.Default,
): List<MediaItem> =
    spreadSmartShuffleRecommendations(shuffledItems, random, MediaItem::isSmartShuffleRecommendation)

internal fun <T> spreadSmartShuffleRecommendations(
    shuffledItems: List<T>,
    random: Random = Random.Default,
    recommended: (T) -> Boolean,
): List<T> {
    if (shuffledItems.none(recommended)) return shuffledItems
    val (recommendations, songs) = shuffledItems.partition(recommended)
    val gaps = recommendationGaps(songs.size, recommendations.size, random)
    return buildList(shuffledItems.size) {
        var recommendationIndex = 0
        for (gap in 0..songs.size) {
            while (gaps.getOrNull(recommendationIndex) == gap) {
                add(recommendations[recommendationIndex++])
            }
            songs.getOrNull(gap)?.let(::add)
        }
    }
}
