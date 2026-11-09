package app.n_zik.android.components.menu.song

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SongMenuSnapshotTest {
    @get:Rule val compose = createComposeRule()
    private val entry = Song("T-5rIvXMiUA", "modified:Custom", "modified:Tangerl", "3:00", "modified:cover")

    @After fun cleanup() = unmockkAll()

    @Test
    fun `open header and retained action observe the committed reset without reopening`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = SongMenuSnapshot(entry, getActiveProfile(context))
        val rows = MutableStateFlow<Song?>(entry)
        val collected = AtomicReference<Song?>()
        val table = mockk<SongTable>()
        mockkObject(Database)
        every { Database.songTable } returns table
        every { table.findById(entry.id) } returns rows.onEach(collected::set)
        var retainedAction: () -> Song = { entry }
        compose.setContent {
            ObserveSongMenuSnapshot(snapshot) { error("Unexpected profile change") }
            retainedAction = remember { { snapshot.song } }
            BasicText(snapshot.song.title + "|" + snapshot.song.artistsText + "|" + snapshot.song.thumbnailUrl)
        }
        compose.waitUntil { rows.subscriptionCount.value == 1 }
        val reset = entry.copy(title = "! PARTY SIRENS !", artistsText = "Tanger", thumbnailUrl = "https://provider/cover")
        compose.runOnIdle { rows.value = reset }
        compose.waitUntil { collected.get() == reset }
        compose.waitUntil {
            pumpUi()
            snapshot.song == reset
        }
        compose.onNodeWithText("! PARTY SIRENS !|Tanger|https://provider/cover").assertIsDisplayed()
        compose.runOnIdle { assertEquals(reset, retainedAction()) }
    }

    @Test
    fun `rows from another target or profile cannot replace retained menu data`() {
        val snapshot = SongMenuSnapshot(entry, "owner")
        snapshot.accept(entry.copy(title = "Wrong profile"), "other")
        snapshot.accept(entry.copy(id = "other_00001", title = "Wrong song"), "owner")
        snapshot.accept(null, "owner")
        assertEquals(entry, snapshot.song)
        val reset = entry.copy(title = "Original")
        snapshot.accept(reset, "owner")
        assertEquals(reset, snapshot.song)
    }

    private fun pumpUi() {
        // DATA snapshot notifications also need Robolectric's looper, independently of Compose frames.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

}
