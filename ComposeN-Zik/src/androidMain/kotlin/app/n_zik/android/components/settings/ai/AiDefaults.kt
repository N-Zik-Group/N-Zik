package app.n_zik.android.components.settings.ai

import android.content.Context
import androidx.core.content.edit
import app.n_zik.android.enums.LocalRecommandationsNumber
import app.n_zik.android.enums.MaxStatisticsItems
import app.n_zik.android.enums.MaxTopPlaylistItems
import app.n_zik.android.enums.PlayEventsType
import app.n_zik.android.enums.RecommendationsNumber
import app.n_zik.android.utils.MaxTopPlaylistItemsCustomValueKey
import app.n_zik.android.utils.MaxTopPlaylistItemsKey
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.maxStatisticsItemsCustomValueKey
import app.n_zik.android.utils.maxStatisticsItemsKey
import app.n_zik.android.utils.playEventsTypeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.recommendationsNumberKey
import app.n_zik.android.utils.selectedCountryCodeKey
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
        remove(selectedCountryCodeKey)
    }
}
