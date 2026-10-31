package app.n_zik.android.components.playlist

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.components.SwipeableContent
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.R
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.editStoredPlaylistMembership
import app.n_zik.android.core.database.canEditStoredPlaylistMembership
import app.n_zik.android.utils.playlist.PlaylistRefreshPreferences
import app.n_zik.android.utils.playlist.importSongsToPlaylist
import app.it.fast4x.rimusic.models.Song
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
// JUnit entrypoints synchronously bridge Room's suspend transaction API.
@androidx.media3.common.util.UnstableApi
class PlaylistSourceRefreshTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DatabaseInitializer

    @Before fun setup() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        context.preferences.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries().build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After fun cleanup() {
        context.preferences.edit().clear().commit()
        unmockkAll()
        db.close()
    }

    private fun media(id: String) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(id).build()).build()

    private fun saved(id: String = "PLsource", editable: Boolean = false): Playlist {
        val row = Playlist(name = "Saved", browseId = id, isEditable = editable)
        return row.copy(id = db.playlistTable.insert(row))
    }

    private fun ids(playlist: Playlist) = db.songPlaylistMapTable.songsByPositionDirect(playlist.id).map { it.id }

    @Test fun `complete source updates membership and retains local survivor order`() = runBlocking {
        val row = saved()
        val other = saved("PLother")
        Database.mapIgnore(row, media("b"), media("a"), media("removed"))
        Database.mapIgnore(other, media("removed"))
        assertTrue(refreshPlaylistSource(row) { listOf(media("a"), media("new"), media("b")) })
        assertEquals(listOf("b", "a", "new"), ids(row))
        assertEquals(listOf("removed"), ids(other))
        assertEquals(row, db.playlistTable.getAll().first { it.id == row.id })
    }

    @Test fun `null empty and failed source preserve snapshot and source race refuses replacement`() = runBlocking {
        val row = saved("MPSPopaque")
        Database.mapIgnore(row, media("old"))
        assertFalse(refreshPlaylistSource(row) { null })
        assertFalse(refreshPlaylistSource(row) { emptyList() })
        try {
            refreshPlaylistSource(row) { error("network failed") }
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("old"), ids(row))
        assertFalse(refreshPlaylistSource(row) { exactId ->
            assertEquals("MPSPopaque", exactId)
            db.playlistTable.update(row.copy(browseId = "MPSPchanged"))
            listOf(media("new"))
        })
        assertEquals(listOf("old"), ids(row))
    }

    @Test fun `stored membership guard rejects stale editable target while direct order remains mutable`() {
        val editable = saved(editable = true)
        db.playlistTable.update(editable.copy(isEditable = false))
        assertFalse(canEditStoredPlaylistMembership(editable.id))
        var rejected = false
        try {
            importSongsToPlaylist(editable, listOf(Song(id = "blocked", title = "Blocked", durationText = null, thumbnailUrl = null)))
        } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertTrue(ids(editable).isEmpty())
        Database.mapIgnore(editable, media("a"), media("b"))
        assertFalse(editStoredPlaylistMembership(editable.id) {
            songPlaylistMapTable.deleteBySongId("a", editable.id)
            songPlaylistMapTable.map("blocked", editable.id)
        })
        assertEquals(listOf("a", "b"), ids(editable))
        db.songPlaylistMapTable.updatePosition(editable.id, "b", -1)
        assertEquals(listOf("b", "a"), ids(editable))
    }

    @Test fun `preference defaults and overrides are per playlist and independent of account sync`() {
        val readonly = saved()
        val editable = saved("PLeditable", editable = true)
        val prefs = PlaylistRefreshPreferences(context)
        assertTrue(prefs.get(readonly))
        assertFalse(prefs.get(editable))
        prefs.set(readonly.id, false)
        prefs.set(editable.id, true)
        assertFalse(prefs.get(readonly))
        assertTrue(prefs.get(editable))
        assertFalse(readonly.isAutoSync)
        assertFalse(editable.isAutoSync)
    }

    @Test fun `opening waits for row refreshes once and reopening respects persisted switch`() {
        val row = saved()
        val loaded = mutableStateOf<Playlist?>(null)
        val visible = mutableStateOf(true)
        var requests = 0
        compose.setContent {
            if (visible.value) RefreshPlaylistOnOpening(row.id, loaded.value, offline = false) { requests++ }
        }
        compose.runOnIdle { assertEquals(0, requests); loaded.value = row }
        compose.runOnIdle { assertEquals(1, requests); loaded.value = row.copy(name = "Changed") }
        compose.runOnIdle { assertEquals(1, requests); PlaylistRefreshPreferences(context).set(row.id, false) }
        compose.runOnIdle { assertEquals(1, requests); visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.runOnIdle { assertEquals(1, requests); visible.value = false }
        compose.runOnIdle { PlaylistRefreshPreferences(context).set(row.id, true); visible.value = true }
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun `toolbar action reads live state and keeps the menu open`() {
        val row = saved()
        var action: PlaylistRefreshOnOpenAction? = null
        var retained: PlaylistRefreshOnOpenAction? = null
        var color: Color? = null
        var title: String? = null
        val menu = MenuState()
        val appearance = appearance()
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalMenuState provides menu) {
                action = playlistRefreshOnOpenAction(row)
                color = requireNotNull(action).color
                title = requireNotNull(action).menuIconTitle
            }
        }
        compose.runOnIdle {
            retained = action
            assertTrue(requireNotNull(action).enabled)
            assertEquals(R.drawable.sync_off, requireNotNull(action).iconId)
            assertEquals(appearance.colorPalette.accent, color)
            assertEquals(context.getString(R.string.playlist_refresh_on_open_disable), title)
            menu.display { }
            requireNotNull(action).onShortClick()
            assertTrue(menu.isDisplayed)
        }
        compose.runOnIdle {
            assertSame(retained, action)
            assertFalse(requireNotNull(retained).enabled)
            assertEquals(R.drawable.sync, requireNotNull(retained).iconId)
            assertEquals(appearance.colorPalette.text, color)
            assertEquals(context.getString(R.string.playlist_refresh_on_open_enable), title)
            assertFalse(PlaylistRefreshPreferences(context).get(row))
            requireNotNull(retained).onShortClick()
            requireNotNull(retained).onShortClick()
            assertFalse(requireNotNull(retained).enabled)
            assertFalse(PlaylistRefreshPreferences(context).get(row))
            assertFalse(context.preferences.getBoolean("playlist_refresh_on_open_999", false))
        }
        compose.runOnIdle { PlaylistRefreshPreferences(context).set(row.id, true) }
        compose.runOnIdle {
            assertSame(retained, action)
            assertTrue(requireNotNull(retained).enabled)
            assertEquals(R.drawable.sync_off, requireNotNull(retained).iconId)
            assertEquals(appearance.colorPalette.accent, color)
        }
    }


    @Test fun `no action swipe direction never invokes callback while opposite configured action works`() {
        var removed = 0
        var played = 0
        val appearance = appearance()
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SwipeableContent(
                    swipeToLeftIcon = null,
                    swipeToRightIcon = R.drawable.play_skip_forward,
                    onSwipeToLeft = { removed++ },
                    onSwipeToRight = { played++ },
                    modifier = Modifier.testTag("swipe").size(300.dp, 64.dp),
                ) { Box(Modifier.size(300.dp, 64.dp)) }
            }
        }
        compose.onNodeWithTag("swipe").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, removed); assertEquals(0, played) }
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0, removed); assertEquals(1, played) }
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0, removed); assertEquals(2, played) }
        compose.onNodeWithTag("swipe").performTouchInput {
            down(center)
            moveTo(center + Offset(10f, 0f))
            cancel()
        }
        compose.onNodeWithTag("swipe").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, removed); assertEquals(2, played) }
        compose.onNodeWithTag("swipe").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(0, removed); assertEquals(3, played) }
    }

    @Test fun `radio keeps live binder state exact start action and menu dismissal with the shared base`() {
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val active = mutableStateOf(false)
        every { binder.isRadioActive } answers { active.value }
        every { binder.radioActionTextRes } answers {
            if (active.value) R.string.stop_radio else R.string.start_radio
        }
        val song = Song(id = "radio-song", title = "Radio song", durationText = null, thumbnailUrl = null)
        val menu = MenuState()
        var radio: Radio? = null
        var color: Color? = null
        var title: String? = null
        val appearance = appearance()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalMenuState provides menu,
                LocalPlayerServiceBinder provides binder,
            ) {
                radio = Radio { listOf(song) }
                color = requireNotNull(radio).color
                title = requireNotNull(radio).menuIconTitle
            }
        }
        compose.runOnIdle {
            assertEquals(R.drawable.radio, requireNotNull(radio).iconId)
            assertEquals(appearance.colorPalette.text, color)
            assertEquals(context.getString(R.string.start_radio), title)
            active.value = true
        }
        compose.runOnIdle {
            assertEquals(R.drawable.radio_stop, requireNotNull(radio).iconId)
            assertEquals(appearance.colorPalette.accent, color)
            assertEquals(context.getString(R.string.stop_radio), title)
            menu.display { }
            requireNotNull(radio).onShortClick()
            assertFalse(menu.isDisplayed)
            verify(exactly = 1) { binder.startRadio(song, false, null, true) }
        }
    }

    private fun appearance(): Appearance {
        val shape = RoundedCornerShape(20.dp)
        return Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape,
            uiRoundnessShape = shape,
            artistThumbnailShape = shape,
        )
    }

}
