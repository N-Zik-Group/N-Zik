package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.colorPalette
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassArtworkContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `readable gray roles and glyphs stay gray on a dark artwork pane`() {
        val gray = Color(0xFFB0B0B0)
        val source = DefaultLightColorPalette.copy(text = gray, textSecondary = gray,
            textDisabled = gray.copy(alpha = .8f), iconButtonPlayer = gray)
        val settings = GlassSettings(enabled = true, source = GlassTintSource.Artwork)
        var actual = DefaultLightColorPalette
        var content = Color.Unspecified
        var glyph = Color.Unspecified
        compose.setContent {
            val host = remember { GlassMenuHostState() }
            host.artworkTint = GlassArtworkTint(Color(0xFF242424), darkness = 1f)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(source,
                    typographyOf(source.text, true, false, FontType.Rubik),
                    RoundedCornerShape(12.dp), RoundedCornerShape(12.dp), RoundedCornerShape(12.dp)),
                LocalGlassMenuHost provides host,
                LocalContentColor provides gray,
            ) {
                GlassArtworkContent(settings) {
                    val palette = colorPalette()
                    val inherited = LocalContentColor.current
                    val ink = glassArtworkForeground(settings, gray)
                    SideEffect { actual = palette; content = inherited; glyph = ink }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(source, actual)
            assertEquals(gray, content)
            assertEquals(gray, glyph)
        }
    }

    @Test fun `artwork contrast preserves colored palette roles content and explicit glyphs exactly`() {
        val colored = DefaultLightColorPalette.copy(
            text = Color(0xFFB477E8), textSecondary = Color(0xFFF0FFF8),
            textDisabled = Color(0xFF22AA99).copy(alpha = .3f),
            iconButtonPlayer = Color(0xFFFFF0F0),
        )
        val settings = GlassSettings(enabled = true, source = GlassTintSource.Artwork)
        val content = Color(0xFFFF9933).copy(alpha = .7f)
        var actual = DefaultLightColorPalette
        var inherited = Color.Unspecified
        var glyph = Color.Unspecified
        var neutralGlyph = Color.Unspecified
        var pane = Color.Unspecified
        compose.setContent {
            val host = remember { GlassMenuHostState() }
            host.artworkTint = GlassArtworkTint(Color(0xFF242424), darkness = 1f)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(colored,
                    typographyOf(colored.text, true, false, FontType.Rubik),
                    RoundedCornerShape(12.dp), RoundedCornerShape(12.dp), RoundedCornerShape(12.dp)),
                LocalGlassMenuHost provides host,
                LocalContentColor provides content,
            ) {
                GlassArtworkContent(settings) {
                    val palette = colorPalette()
                    val inheritedInk = LocalContentColor.current
                    val selectedGlyph = glassArtworkForeground(settings, colored.iconButtonPlayer)
                    val grayGlyph = glassArtworkForeground(settings, Color.Black.copy(alpha = .4f))
                    val actualPane = glassSurfaceTint(settings)
                    SideEffect {
                        actual = palette; inherited = inheritedInk; glyph = selectedGlyph; neutralGlyph = grayGlyph; pane = actualPane
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(colored, actual)
            assertEquals(content, inherited)
            assertEquals(colored.iconButtonPlayer, glyph)
            assertEquals(.4f, neutralGlyph.alpha, .005f)
            assertTrue(isNeutralForeground(neutralGlyph))
            assertTrue(neutralGlyph.red <= .081f)
        }
    }

    @Test fun `real liquid button text uses readable artwork roles while surrounding custom light theme stays intact`() {
        val source = mutableStateOf(GlassTintSource.Artwork)
        val forceDark = mutableStateOf(true)
        val custom = DefaultLightColorPalette.copy(accent = Color(0xFFFFF59D), onAccent = Color.Black)
        var explicitText = Color.Unspecified
        var implicitText = Color.Unspecified
        var outsideText = Color.Unspecified
        var buttonAccent = Color.Unspecified
        var accentActionText = Color.Unspecified
        var pane = Color.Unspecified
        compose.setContent {
            val host = remember { GlassMenuHostState() }
            host.artworkTint = GlassArtworkTint(Color(0xFF242424), darkness = if (forceDark.value) 1f else 0f)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(custom,
                    typographyOf(custom.text, true, false, FontType.Rubik),
                    RoundedCornerShape(12.dp), RoundedCornerShape(12.dp), RoundedCornerShape(12.dp)),
                LocalGlassMenuHost provides host,
                LocalContentColor provides Color.Black,
            ) {
                Column {
                    LiquidButton(onClick = {}, backdrop = null,
                        settings = GlassSettings(enabled = true, source = source.value)) {
                        buttonAccent = colorPalette().accent
                        pane = glassSurfaceTint(GlassSettings(enabled = true, source = source.value))
                        Column {
                            Text("Explicit palette foreground", color = colorPalette().text,
                                onTextLayout = { explicitText = it.layoutInput.style.color })
                            Text("Inherited foreground", onTextLayout = { implicitText = it.layoutInput.style.color })
                            Button(onClick = {}, colors = ButtonDefaults.buttonColors(
                                containerColor = colorPalette().accent,
                                contentColor = colorPalette().onAccent,
                            )) {
                                Text("Accent action", onTextLayout = { accentActionText = it.layoutInput.style.color })
                            }
                        }
                    }
                    Text("Unaffected outside", color = colorPalette().text,
                        onTextLayout = { outsideText = it.layoutInput.style.color })
                }
            }
        }
        compose.runOnIdle {
            assertEquals(explicitText, implicitText)
            assertTrue(isNeutralForeground(explicitText))
            assertEquals(Color.White, explicitText)
            assertTrue(contrastAgainst(explicitText, pane) >= IconContrast)
            assertEquals(custom.text, outsideText)
            assertEquals(custom.accent, buttonAccent)
            assertEquals(Color.Black, accentActionText)
            source.value = GlassTintSource.Custom
        }
        compose.runOnIdle {
            assertEquals(custom.text, explicitText)
            assertEquals(Color.Black, implicitText)
            source.value = GlassTintSource.Artwork
            forceDark.value = false
        }
        compose.runOnIdle {
            assertEquals(custom.text, explicitText)
            assertEquals(Color.Black, implicitText)
        }
    }
}
