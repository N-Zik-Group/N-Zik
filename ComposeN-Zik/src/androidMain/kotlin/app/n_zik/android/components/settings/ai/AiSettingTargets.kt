package app.n_zik.android.components.settings.ai

import app.n_zik.android.enums.LocalRecommandationsNumber
import app.n_zik.android.utils.MaxTopPlaylistItemsCustomValueKey
import app.n_zik.android.utils.MaxTopPlaylistItemsKey
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.maxStatisticsItemsCustomValueKey
import app.n_zik.android.utils.maxStatisticsItemsKey
import app.n_zik.android.utils.playEventsTypeKey
import app.n_zik.android.utils.recommendationsNumberKey
import app.n_zik.android.utils.showChartsKey
import app.n_zik.android.utils.showMonthlyPlaylistInQuickPicksKey
import app.n_zik.android.utils.showMoodsAndGenresKey
import app.n_zik.android.utils.showMyTopPlaylistKey
import app.n_zik.android.utils.showNewAlbumsArtistsKey
import app.n_zik.android.utils.showNewAlbumsKey
import app.n_zik.android.utils.showPlaylistMightLikeKey
import app.n_zik.android.utils.showRelatedAlbumsKey
import app.n_zik.android.utils.showSimilarArtistsKey
import app.n_zik.android.utils.showStatsListeningTimeKey
import app.n_zik.android.utils.showTipsKey

/** Stable local targets for future global search. */
internal object AiSettingTargets {
    val cards = listOf("ai.general", "ai.quick-picks", "ai.rewind", "ai.recommendations", "ai.statistics", "ai.top-playlists", "ai.data", "ai.reset")
    val preferenceKeys = listOf(playEventsTypeKey, showTipsKey, showRelatedAlbumsKey, showSimilarArtistsKey, showNewAlbumsArtistsKey, showNewAlbumsKey, showPlaylistMightLikeKey, showMoodsAndGenresKey, showMonthlyPlaylistInQuickPicksKey, showChartsKey, enableQuickPicksPageKey, "LocalRecommandationsNumber", recommendationsNumberKey, showMyTopPlaylistKey, showStatsListeningTimeKey, maxStatisticsItemsKey, maxStatisticsItemsCustomValueKey, MaxTopPlaylistItemsKey, MaxTopPlaylistItemsCustomValueKey)
    val actions = listOf("ai.quick-picks.content", "ai.rewind.open", "ai.statistics.open", "ai.data.clear-events", "ai.reset.apply")
}
