package app.n_zik.android.components.menu

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "en")
class ArtistMoreFromTitleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `list and grid display cleaned names while clicks retain the original artist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefix = context.getString(R.string.artist_more_from)
        val artist = mutableStateOf(Artist(id = "UC-original", name = "Initial"))
        val grid = mutableStateOf(false)
        var selected: Artist? = null
        compose.setContent {
            val target = artist.value
            val item = object : MenuIcon {
                override val iconId = R.drawable.people
                override val modifier = Modifier.width(240.dp)
                override val menuIconTitle: String
                    @Composable get() = artistMoreFromTitle(target.name)
                override fun onShortClick() { selected = target }
            }
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = ColorPalette.fallback,
                typography = Typography.fallback,
                thumbnailShape = shape,
                uiRoundnessShape = shape,
                artistThumbnailShape = shape,
            )) {
                if (grid.value) item.GridMenuItem() else item.ListMenuItem()
            }
        }
        val cases = listOf(
            "${MODIFIED_PREFIX}Tangerl" to "Tangerl",
            "$MODIFIED_PREFIX${EXPLICIT_PREFIX}Artist: Live" to "Artist: Live",
            "Artist: Live" to "Artist: Live",
            null to "",
        )
        for (isGrid in listOf(false, true)) for ((rawName, displayName) in cases) {
            val target = Artist(id = "UC-original", name = rawName)
            compose.runOnIdle {
                artist.value = target
                grid.value = isGrid
                selected = null
            }
            compose.onNodeWithText("$prefix $displayName").assertIsDisplayed().performClick()
            if (rawName != null && rawName != displayName) {
                compose.onNodeWithText("$prefix $rawName").assertDoesNotExist()
            }
            compose.runOnIdle {
                assertSame(target, selected)
                assertEquals(rawName, target.name)
                assertEquals("UC-original", selected?.id)
            }
        }
    }
}
