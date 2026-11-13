package app.n_zik.android.components.dialog.song

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.models.Song
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.dialog.common.CheckboxDialog
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UpdateSongDialogConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private val selection = slot<SongUpdateSelection>()
    private val song = Song("abcdefghijk", "modified:Custom", "modified:Artist", "3:00", "modified:cover")
    private val appearance = Appearance(
        DefaultDarkColorPalette, typographyOf(Color.White, true, false, FontType.Rubik),
        CircleShape, CircleShape, CircleShape,
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkStatic("app.n_zik.android.utils.ProfilePreferencesKt")
        every { getActiveProfile(any()) } returns "active"
        mockkStatic("app.n_zik.android.components.dialog.song.UpdateSongDialogKt")
        coEvery { runSongUpdate(any(), any(), capture(selection), any()) } returns Unit
        mockkObject(NzikDispatchers)
        every { NzikDispatchers.fireAndForget(any<kotlinx.coroutines.CoroutineDispatcher>()) } returns
            CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        CheckboxDialog.Item.SELECT_ALL.selected = false
    }

    @After
    fun teardown() {
        CheckboxDialog.Item.SELECT_ALL.selected = false
        unmockkAll()
    }

    @Test
    fun `same song database emissions retain active checkboxes and Apply receives latest row`() {
        val currentSong = mutableStateOf(song)
        lateinit var dialog: UpdateSongDialog
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                dialog = UpdateSongDialog(currentSong.value)
                Column { dialog.DialogBody(); dialog.Buttons() }
            }
        }
        val retained = dialog
        compose.runOnIdle { retained.showDialog() }
        click(R.string.metadata_action_refresh_title_from_youtube)
        val latest = song.copy(title = "Committed title", totalPlayTimeMs = 12_000L)
        compose.runOnIdle { currentSong.value = latest }
        compose.runOnIdle {
            assertSame(retained, dialog)
            assertTrue(dialog.isActive)
            assertTrue(dialog.items.single { it.id == "title" }.selected)
            assertEquals(latest, dialog.song.get())
        }
        click(R.string.common_action_apply)
        coVerify(exactly = 1) { runSongUpdate(any(), latest, any(), "active") }
        assertEquals(SongUpdateSelection(title = true), selection.captured)
    }

    @Test
    fun `real select all and Apply dispatch all reset options`() {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val dialog = UpdateSongDialog(song)
                Column { dialog.DialogBody(); dialog.Buttons() }
            }
        }
        click(R.string.common_option_all)
        compose.waitForIdle()
        click(R.string.common_action_apply)
        assertEquals(SongUpdateSelection(true, true, true, true, true, true, true), selection.captured)
    }

    @Test
    fun `favorite reset starts unchecked and Apply dispatches only the explicit choice`() {
        lateinit var dialog: UpdateSongDialog
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                dialog = UpdateSongDialog(song)
                Column { dialog.DialogBody(); dialog.Buttons() }
            }
        }
        compose.runOnIdle {
            assertTrue(dialog.items.none { it.selected })
        }
        click(R.string.song_like_status_action_reset)
        click(R.string.common_action_apply)
        assertEquals(SongUpdateSelection(favorite = true), selection.captured)
    }

    @Test
    fun `offline download removal is independent from playback cache`() {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val dialog = UpdateSongDialog(song)
                Column { dialog.DialogBody(); dialog.Buttons() }
            }
        }
        click(R.string.song_update_action_remove_offline_download)
        click(R.string.common_action_apply)
        assertEquals(SongUpdateSelection(offlineDownload = true), selection.captured)
    }

    private fun click(resource: Int) {
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<Application>().getString(resource)).performClick()
    }
}
