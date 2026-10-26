package app.n_zik.android.components.settings.ai

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
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
