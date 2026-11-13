package app.n_zik.android.components.settings.ai

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pins reset values to the pre-extraction AI page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AiSettingsPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val expected = mapOf<String, Any>(
        playEventsTypeKey to PlayEventsType.MostPlayed.name,
        showTipsKey to true,
        showRelatedAlbumsKey to true,
        showSimilarArtistsKey to true,
        showNewAlbumsArtistsKey to true,
        showNewAlbumsKey to true,
        showPlaylistMightLikeKey to true,
        showMoodsAndGenresKey to true,
        showMonthlyPlaylistInQuickPicksKey to true,
        showChartsKey to true,
        enableQuickPicksPageKey to true,
        "LocalRecommandationsNumber" to LocalRecommandationsNumber.SixQ.name,
        recommendationsNumberKey to RecommendationsNumber.Adaptive.name,
        showMyTopPlaylistKey to true,
        showStatsListeningTimeKey to true,
        maxStatisticsItemsKey to MaxStatisticsItems.`10`.name,
        maxStatisticsItemsCustomValueKey to 10,
        MaxTopPlaylistItemsKey to MaxTopPlaylistItems.`10`.name,
        MaxTopPlaylistItemsCustomValueKey to 10,
    )

    @Before fun setUp() {
        setActiveProfile("ai-test", context)
        context.preferences.edit().clear().commit()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() { setActiveProfile("default", context) }

    @Test fun `reset preserves baseline values and unrelated profile settings`() {
        context.preferences.edit().putString("sentinel", "keep").commit()
        val other = context.getSharedPreferences("preferences_other", Context.MODE_PRIVATE)
        other.edit().clear().putString("sentinel", "other").commit()
        applyAiDefaults(context)
        assertEquals(expected + mapOf("sentinel" to "keep"), context.preferences.all)
        assertEquals(mapOf("sentinel" to "other"), other.all)
        context.preferences.edit().putBoolean(showTipsKey, false).commit()
        assertTrue(!context.preferences.getBoolean(showTipsKey, true))
    }

    @Test fun `target inventory preserves original group order and unique keys`() {
        assertEquals(listOf("ai.general", "ai.quick-picks", "ai.rewind", "ai.recommendations", "ai.statistics", "ai.top-playlists", "ai.data", "ai.reset"), AiSettingTargets.cards)
        assertEquals(19, AiSettingTargets.preferenceKeys.toSet().size)
        assertEquals(AiSettingTargets.actions.size, AiSettingTargets.actions.toSet().size)
    }
}
