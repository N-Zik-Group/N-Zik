package app.n_zik.android.components.settings.appearance

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

/** Literal reset fixture from AppearanceSettings at baseline 89c9a371. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AppearanceSettingsPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun store(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Before fun setUp() {
        listOf("preferences", "preferences_appearance-test", "preferences_untouched", "app_settings").forEach {
            store(it).edit().clear().commit()
        }
        setActiveProfile("appearance-test", context)
    }
    @After fun tearDown() { setActiveProfile("default", context) }

    private val baselineDefaults: Map<String, Any> = mapOf(
        "isShowingThumbnailInLockscreen" to true,
        "showthumbnail" to true,
        "transparentbar" to true,
        "blackgradient" to false,
        "showlyricsthumbnail" to true,
        "expandedplayer" to false,
        "playerPlayButtonType" to "CircularRibbed",
        "bottomgradient" to false,
        "textoutline" to false,
        "lastPlayerPlayButtonType" to "Rectangular",
        "disablePlayerHorizontalSwipe" to false,
        "playerSwipeSensitivity" to "Medium",
        "disableScrollingText" to false,
        "showLikeButtonBackgroundPlayer" to true,
        "showDownloadButtonBackgroundPlayer" to true,
        "visualizerEnabled" to false,
        "visualizerWhiteColorOption" to "White",
        "visualizerCustomColor" to -1,
        "playerVisualizerType" to "Disabled",
        "visualizerLineThickness" to 6f,
        "playerTimelineType" to "Wavy",
        "thumbnailSizeDpKey" to 90f,
        "thumbnailSizeLDpKey" to 0f,
        "playerTimelineSize" to "Biggest",
        "effectRotation" to false,
        "thumbnailTapEnabled" to true,
        "showButtonPlayerAddToPlaylist" to true,
        "showButtonPlayerArrow" to true,
        "showButtonPlayerDownload" to true,
        "showButtonPlayerLoop" to false,
        "showButtonPlayerLyrics" to true,
        "expandedplayertoggle" to false,
        "showButtonPlayerShuffle" to true,
        "showButtonPlayerSleepTimer" to false,
        "showButtonPlayerMenu" to false,
        "showButtonPlayerStartRadio" to false,
        "showButtonPlayerSystemEqualizer" to false,
        "showButtonPlayerDiscover" to false,
        "showButtonPlayerVideo" to true,
        "showTotalTimeQueue" to true,
        "backgroundProgress" to "MiniPlayer",
        "showNextSongsInPlayer" to false,
        "showRemainingSongTime" to true,
        "showSkipTimeButtons" to true,
        "clickOnLyricsText" to true,
        "lyricsColor" to "White",
        "lyricsCustomColor" to -1,
        "showBackgroundLyrics" to false,
        "thumbnailRoundnessDpKey" to 12f,
        "artistThumbnailRoundnessDpKey" to 48f,
        "uiRoundnessDpKey" to 25f,
        "miniPlayerType" to "Essential",
        "playerBackgroundColors" to "AnimatedGradient",
        "showTopActionsBar" to true,
        "playerControlsType" to "Essential",
        "playerControlsColors" to "Monochrome",
        "playerInfoType" to "Modern",
        "transparentBackgroundPlayerActionBar" to true,
        "iconLikeType" to "Essential",
        "playerSwapControlsWithTimeline" to false,
        "playerEnableLyricsPopupMessage" to true,
        "actionspacedevenly" to true,
        "thumbnailType" to "Modern",
        "showvisthumbnail" to true,
        "blackBackgroundForVisThumbnail" to true,
        "showVisualizerButtons" to true,
        "buttonzoomout" to true,
        "thumbnailpause" to true,
        "cropVideoThumbnails" to true,
        "showsongs" to "2",
        "showalbumcover" to true,
        "prevNextSongs" to "twosongs",
        "tapqueue" to true,
        "swipeUpQueue" to true,
        "statsfornerds" to false,
        "playerType" to "Essential",
        "queueType" to "Modern",
        "noblur" to true,
        "fadingedge" to false,
        "carousel" to true,
        "carouselSize" to "Biggest",
        "keepPlayerMinimized" to true,
        "playerInfoShowIcons" to true,
        "queueDurationExpanded" to true,
        "titleExpanded" to true,
        "timelineExpanded" to true,
        "controlsExpanded" to true,
        "miniQueueExpanded" to true,
        "statsExpanded" to true,
        "actionExpanded" to true,
        "showCoverThumbnailAnimation" to false,
        "coverThumbnailAnimation" to "Vinyl",
        "showLyricsStateKey" to false,
        "showVisualizerStateKey" to false,
        "notificationPlayerFirstIcon" to "Download",
        "notificationPlayerSecondIcon" to "Favorites",
        "topPadding" to true,
        "animatedGradient" to "M3EMorphingCover",
        "shakeSensitivityTheme" to "Disabled",
        "albumCoverRotation" to false,
        "blurScale" to 25f,
        "thumbnailFadeEx" to 5f,
        "thumbnailFade" to 5f,
        "thumbnailSpacing" to 0f,
        "colorPaletteName" to "Dynamic",
        "colorPaletteMode" to "System",
        "swipeAnimationsNoThumbnail" to "Sliding",
        "rotatingAlbumCover" to false,
    )

    @Test fun resetMatchesAllBaselineValuesAndPreservesOtherStores() {
        val editor = context.preferences.edit()
        baselineDefaults.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, !value)
                is Float -> editor.putFloat(key, value + 10f)
                is Int -> editor.putInt(key, 123)
                is String -> editor.putString(key, "different")
            }
        }
        editor.putString("unrelated", "keep").commit()
        store("preferences_untouched").edit().putString("colorPaletteMode", "Light").commit()
        val globalBefore = store("app_settings").all.toMap()
        applyAppearanceDefaults(context)
        baselineDefaults.forEach { (key, expected) -> assertEquals(key, expected, context.preferences.all[key]) }
        assertEquals(108, baselineDefaults.size)
        assertEquals("keep", context.preferences.getString("unrelated", null))
        assertEquals("Light", store("preferences_untouched").getString("colorPaletteMode", null))
        assertEquals(globalBefore, store("app_settings").all)
        assertTrue(store("preferences").all.isEmpty())
        val afterFirstReset = context.preferences.all.toMap()
        applyAppearanceDefaults(context)
        assertEquals(afterFirstReset, context.preferences.all)
    }

    @Test fun targetsRetainCardOrderAndUniqueStableIds() {
        assertEquals(listOf("presets", "cover", "timeline", "colors", "behavior", "lyrics", "actionBar", "fullscreenLyrics", "notification", "reset"),
            AppearanceSettingTargets.cards.map { it.id.removePrefix("appearance.card.") })
        assertEquals(AppearanceSettingTargets.ids.size, AppearanceSettingTargets.ids.distinct().size)
        assertTrue(AppearanceSettingTargets.ids.contains("appearance.card.cover.setting.cropVideoThumbnails"))
        assertTrue(AppearanceSettingTargets.ids.contains("appearance.card.reset.action.applyAppearanceDefaults"))
    }
}
