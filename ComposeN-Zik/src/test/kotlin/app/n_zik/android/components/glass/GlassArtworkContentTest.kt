package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.colorPalette
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

    @Test fun `real liquid button text uses readable artwork roles while surrounding custom light theme stays intact`() {
        val source = mutableStateOf(GlassTintSource.Artwork)
        val forceDark = mutableStateOf(true)
        val custom = DefaultLightColorPalette.copy(accent = Color(0xFFFFF59D), onAccent = Color.Black)
        var explicitText = Color.Unspecified
        var implicitText = Color.Unspecified
        var outsideText = Color.Unspecified
        var buttonAccent = Color.Unspecified
        var accentActionText = Color.Unspecified
        compose.setContent {
            val host = remember { GlassMenuHostState() }
            host.artworkColor = Color(0xFF242424)
            host.artworkForceDark = forceDark.value
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
            assertEquals(Color.White, explicitText)
            assertEquals(Color.White, implicitText)
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
