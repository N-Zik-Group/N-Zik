package app.n_zik.android.components.settings.ai

import android.content.Context
import androidx.core.content.edit
import app.it.fast4x.rimusic.enums.LocalRecommandationsNumber
import app.it.fast4x.rimusic.enums.MaxStatisticsItems
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.it.fast4x.rimusic.enums.RecommendationsNumber
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.enableQuickPicksPageKey
import app.it.fast4x.rimusic.utils.maxStatisticsItemsCustomValueKey
import app.it.fast4x.rimusic.utils.maxStatisticsItemsKey
import app.it.fast4x.rimusic.utils.playEventsTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum
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
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog

/** Restores the original AI defaults once for the active profile. */
internal fun applyAiDefaults(context: Context) {
    QuickPicksContentSettingsDialog.reset(context)
    context.preferences.edit {
        putEnum(playEventsTypeKey, PlayEventsType.MostPlayed)
        putBoolean(showTipsKey, true)
        putBoolean(showRelatedAlbumsKey, true)
        putBoolean(showSimilarArtistsKey, true)
        putBoolean(showNewAlbumsArtistsKey, true)
        putBoolean(showNewAlbumsKey, true)
        putBoolean(showPlaylistMightLikeKey, true)
        putBoolean(showMoodsAndGenresKey, true)
        putBoolean(showMonthlyPlaylistInQuickPicksKey, true)
        putBoolean(showChartsKey, true)
        putBoolean(enableQuickPicksPageKey, true)
        putEnum("LocalRecommandationsNumber", LocalRecommandationsNumber.SixQ)
        putEnum(recommendationsNumberKey, RecommendationsNumber.Adaptive)
        putBoolean(showMyTopPlaylistKey, true)
        putBoolean(showStatsListeningTimeKey, true)
        putEnum(maxStatisticsItemsKey, MaxStatisticsItems.`10`)
        putInt(maxStatisticsItemsCustomValueKey, 10)
        putEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
        putInt(MaxTopPlaylistItemsCustomValueKey, 10)
    }
}
