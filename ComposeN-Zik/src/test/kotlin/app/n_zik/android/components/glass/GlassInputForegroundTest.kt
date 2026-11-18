package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassInputForegroundTest {
    @get:Rule val compose = createComposeRule()

    private val shape = RoundedCornerShape(12.dp)
    private val appearance = Appearance(DefaultDarkColorPalette.copy(text = Color.White),
        Typography.fallback.copy(color = Color.White), shape, shape, shape)

    private fun textColor(tag: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.color
    }

    @Test fun `real fields and search resolve white film and transparent film without changing semantic ink`() {
        val settings = mutableStateOf(GlassSettings(quality = GlassQuality.Performance,
            source = GlassTintSource.Custom, customColor = 0xFFFFFFFF.toInt(),
            tintStrength = 1f, film = 1f, rim = 0f, depth = 0f))
        val disabledInk = Color(0xFF909090).copy(alpha = .38f)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides settings.value) {
                Column(Modifier.background(DefaultDarkColorPalette.background0)) {
                    val colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        disabledTextColor = disabledInk, errorTextColor = Color.Red,
                        focusedContainerColor = Color.Black, unfocusedContainerColor = Color.Black,
                    )
                    GlassTextField("String", {}, Modifier.testTag("string"),
                        textStyle = TextStyle(color = Color.White), colors = colors)
                    GlassTextField(TextFieldValue("Value"), {}, Modifier.testTag("value"), colors = colors)
                    GlassOutlinedTextField("Outlined", {}, Modifier.testTag("outlined"), colors = colors)
                    GlassTextField("Error", {}, Modifier.testTag("error"), isError = true, colors = colors)
                    GlassTextField("Disabled", {}, Modifier.testTag("disabled"), enabled = false, colors = colors)
                    UnifiedSearchBar(TextFieldValue("Search"), {}, R.string.search_title,
                        onClear = {}, textFieldTestTag = "search")
                }
            }
        }
        fun assertNeutralInk(expected: Color) {
            listOf("string", "value", "outlined", "search").forEach { assertEquals(it, expected, textColor(it)) }
            assertEquals(Color.Red, textColor("error"))
            assertEquals(disabledInk, textColor("disabled"))
        }
        assertNeutralInk(Color.Black)
        val pixels = compose.onNodeWithTag("string").captureToImage().toPixelMap()
        val fill = pixels[8, pixels.height / 2]
        assertEquals(1f, fill.red, .03f)
        assertEquals(1f, fill.green, .03f)
        assertEquals(1f, fill.blue, .03f)
        compose.runOnIdle { settings.value = settings.value.copy(film = 0f) }
        assertNeutralInk(Color.White)
        compose.runOnIdle { settings.value = settings.value.copy(enabled = false) }
        assertNeutralInk(Color.White)
    }

    @Test fun `focused film follows focused container and resolves explicit text style`() {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(quality = GlassQuality.Performance,
                    source = GlassTintSource.None, tintStrength = 0f, film = 1f, rim = 0f)) {
                GlassTextField("Focus", {}, Modifier.testTag("focus"),
                    textStyle = TextStyle(color = Color.Black),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.White))
            }
        }
        assertEquals(Color.Black, textColor("focus"))
        compose.onNodeWithTag("focus").performClick()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(Color.White, textColor("focus"))
    }
}
