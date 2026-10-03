package app.n_zik.android.playback.services.automotive.session

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber

/**
 * Issue #881 (gh-881): pins `AutoSessionCallback.onPlayerCommandRequest` (spec S3) — the
 * override must (a) never change the outcome: every player command is delegated to the default
 * `MediaSession.Callback` handling, which returns `RESULT_SUCCESS` (a non-SUCCESS return would
 * make media3 drop the command), and (b) log exactly one `SEEK_SESSION` line per seek command
 * (none for non-seek commands).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AutoSessionCallbackPlayerCommandTest {

    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback

    private val captured = mutableListOf<String>()

    private val captureTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            captured += message
        }
    }

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        context = app
        captured.clear()
        Timber.plant(captureTree)
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
        callback = AutoSessionCallback(context, Database, MyDownloadHelper)
    }

    @After
    fun tearDown() {
        Timber.uproot(captureTree)
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
    }

    /**
     * The shared [DatabaseInitializer.Instance] has no `allowMainThreadQueries()`,
     * so every DAO call must run off the main thread (same pattern as
     * `AutoSessionCallbackSelectionTest`). runBlocking is forced by the
     * synchronous test API (AGENTS.md runBlocking exception).
     */
    private suspend fun <T> onDatabase(block: suspend () -> T): T = withContext(NzikDispatchers.DATA) { block() }

    @Test
    fun `seek player commands are observed and always accepted`() {
        val session = mockk<MediaLibrarySession>()
        val controller = mockk<MediaSession.ControllerInfo>()

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            callback.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
        )
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            callback.onPlayerCommandRequest(session, controller, Player.COMMAND_SEEK_TO_NEXT),
        )

        assertTrue(captured.any { it.startsWith("SEEK_SESSION op=SEEK_TO ") })
        assertTrue(captured.any { it.startsWith("SEEK_SESSION op=SEEK_NEXT ") })
    }

    @Test
    fun `non-seek player commands pass through without a SEEK_SESSION line`() {
        val session = mockk<MediaLibrarySession>()
        val controller = mockk<MediaSession.ControllerInfo>()

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            callback.onPlayerCommandRequest(session, controller, Player.COMMAND_PLAY_PAUSE),
        )

        assertTrue(captured.none { it.startsWith("SEEK_SESSION") })
    }
}
