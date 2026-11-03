package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.components.ui.screens.profiles.ProfileCardSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassImmediateParentSurfaceTest {
    @get:Rule val compose = createComposeRule()

    private val neutral = GlassSettings(enabled = true, blur = 0f, film = 0f,
        rim = 0f, depth = 0f, amount = 0f, vibrancy = 0f)

    @Test fun `settings section badge reflects the actual card film and mounted source changes`() {
        verifyCardChild(profile = false)
    }

    @Test fun `profile status chip reflects the actual card film and mounted source changes`() {
        verifyCardChild(profile = true)
    }

    @Test fun `real search leading badge reflects mounted search panel fill changes`() {
        val fill = mutableStateOf(Color.Red)
        compose.setContent { AppearanceProvider {
            val playerFreePage = rememberCanvasBackdrop { drawRect(Color.Blue) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides playerFreePage,
                LocalGlassMaterialSource provides playerFreePage,
                LocalGlassPreviewSettings provides neutral.copy(film = .7f)) {
                Box(Modifier.size(320.dp, 100.dp).background(Color.Blue)) {
                    UnifiedSearchBar(TextFieldValue(""), {}, R.string.search_title,
                        baseSurface = fill.value, onClear = {})
                }
            }
        } }
        fun samples(): Pair<Color, Color> {
            val pixels = compose.onNodeWithTag("unified_search_bar").captureToImage().toPixelMap()
            val badgeX = with(compose.density) { 42.dp.roundToPx() }
            val rightInset = with(compose.density) { 24.dp.roundToPx() }
            return pixels[badgeX, pixels.height / 2] to pixels[pixels.width - rightInset, pixels.height / 2]
        }
        val (initialBadge, initialPanel) = samples()
        compose.runOnIdle { fill.value = Color.Blue }
        val (updatedBadge, updatedPanel) = samples()
        assertTrue("The search panel fill must visibly change for this fixture", colorDistance(initialPanel, updatedPanel) > .04f)
        assertTrue("The leading badge must reflect the changed panel beneath it", colorDistance(initialBadge, updatedBadge) > .04f)
    }

    private fun verifyCardChild(profile: Boolean) {
        val film = mutableStateOf(.45f)
        val page = mutableStateOf(Color.Blue)
        compose.setContent { AppearanceProvider {
            val source = rememberCanvasBackdrop { drawRect(page.value) }
            val unrelated = rememberCanvasBackdrop { drawRect(Color.Green) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides source,
                LocalGlassMaterialSource provides source, LocalGlassBackdrop provides unrelated) {
                Box(Modifier.size(240.dp, 180.dp).background(page.value).testTag("card.scene")) {
                    val content: @Composable () -> Unit = {
                        Box(Modifier.size(180.dp, 120.dp)) {
                            val fill = if (profile) Modifier.glassChipBackground(Color.Transparent,
                                RoundedCornerShape(8.dp), neutral)
                            else Modifier.iconBadgeBackground(Color.Transparent, RoundedCornerShape(8.dp), neutral)
                            Box(Modifier.offset(24.dp, 24.dp).size(40.dp).then(fill).testTag("card.child"))
                        }
                    }
                    if (profile) {
                        ProfileCardSurface(Modifier.padding(24.dp), settings = neutral.copy(film = film.value)) {
                            content()
                        }
                    } else {
                        SettingsCardSurface(Modifier.padding(24.dp), RoundedCornerShape(12.dp),
                            containerColor = Color.Red, backgroundColor = page.value, shadowColor = Color.Transparent,
                            shadowElevation = 0.dp, settings = neutral.copy(film = film.value)) {
                            content()
                        }
                    }
                }
            }
        } }
        fun assertChildMatchesCard(): Color {
            val root = compose.onNodeWithTag("card.scene").fetchSemanticsNode().boundsInRoot
            val child = compose.onNodeWithTag("card.child").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onNodeWithTag("card.scene").captureToImage().toPixelMap()
            val x = (child.center.x - root.left).toInt()
            val y = (child.center.y - root.top).toInt()
            val expected = pixels[(x + 80).coerceAtMost(pixels.width - 1), y]
            val actual = pixels[x, y]
            assertColor("Child must see rendered ${if (profile) "profile" else "settings"} card", expected, actual)
            assertTrue("Unrelated green application pixels must not enter the card", actual.green < actual.blue)
            return actual
        }
        val initial = assertChildMatchesCard()
        compose.runOnIdle { film.value = .85f }
        val updated = assertChildMatchesCard()
        assertTrue("Mounted child must observe the changed card film", colorDistance(initial, updated) > .025f)
        compose.runOnIdle { page.value = Color(.2f, .1f, .8f) }
        assertChildMatchesCard()
    }

    @Test fun `page floating icon samples actual content sibling objects and live updates without sampling itself`() {
        val objectColor = mutableStateOf(Color.Red)
        compose.setContent { AppearanceProvider {
            val unrelated = rememberCanvasBackdrop { drawRect(Color.Green) }
            CompositionLocalProvider(LocalGlassBackdrop provides unrelated) {
                val content = rememberGlassFloatingActionBackdrop(neutral)
                Box(Modifier.size(240.dp).background(Color.Blue).testTag("floating.scene")) {
                    Box(Modifier.size(240.dp).glassFloatingActionContent(content)) {
                        Box(Modifier.offset(40.dp, 40.dp).size(140.dp).background(objectColor.value))
                    }
                    GlassFloatingActions(content) {
                        GlassFloatingActionIcon(painterResource(R.drawable.play), {},
                            Modifier.offset(72.dp, 72.dp).testTag("floating.icon"),
                            size = 48.dp, tint = Color.Transparent, settings = neutral)
                    }
                }
            }
        } }
        fun actual() = compose.onNodeWithTag("floating.icon").captureToImage().toPixelMap().let {
            it[it.width / 2, it.height / 2]
        }
        assertColor("Floating icon must see the red content object", Color.Red, actual())
        compose.runOnIdle { objectColor.value = Color.Blue }
        assertColor("Floating icon must see the updated content object", Color.Blue, actual())
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            assertColor("Stable sibling capture must not feed back the floating icon", Color.Blue, actual())
        }
    }

    private fun assertColor(message: String, expected: Color, actual: Color) {
        assertEquals("$message red", expected.red, actual.red, .04f)
        assertEquals("$message green", expected.green, actual.green, .04f)
        assertEquals("$message blue", expected.blue, actual.blue, .04f)
    }

    private fun colorDistance(first: Color, second: Color): Float =
        kotlin.math.abs(first.red - second.red) + kotlin.math.abs(first.green - second.green) +
            kotlin.math.abs(first.blue - second.blue)

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(12.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
            Typography.fallback, shape, shape, shape), content = content)
    }
}
