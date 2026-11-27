package app.n_zik.android.components.menu.player

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.LocalSongRowCachedIds
import app.n_zik.android.components.LocalSongRowDownloads
import app.n_zik.android.components.SongRowDownloads
import app.n_zik.android.components.menu.rememberListenOnMenuAction
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.preferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
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

    @OptIn(ExperimentalAnimationApi::class, ExperimentalTextApi::class)
    @Test
    fun `external playback action follows offline mode while the player menu stays open`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.preferences
        prefs.edit()
            .putBoolean(fullOfflineModeKey, false)
            .putBoolean(disableScrollingTextKey, true)
            .putString(menuStyleKey, MenuStyle.List.name)
            .commit()
        val item = MediaItem.Builder().setMediaId("menu-remote-track")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Remote track").build())
            .build()
        val menuState = MenuState().apply { display {} }
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val navController = mockk<NavController>()

        mockkStatic(::appContext)
        mockkStatic("app.n_zik.android.utils.EncryptedPreferencesKt")
        mockkObject(Database)
        try {
            every { appContext() } returns app
            every { any<Context>().encryptedPreferences } returns mockk<SharedPreferences>(relaxed = true)
            every { Database.songTable } returns mockk<SongTable> {
                every { findById(any()) } returns flowOf(null)
                every { likeState(any()) } returns flowOf(null)
            }
            every { Database.formatTable } returns mockk<FormatTable> {
                every { findBySongId(any()) } returns flowOf(null)
            }
            every { Database.artistTable } returns mockk<ArtistTable> {
                every { findBySongId(any()) } returns flowOf(emptyList())
            }
            every { Database.songAlbumMapTable } returns mockk<SongAlbumMapTable> {
                coEvery { findAlbumsOfDirect(any()) } returns emptyList()
            }

            val downloads = SongRowDownloads(mutableStateOf(emptyMap()), mutableStateOf(emptyMap()), online = true, preview = false)
            composeRule.setContent {
                val appearance = Appearance(ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape)
                CompositionLocalProvider(
                    LocalAppearance provides appearance,
                    LocalMenuState provides menuState,
                    LocalSongRowDownloads provides downloads,
                    LocalSongRowCachedIds provides emptySet(),
                ) {
                    PlayerMenu(
                        navController = navController,
                        binder = binder,
                        mediaItem = item,
                        onDismiss = menuState::hide,
                        onClosePlayer = {},
                        onShowSleepTimer = {},
                        disableScrollingText = true,
                    )
                }
            }

            val externalPlayback = composeRule.onNodeWithText(app.getString(R.string.player_listen_on_title))
            externalPlayback.assertExists()
            composeRule.runOnIdle { prefs.edit().putBoolean(fullOfflineModeKey, true).apply() }
            externalPlayback.assertDoesNotExist()
            composeRule.runOnIdle { prefs.edit().putBoolean(fullOfflineModeKey, false).apply() }
            externalPlayback.assertExists()
            composeRule.runOnIdle { assertTrue(menuState.isDisplayed) }
        } finally {
            prefs.edit().remove(fullOfflineModeKey).remove(disableScrollingTextKey).remove(menuStyleKey).commit()
            unmockkObject(Database)
            unmockkStatic("app.n_zik.android.utils.EncryptedPreferencesKt")
            unmockkStatic(::appContext)
        }
    }

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
