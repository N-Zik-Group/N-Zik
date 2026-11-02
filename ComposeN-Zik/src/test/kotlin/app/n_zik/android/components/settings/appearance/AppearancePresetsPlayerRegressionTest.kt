package app.n_zik.android.components.settings.appearance

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.AnimatedGradient
import app.it.fast4x.rimusic.enums.CarouselSize
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.enums.ThumbnailType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.GetControls
import app.it.fast4x.rimusic.utils.actionspacedevenlyKey
import app.it.fast4x.rimusic.utils.albumCoverRotationKey
import app.it.fast4x.rimusic.utils.animatedGradientKey
import app.it.fast4x.rimusic.utils.blurStrengthKey
import app.it.fast4x.rimusic.utils.bottomgradientKey
import app.it.fast4x.rimusic.utils.carouselKey
import app.it.fast4x.rimusic.utils.carouselSizeKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.controlsExpandedKey
import app.it.fast4x.rimusic.utils.expandedplayerKey
import app.it.fast4x.rimusic.utils.expandedplayertoggleKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.noblurKey
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.playerInfoTypeKey
import app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerSwapControlsWithTimelineKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showButtonPlayerAddToPlaylistKey
import app.it.fast4x.rimusic.utils.showButtonPlayerArrowKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.showButtonPlayerDownloadKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLoopKey
import app.it.fast4x.rimusic.utils.showButtonPlayerLyricsKey
import app.it.fast4x.rimusic.utils.showButtonPlayerMenuKey
import app.it.fast4x.rimusic.utils.showButtonPlayerShuffleKey
import app.it.fast4x.rimusic.utils.showButtonPlayerSleepTimerKey
import app.it.fast4x.rimusic.utils.showButtonPlayerStartRadioKey
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.showCoverThumbnailAnimationKey
import app.it.fast4x.rimusic.utils.showNextSongsInPlayerKey
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showTopActionsBarKey
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.thumbnailFadeExKey
import app.it.fast4x.rimusic.utils.thumbnailFadeKey
import app.it.fast4x.rimusic.utils.thumbnailRoundnessDpKey
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.thumbnailSizeLDpKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingLKey
import app.it.fast4x.rimusic.utils.thumbnailTypeKey
import app.it.fast4x.rimusic.utils.timelineExpandedKey
import app.it.fast4x.rimusic.utils.titleExpandedKey
import app.it.fast4x.rimusic.utils.topPaddingKey
import app.it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import app.it.fast4x.rimusic.utils.transparentbarKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercise the real chooser and transport using Robolectric-only Application preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w800dp-h1200dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalAnimationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppearancePresetsPlayerRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val preferences get() = context.getSharedPreferences("appearance-presets-player-regression", Context.MODE_PRIVATE)
    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    // Match the app's bounded UI corners: a circular dialog clips its footer's physical hit targets.
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(20.dp, 0.4f)),
        artistThumbnailShape = CircleShape,
    )

    @Before fun setup() {
        listenTogetherGuestLock.value = false
        setActiveProfile("default", context)
        preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        every { binder.player } returns player
        every { player.currentMediaItem } returns null
        val songs = mockk<SongTable>()
        val formats = mockk<FormatTable>()
        every { songs.likeState(any()) } returns flowOf(null)
        every { songs.findById(any()) } returns flowOf(null)
        every { formats.findBySongId(any()) } returns flowOf(null)
        mockkObject(Database)
        every { Database.songTable } returns songs
        every { Database.formatTable } returns formats
    }

    @After fun cleanup() {
        preferences.edit().clear().commit()
        listenTogetherGuestLock.value = false
        unmockkAll()
    }

    @Test fun `all six actual presets keep their contracts and fit native transport in both orientations and Glass modes`() {
        // Phase zero unmounts preference listeners before the next fixture is seeded.
        val phase = mutableStateOf(0)
        val landscape = mutableStateOf(false)
        val width = mutableStateOf(360.dp)
        val fixturePreferences = preferences
        var boundPreferences: SharedPreferences? = null
        compose.setContent {
            val activityContext = LocalContext.current
            val fixtureContext = remember(activityContext) {
                object : ContextWrapper(activityContext) {
                    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                        if (name == "preferences") fixturePreferences else super.getSharedPreferences(name, mode)
                }
            }
            boundPreferences = fixtureContext.preferences
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape.value) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalAppearance provides appearance, LocalConfiguration provides configuration,
                LocalContext provides fixtureContext) {
                if (phase.value == 1) AppearancePresetsCard(Search(initialVisible = true))
                else if (phase.value == 2) {
                    val size = rememberPreference(playerTimelineSizeKey, PlayerTimelineSize.Biggest).value
                    Box(Modifier.requiredWidth(width.value).testTag("preset.transport.viewport")) {
                        Column(Modifier.padding(horizontal = size.size.dp)) {
                            GetControls(binder, { 0L }, false, false, null, "preset-contract", {},
                                DefaultDarkColorPalette, timelineHorizontalInset = size.size.dp)
                        }
                    }
                }
            }
        }
        for ((index, contract) in contracts.withIndex()) {
            compose.runOnIdle {
                landscape.value = false
                val seed = preferences.edit().clear()
                // Begin with different valid values: a no-op preference setter need not persist a key.
                for ((key, value) in common + contract) {
                    when (value) {
                        is Boolean -> seed.putBoolean(key, !value)
                        is Float -> seed.putFloat(key, value + 1f)
                        is String -> seed.putString(key, enumValues.getValue(key).first { it != value })
                    }
                }
                seed
                    .putBoolean("buttonzoomout", false)
                    .putBoolean(carouselKey, false)
                    .putBoolean(titleExpandedKey, false)
                    .putBoolean(timelineExpandedKey, false)
                    .putBoolean(controlsExpandedKey, false)
                    .putBoolean(playerSwapControlsWithTimelineKey, true)
                    .putBoolean(showCoverThumbnailAnimationKey, true)
                    .putBoolean(albumCoverRotationKey, true)
                    .putFloat(thumbnailSizeLDpKey, 67f)
                    .putFloat(thumbnailSpacingLKey, -17f)
                    .putFloat(thumbnailSizeDpKey, 82f)
                    .putBoolean(glassEnabledKey, true).commit()
                assertSame("UI and contract assertions must use the same isolated preferences", preferences, boundPreferences)
                phase.value = 1
            }
            applyPresetWithTouch(index)
            for ((key, expected) in common + contract) {
                assertEquals("Preset ${index + 1}: $key", expected, preferences.all[key])
            }
            // Presets intentionally leave these user options alone, including landscape geometry.
            assertEquals("Only Preset 2 enables the tapered stack", index == 1, preferences.all[carouselKey])
            assertEquals(false, preferences.all[titleExpandedKey])
            assertEquals(false, preferences.all[timelineExpandedKey])
            assertEquals(false, preferences.all[controlsExpandedKey])
            assertEquals(true, preferences.all[playerSwapControlsWithTimelineKey])
            assertEquals(true, preferences.all[showCoverThumbnailAnimationKey])
            assertEquals(true, preferences.all[albumCoverRotationKey])
            assertEquals(67f, preferences.all[thumbnailSizeLDpKey])
            assertEquals(-17f, preferences.all[thumbnailSpacingLKey])
            assertEquals(true, preferences.all[glassEnabledKey])
            if (index == 2) assertEquals("Hidden-cover preset retains artwork size", 82f, preferences.all[thumbnailSizeDpKey])

            compose.runOnIdle { phase.value = 2 }
            for (land in listOf(false, true)) for (w in listOf(360.dp, 640.dp)) {
                compose.runOnIdle { landscape.value = land; width.value = w; preferences.edit().putBoolean(glassEnabledKey, false).commit() }
                val viewport = compose.onNodeWithTag("preset.transport.viewport").fetchSemanticsNode().boundsInRoot
                val native = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
                val label = "Preset ${index + 1}, landscape=$land, width=$w"
                assertEquals(label, if (index == 3) 3 else 5, native.size)
                assertTrue("$label transport remains visible", native.all {
                    it.width > 0f && it.height > 0f && it.left >= viewport.left - 1f && it.right <= viewport.right + 1f
                })
                native.zipWithNext().forEach { (left, right) ->
                    assertTrue("$label buttons do not overlap", left.right <= right.left + 1f)
                }
                compose.runOnIdle { preferences.edit().putBoolean(glassEnabledKey, true).commit() }
                assertEquals("$label Glass retains transport geometry", native,
                    compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot })
            }
            compose.runOnIdle { phase.value = 0 }
            compose.waitForIdle()
        }
    }

    @Test fun `sequential actual preset clicks update mounted transport and retain unrelated user preferences`() {
        val seed = preferences.edit()
        // Persist every contract key so unchanged/default values remain observable in later presets.
        for ((key, value) in common + contracts.flatMap { it.entries }.associate { it.key to it.value }) {
            when (value) {
                is Boolean -> seed.putBoolean(key, !value)
                is Float -> seed.putFloat(key, value + 1f)
                is String -> seed.putString(key, enumValues.getValue(key).first { it != value })
            }
        }
        seed.putBoolean(carouselKey, false)
            .putBoolean(glassEnabledKey, true)
            .putBoolean(titleExpandedKey, false)
            .putBoolean(timelineExpandedKey, false)
            .putBoolean(controlsExpandedKey, false)
            .putBoolean(playerSwapControlsWithTimelineKey, true)
            .putBoolean(showCoverThumbnailAnimationKey, true)
            .putBoolean(albumCoverRotationKey, true)
            .putFloat(thumbnailSizeLDpKey, 67f)
            .putFloat(thumbnailSpacingLKey, -17f)
            .commit()
        val fixturePreferences = preferences
        var observedControls: PlayerControlsType? = null
        var observedButton: PlayerPlayButtonType? = null
        var observedThumbnailSize: Float? = null
        var transportMounts = 0
        var transportDisposals = 0
        compose.setContent {
            val activityContext = LocalContext.current
            val fixtureContext = remember(activityContext) {
                object : ContextWrapper(activityContext) {
                    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                        if (name == "preferences") fixturePreferences else super.getSharedPreferences(name, mode)
                }
            }
            CompositionLocalProvider(LocalAppearance provides appearance, LocalContext provides fixtureContext) {
                Column {
                    Box(Modifier.requiredWidth(360.dp).testTag("preset.live.transport")) {
                        val controls = rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential).value
                        val button = rememberPreference(playerPlayButtonTypeKey, PlayerPlayButtonType.CircularRibbed).value
                        val thumbnailSize = rememberPreference(thumbnailSizeDpKey, 90f).value
                        val size = rememberPreference(playerTimelineSizeKey, PlayerTimelineSize.Biggest).value
                        DisposableEffect(Unit) {
                            transportMounts++
                            onDispose { transportDisposals++ }
                        }
                        SideEffect {
                            observedControls = controls
                            observedButton = button
                            observedThumbnailSize = thumbnailSize
                        }
                        Column(Modifier.padding(horizontal = size.size.dp)) {
                            GetControls(binder, { 0L }, false, false, null, "preset-live", {},
                                DefaultDarkColorPalette, timelineHorizontalInset = size.size.dp)
                        }
                    }
                    AppearancePresetsCard(Search(initialVisible = true))
                }
            }
        }
        val transportGeometry = mutableMapOf<Int, List<Rect>>()
        // The real transport and chooser stay mounted together; no phase or preset key recreates them.
        for (index in listOf(0, 1, 2, 3, 4, 5, 1, 3, 1, 4, 5)) {
            applyPresetWithTouch(index)
            compose.runOnIdle {
                assertEquals("Preset ${index + 1}: existing controls observer updates",
                    contracts[index].getValue(playerControlsTypeKey), observedControls?.name)
                assertEquals("Preset ${index + 1}: existing button observer updates",
                    contracts[index].getValue(playerPlayButtonTypeKey), observedButton?.name)
                contracts[index][thumbnailSizeDpKey]?.let { expected ->
                    assertEquals("Preset ${index + 1}: mounted portrait size observer updates",
                        expected, observedThumbnailSize)
                }
                assertEquals("Transport host mounts only once", 1, transportMounts)
                assertEquals("Transport host is never disposed between presets", 0, transportDisposals)
            }
            val viewport = compose.onNodeWithTag("preset.live.transport").fetchSemanticsNode().boundsInRoot
            val buttons = compose.onAllNodes(hasAnyAncestor(hasTestTag("preset.live.transport")) and hasClickAction())
                .fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
            assertEquals("Preset ${index + 1}: mounted native controls variant", if (index == 3) 3 else 5, buttons.size)
            val play = buttons[buttons.size / 2]
            val expectedPlaySize = with(compose.density) { listOf(100, 60, 60, 44, 80, 100)[index].dp.toPx() }
            assertEquals("Preset ${index + 1}: actual mounted play width follows its style", expectedPlaySize, play.width, 1f)
            assertEquals("Preset ${index + 1}: actual mounted play height follows its style", expectedPlaySize, play.height, 1f)
            assertTrue("Preset ${index + 1}: mounted buttons remain visible", buttons.all {
                it.width > 0f && it.height > 0f && it.left >= viewport.left - 1f && it.right <= viewport.right + 1f
            })
            buttons.zipWithNext().forEach { (left, right) ->
                assertTrue("Preset ${index + 1}: mounted buttons do not overlap", left.right <= right.left + 1f)
            }
            val previousGeometry = transportGeometry.putIfAbsent(index, buttons)
            if (previousGeometry != null) {
                assertEquals("Preset ${index + 1}: returning to a preset restores its mounted geometry", previousGeometry, buttons)
            }
            if (index == 5) {
                val presetFive = checkNotNull(transportGeometry[4])
                assertTrue("Preset 5 -> 6 changes the real mounted play button from square to ribbed",
                    play.width > presetFive[presetFive.size / 2].width)
            }
            for ((key, expected) in common + contracts[index]) {
                assertEquals("Sequential preset ${index + 1}: $key", expected, preferences.all[key])
            }
            assertEquals(true, preferences.all[glassEnabledKey])
            assertEquals(false, preferences.all[titleExpandedKey])
            assertEquals(false, preferences.all[timelineExpandedKey])
            assertEquals(false, preferences.all[controlsExpandedKey])
            assertEquals(true, preferences.all[playerSwapControlsWithTimelineKey])
            assertEquals(true, preferences.all[showCoverThumbnailAnimationKey])
            assertEquals(true, preferences.all[albumCoverRotationKey])
            assertEquals(67f, preferences.all[thumbnailSizeLDpKey])
            assertEquals(-17f, preferences.all[thumbnailSpacingLKey])
        }
    }

    private fun applyPresetWithTouch(index: Int) {
        compose.onNodeWithText(context.getString(R.string.appearancepresets)).performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onNode(hasAnyAncestor(isDialog()) and hasScrollToIndexAction()).performScrollToIndex(index)
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val apply = compose.onNode(hasAnyAncestor(isDialog()) and hasClickAction() and
            hasText(context.getString(R.string.dialog_apply))).assertIsEnabled()
        val cardBounds = compose.onNodeWithTag("dialogFrameCard").fetchSemanticsNode().boundsInRoot
        val applyBounds = apply.fetchSemanticsNode().boundsInRoot
        assertTrue("Preset ${index + 1}: Apply must be inside the visible dialog", cardBounds.contains(applyBounds.center))
        val safeInset = with(compose.density) { (16 + 20).dp.toPx() }
        val unclippedInterior = Rect(cardBounds.left + safeInset, cardBounds.top + safeInset,
            cardBounds.right - safeInset, cardBounds.bottom - safeInset)
        assertTrue("Preset ${index + 1}: Apply must avoid the dialog's rounded clip", unclippedInterior.contains(applyBounds.center))
        apply.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    // Fixed contract values from the six existing presets, independent of the chooser implementation.
    private val common = mapOf<String, Any>(
        showTotalTimeQueueKey to false, showRemainingSongTimeKey to true,
        showlyricsthumbnailKey to false, transparentBackgroundPlayerActionBarKey to true,
        actionspacedevenlyKey to true, showButtonPlayerVideoKey to false,
        showButtonPlayerDiscoverKey to false, showButtonPlayerSleepTimerKey to false,
        showButtonPlayerArrowKey to true, showButtonPlayerStartRadioKey to false,
        showButtonPlayerMenuKey to true, visualizerEnabledKey to false,
    )

    private fun contract(type: PlayerType, controls: PlayerControlsType, info: PlayerInfoType,
        timeline: PlayerTimelineType, button: PlayerPlayButtonType, expanded: Boolean,
        top: Boolean, cover: Boolean, background: PlayerBackgroundColors, transparent: Boolean,
        next: Boolean, toggle: Boolean, add: Boolean, download: Boolean, shuffle: Boolean,
        lyrics: Boolean, loop: Boolean, extras: Map<String, Any>) = mapOf<String, Any>(
        playerTypeKey to type.name, playerControlsTypeKey to controls.name, playerInfoTypeKey to info.name,
        playerTimelineTypeKey to timeline.name, playerPlayButtonTypeKey to button.name,
        expandedplayerKey to expanded, showTopActionsBarKey to top, showthumbnailKey to cover,
        playerBackgroundColorsKey to background.name, transparentbarKey to transparent,
        showNextSongsInPlayerKey to next, expandedplayertoggleKey to toggle,
        showButtonPlayerAddToPlaylistKey to add, showButtonPlayerDownloadKey to download,
        showButtonPlayerShuffleKey to shuffle, showButtonPlayerLyricsKey to lyrics,
        showButtonPlayerLoopKey to loop,
    ) + extras

    private val enumValues = mapOf(
        playerTypeKey to PlayerType.entries.map { it.name },
        playerControlsTypeKey to PlayerControlsType.entries.map { it.name },
        playerInfoTypeKey to PlayerInfoType.entries.map { it.name },
        playerTimelineTypeKey to PlayerTimelineType.entries.map { it.name },
        playerPlayButtonTypeKey to PlayerPlayButtonType.entries.map { it.name },
        playerBackgroundColorsKey to PlayerBackgroundColors.entries.map { it.name },
        thumbnailTypeKey to ThumbnailType.entries.map { it.name },
        playerTimelineSizeKey to PlayerTimelineSize.entries.map { it.name },
        colorPaletteNameKey to ColorPaletteName.entries.map { it.name },
        colorPaletteModeKey to ColorPaletteMode.entries.map { it.name },
        carouselSizeKey to CarouselSize.entries.map { it.name },
        animatedGradientKey to AnimatedGradient.entries.map { it.name },
    )

    private val contracts = listOf(
        contract(PlayerType.Essential, PlayerControlsType.Essential, PlayerInfoType.Essential,
            PlayerTimelineType.ThinBar, PlayerPlayButtonType.CircularRibbed, true, true, true,
            PlayerBackgroundColors.BlurredCoverColor, true, false, false, true, false, true, false, false,
            mapOf(thumbnailSizeDpKey to 75f, thumbnailTypeKey to ThumbnailType.Modern.name,
                thumbnailRoundnessDpKey to 0f, blurStrengthKey to 50f, bottomgradientKey to true,
                playerTimelineSizeKey to PlayerTimelineSize.Biggest.name,
                colorPaletteNameKey to ColorPaletteName.Dynamic.name, colorPaletteModeKey to ColorPaletteMode.System.name)),
        contract(PlayerType.Modern, PlayerControlsType.Essential, PlayerInfoType.Essential,
            PlayerTimelineType.ThinBar, PlayerPlayButtonType.Disabled, true, true, true,
            PlayerBackgroundColors.BlurredCoverColor, true, true, true, true, false, false, false, false,
            mapOf(thumbnailSizeDpKey to 100f, thumbnailTypeKey to ThumbnailType.Essential.name,
                thumbnailRoundnessDpKey to 18f, blurStrengthKey to 50f, bottomgradientKey to true,
                fadingedgeKey to true, thumbnailFadeExKey to 4f, thumbnailSpacingKey to -32f,
                carouselKey to true, carouselSizeKey to CarouselSize.Big.name, colorPaletteNameKey to ColorPaletteName.Dynamic.name,
                colorPaletteModeKey to ColorPaletteMode.System.name)),
        contract(PlayerType.Modern, PlayerControlsType.Essential, PlayerInfoType.Modern,
            PlayerTimelineType.ThinBar, PlayerPlayButtonType.Disabled, true, false, false,
            PlayerBackgroundColors.BlurredCoverColor, true, false, false, false, false, false, false, false,
            mapOf(noblurKey to true, topPaddingKey to false, blurStrengthKey to 50f,
                playerInfoShowIconsKey to false, bottomgradientKey to true,
                colorPaletteNameKey to ColorPaletteName.Dynamic.name, colorPaletteModeKey to ColorPaletteMode.System.name)),
        contract(PlayerType.Modern, PlayerControlsType.Modern, PlayerInfoType.Essential,
            PlayerTimelineType.AudioWaves, PlayerPlayButtonType.Disabled, false, false, true,
            PlayerBackgroundColors.BlurredCoverColor, false, false, false, false, false, true, false, true,
            mapOf(carouselKey to false, thumbnailSizeDpKey to 100f, thumbnailTypeKey to ThumbnailType.Essential.name,
                thumbnailRoundnessDpKey to 9f, topPaddingKey to false, blurStrengthKey to 50f,
                playerTimelineSizeKey to PlayerTimelineSize.Biggest.name, bottomgradientKey to true,
                fadingedgeKey to true, thumbnailFadeKey to 5f, colorPaletteNameKey to ColorPaletteName.PureBlack.name)),
        contract(PlayerType.Modern, PlayerControlsType.Essential, PlayerInfoType.Essential,
            PlayerTimelineType.PinBar, PlayerPlayButtonType.Square, false, false, true,
            PlayerBackgroundColors.AnimatedGradient, false, false, true, false, true, false, false, false,
            mapOf(carouselKey to false, thumbnailSizeDpKey to 85f, thumbnailTypeKey to ThumbnailType.Modern.name,
                thumbnailRoundnessDpKey to 20f, topPaddingKey to true,
                playerTimelineSizeKey to PlayerTimelineSize.Biggest.name, animatedGradientKey to AnimatedGradient.Linear.name,
                fadingedgeKey to true, thumbnailFadeKey to 0f, thumbnailFadeExKey to 5f, thumbnailSpacingKey to -32f,
                colorPaletteNameKey to ColorPaletteName.Dynamic.name, colorPaletteModeKey to ColorPaletteMode.PitchBlack.name)),
        contract(PlayerType.Essential, PlayerControlsType.Essential, PlayerInfoType.Essential,
            PlayerTimelineType.Wavy, PlayerPlayButtonType.CircularRibbed, true, true, true,
            PlayerBackgroundColors.CoverColorGradient, false, false, false, false, false, true, true, false,
            mapOf(thumbnailSizeDpKey to 80f, thumbnailTypeKey to ThumbnailType.Modern.name,
                thumbnailRoundnessDpKey to 20f, playerTimelineSizeKey to PlayerTimelineSize.Biggest.name,
                colorPaletteNameKey to ColorPaletteName.Dynamic.name, colorPaletteModeKey to ColorPaletteMode.System.name)),
    )
}
