package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.expandedplayerKey
import app.it.fast4x.rimusic.utils.fadingedgeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.thumbnailFadeExKey
import app.it.fast4x.rimusic.utils.thumbnailSpacingKey
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ThumbnailOffsetDialog
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the retained foreground recording as well as real layout coordinates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w400dp-h900dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLiveArtworkLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `thumbnail dialog changes keep portrait foreground centered without reopening player`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.preferences.edit().clear().putBoolean(expandedplayerKey, true)
            .putBoolean(fadingedgeKey, true).commit()
        var dialogVisible by mutableStateOf(false)
        lateinit var pager: PagerState
        val shape = RoundedCornerShape(16.dp)
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
            ) {
                var spacing by rememberPreference(thumbnailSpacingKey, 0f)
                var fade by rememberPreference(thumbnailFadeExKey, 5f)
                val overflow = rememberPlayerArtworkShadowOverflow()
                pager = rememberPagerState(initialPage = 500) { 1_000 }
                PlayerGlassBudgetLayout(
                    enabled = false, expandedCarousel = true, reserveLowerContent = true,
                    modifier = Modifier.size(400.dp, 800.dp).background(Color.Black)
                        .testTag("live.player").playerArtworkShadowOverflow(overflow),
                    header = {
                        Box(Modifier.fillMaxWidth().height(40.dp).testTag("live.header"), Alignment.Center) {
                            Box(Modifier.size(8.dp).background(Color.Yellow))
                        }
                    },
                    artwork = {
                        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)
                            .playerArtworkOverflowViewport(overflow)) {
                            val animatedSpacing by animatePlayerCarouselDp(
                                (spacing * 9f - 270f).dp, maxWidth, "dialogSpacing")
                            val geometry = animatePlayerCarouselGeometry(maxWidth, maxHeight, 90.dp,
                                animatedSpacing, true)
                            VerticalPager(
                                state = pager, pageSize = PageSize.Fixed(geometry.pageSize),
                                pageSpacing = geometry.pageSpacing, beyondViewportPageCount = 2,
                                contentPadding = PaddingValues(vertical = (maxHeight - geometry.pageSize) / 2),
                                modifier = Modifier.fillMaxSize().playerArtworkOverflowFade(overflow, true, fade * .05f),
                            ) { page ->
                                Box(Modifier.fillMaxSize().registerPlayerArtworkShadow(overflow, shape,
                                    alpha = { 1f }, zIndex = { if (page == 500) 1f else 0f }, shadow = false)
                                    .background(if (page == 500) Color.Blue else Color.Red))
                            }
                        }
                    },
                    information = {
                        Box(Modifier.fillMaxWidth().height(120.dp).testTag("live.information"), Alignment.Center) {
                            Box(Modifier.size(8.dp).background(Color.Green))
                        }
                    },
                )
                DialogPresence(dialogVisible) {
                    ThumbnailOffsetDialog(onDismiss = { dialogVisible = false },
                        spacingValue = { spacing = it }, spacingValueL = {},
                        fadeValue = {}, fadeValueEx = { fade = it }, imageCoverSizeValue = {})
                }
            }
        }
        compose.waitForIdle()
        val originalPager = pager
        val playerBounds = compose.onNodeWithTag("live.player").fetchSemanticsNode().boundsInRoot
        val initialBounds = listOf("live.header", "live.information").associateWith {
            compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot
        }
        fun verifyForeground() {
            val image = compose.onNodeWithTag("live.player").captureToImage().toPixelMap()
            initialBounds.forEach { (tag, original) ->
                val current = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertEquals("Live dialog updates must preserve $tag bounds", original, current)
                assertEquals(playerBounds.center.x, current.center.x, 1f)
                val marker = image[(current.center.x - playerBounds.left).toInt(),
                    (current.center.y - playerBounds.top).toInt()]
                assertEquals("Retained foreground pixels must match $tag coordinates",
                    if (tag == "live.header") Color.Yellow else Color.Green, marker)
            }
            compose.runOnIdle {
                assertSame("The player must not be recreated to restore placement", originalPager, pager)
                assertEquals(500, pager.currentPage)
                assertEquals(0f, pager.currentPageOffsetFraction, .001f)
            }
        }
        verifyForeground()
        for ((spacing, fade) in listOf(-27f to 0f, -50f to 10f, 50f to 5f, 0f to 0f)) {
            compose.runOnIdle { dialogVisible = true }
            compose.waitForIdle()
            // Same preference notification path used by ThumbnailOffsetDialog's slider callbacks.
            compose.runOnIdle {
                context.preferences.edit().putFloat(thumbnailSpacingKey, spacing)
                    .putFloat(thumbnailFadeExKey, fade).commit()
            }
            compose.onNodeWithText(context.getString(R.string.common_action_close)).performClick()
            compose.waitForIdle()
            verifyForeground()
        }
    }
}
