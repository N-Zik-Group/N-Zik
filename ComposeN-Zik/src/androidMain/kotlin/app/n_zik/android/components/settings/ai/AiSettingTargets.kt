package app.n_zik.android.components.settings.ai

import app.it.fast4x.rimusic.enums.LocalRecommandationsNumber
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.maxStatisticsItemsCustomValueKey
import app.it.fast4x.rimusic.utils.maxStatisticsItemsKey
import app.it.fast4x.rimusic.utils.playEventsTypeKey
import app.it.fast4x.rimusic.utils.recommendationsNumberKey
import app.it.fast4x.rimusic.utils.showChartsKey
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistInQuickPicksKey
import app.it.fast4x.rimusic.utils.showMoodsAndGenresKey
import app.it.fast4x.rimusic.utils.showMyTopPlaylistKey
import app.it.fast4x.rimusic.utils.showNewAlbumsArtistsKey
import app.it.fast4x.rimusic.utils.showNewAlbumsKey
import app.it.fast4x.rimusic.utils.showPlaylistMightLikeKey
import app.it.fast4x.rimusic.utils.showRelatedAlbumsKey
import app.it.fast4x.rimusic.utils.showSimilarArtistsKey
import app.it.fast4x.rimusic.utils.showStatsListeningTimeKey
import app.it.fast4x.rimusic.utils.showTipsKey

/** Stable local targets for future global search. */
internal object AiSettingTargets {
    val cards = listOf("ai.general", "ai.quick-picks", "ai.rewind", "ai.recommendations", "ai.statistics", "ai.top-playlists", "ai.data", "ai.reset")
    val preferenceKeys = listOf(playEventsTypeKey, showTipsKey, showRelatedAlbumsKey, showSimilarArtistsKey, showNewAlbumsArtistsKey, showNewAlbumsKey, showPlaylistMightLikeKey, showMoodsAndGenresKey, showMonthlyPlaylistInQuickPicksKey, showChartsKey, enableQuickPicksPageKey, "LocalRecommandationsNumber", recommendationsNumberKey, showMyTopPlaylistKey, showStatsListeningTimeKey, maxStatisticsItemsKey, maxStatisticsItemsCustomValueKey, MaxTopPlaylistItemsKey, MaxTopPlaylistItemsCustomValueKey)
    val actions = listOf("ai.quick-picks.content", "ai.rewind.open", "ai.statistics.open", "ai.data.clear-events", "ai.reset.apply")
}
