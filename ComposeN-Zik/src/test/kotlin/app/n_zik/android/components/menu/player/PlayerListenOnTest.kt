package app.n_zik.android.components.menu.player

import android.app.Application
import android.content.ActivityNotFoundException
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.menu.rememberListenOnMenuAction
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class PlayerListenOnTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `external players use the canonical song ID and only pause after accepting the link`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val item = MediaItem.Builder()
            .setMediaId("queue/occurrence")
            .setUri("abcdefghijk")
            .setCustomCacheKey("abcdefghijk")
            .build()
        val menuState = MenuState()
        val events = mutableListOf<String>()
        var canOpen = false
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                if (!canOpen) throw IllegalArgumentException(ActivityNotFoundException())
                events.add(uri)
            }
        }
        lateinit var action: MenuIcon
        composeRule.setContent {
            val appearance = Appearance(ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape)
            CompositionLocalProvider(LocalAppearance provides appearance, LocalUriHandler provides uriHandler) {
                action = rememberListenOnMenuAction(playerMenuSongId(item), menuState) { events.add("pause") }
            }
        }

        mockkObject(Toaster)
        try {
            every { Toaster.e(R.string.common_error_title) } just Runs
            composeRule.runOnIdle {
                menuState.display {}
                action.onShortClick()
            }
            composeRule.onNodeWithText(app.getString(R.string.player_action_listen_on_youtube)).performClick()
            composeRule.runOnIdle {
                assertTrue(menuState.isDisplayed)
                assertTrue(events.isEmpty())
                canOpen = true
            }
            verify(exactly = 1) { Toaster.e(R.string.common_error_title) }
            composeRule.onNodeWithText(app.getString(R.string.player_action_listen_on_youtube)).performClick()
            composeRule.runOnIdle { assertFalse(menuState.isDisplayed) }
        } finally {
            unmockkObject(Toaster)
        }

        composeRule.runOnIdle {
            menuState.display {}
            action.onShortClick()
        }
        composeRule.onNodeWithText(app.getString(R.string.player_action_listen_on_youtube_music)).performClick()
        composeRule.runOnIdle {
            assertFalse(menuState.isDisplayed)
            assertEquals(
                listOf(
                    "https://youtube.com/watch?v=abcdefghijk", "pause",
                    "https://music.youtube.com/watch?v=abcdefghijk", "pause",
                ),
                events,
            )
        }
    }
}
