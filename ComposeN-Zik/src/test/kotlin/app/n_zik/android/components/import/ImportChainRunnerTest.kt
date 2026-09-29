package app.n_zik.android.components.import

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rescue.RescueFiles
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
// runBlocking (not a test dispatcher): the chain hops to the real NzikDispatchers
// pools, which the tests deliberately keep real — only the import entry points are
// stubbed, and a virtual clock would fight the withContext hops.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Tests the control flow of the deferred per-profile import chain
 * ([ImportChainRunner.runChain], the "Import into which profile?" flow):
 * a database or settings failure stops the chain before anything else is written —
 * the host stays open for a retry ([onCompleted] never reached), the restart prompt
 * only appears when the live database was closed by the failed replacement; a
 * profile-state failure stops the chain the same way; on success into the active
 * profile the login state is reset and the restart prompt appears, on success into
 * any other profile the host closes with a confirmation toast, and a target that
 * only exists in the picked files is registered in the profile list so its data
 * is not orphaned.
 */
class ImportChainRunnerTest {

    private lateinit var files: File
    private lateinit var context: Context
    private lateinit var profilePrefs: SharedPreferences
    private lateinit var plainPrefs: SharedPreferences
    private lateinit var plainEditor: SharedPreferences.Editor
    private lateinit var onCompleted: () -> Unit

    @BeforeEach
    fun setup() {
        // The chain hops to NzikDispatchers.UI (= Dispatchers.Main): give it an
        // unconfined test dispatcher so the hops run inline on the test thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        files = Files.createTempDirectory("nzik-import-chain").toFile()
        context = mockk()
        every { context.filesDir } returns files
        // The profile store: the base profile is active, no display names.
        profilePrefs = mockk()
        every { profilePrefs.getString(any(), any()) } answers { secondArg<String?>() }
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns profilePrefs
        plainPrefs = mockk()
        plainEditor = mockk(relaxed = true)
        every { plainPrefs.edit() } returns plainEditor
        every { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) } returns plainPrefs

        // The import entry points are stubbed: only the chain's control flow is under test.
        mockkObject(ImportDatabase.Companion)
        mockkObject(ImportSettings.Companion)
        mockkObject(RescueFiles)
        mockkObject(Database)
        every { Database.isClosed } returns false
        mockkObject(Toaster)
        every { Toaster.e(any<String>()) } just Runs
        every { Toaster.i(any<String>()) } just Runs

        onCompleted = mockk(relaxed = true)
        MainApplication.cookieStatus = MainApplication.CookieStatus.NOT_LOGGED_IN
        RestartAppDialog.isActive = false
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun mockUri(): Uri = mockk()

    @Test
    fun aDatabaseFailureStopsTheChainBeforeAnythingElseIsWritten() = runBlocking {
        val dbUri = mockUri()
        val stateUri = mockUri()
        coEvery { ImportDatabase.importTo(context, dbUri, "default") } throws IOException("unreadable file")
        every { RescueFiles.importProfileState(context, stateUri) } answers {
            throw AssertionError("the state import must not run after a database failure")
        }

        ImportChainRunner.runChain(context, "default", dbUri, null, stateUri, onCompleted)

        // The host stays open for a retry, and nothing else was written.
        verify(exactly = 0) { onCompleted() }
        coVerify(exactly = 0) { ImportSettings.importFile(any(), any(), any()) }
        verify { Toaster.e("Import failed: unreadable file") }
        assertFalse(RestartAppDialog.isActive)
    }

    @Test
    fun aSettingsFailureStopsTheChainBeforeAnythingElseIsWritten() = runBlocking {
        val dbUri = mockUri()
        val settingsUri = mockUri()
        val stateUri = mockUri()
        coEvery { ImportDatabase.importTo(context, dbUri, "default") } just Runs
        coEvery { ImportSettings.importFile(context, settingsUri, "default") } throws IOException("bad csv")
        every { RescueFiles.importProfileState(context, stateUri) } answers {
            throw AssertionError("the state import must not run after a settings failure")
        }

        ImportChainRunner.runChain(context, "default", dbUri, settingsUri, stateUri, onCompleted)

        verify(exactly = 0) { onCompleted() }
        verify { Toaster.e("Import failed: bad csv") }
        assertFalse(RestartAppDialog.isActive)
    }

    @Test
    fun aDatabaseFailureThatClosedTheLiveDatabaseShowsTheRestartPrompt() = runBlocking {
        val dbUri = mockUri()
        // The failure happens after the live database was closed (the real importTo
        // checkpoints and closes it before replacing the file).
        every { Database.isClosed } returns true
        coEvery { ImportDatabase.importTo(context, dbUri, "default") } throws IOException("unreadable file")

        ImportChainRunner.runChain(context, "default", dbUri, null, null, onCompleted)

        verify(exactly = 0) { onCompleted() }
        assertTrue(RestartAppDialog.isActive)
    }

    @Test
    fun aProfileStateFailureKeepsTheHostOpenForRetry() = runBlocking {
        val dbUri = mockUri()
        val stateUri = mockUri()
        coEvery { ImportDatabase.importTo(context, dbUri, "default") } just Runs
        every { RescueFiles.importProfileState(context, stateUri) } returns Result.failure(Exception("bad archive"))
        every { context.getString(R.string.import_profile_state_failed) } returns "Import failed"

        ImportChainRunner.runChain(context, "default", dbUri, null, stateUri, onCompleted)

        verify(exactly = 0) { onCompleted() }
        // The active target's live database was replaced: the restart prompt still applies.
        assertTrue(RestartAppDialog.isActive)
        verify { Toaster.e("Import failed") }
    }

    @Test
    fun aSuccessfulImportIntoAnInactiveProfileClosesTheHostWithoutARestart() = runBlocking {
        val dbUri = mockUri()
        val settingsUri = mockUri()
        coEvery { ImportDatabase.importTo(context, dbUri, "work") } just Runs
        coEvery { ImportSettings.importFile(context, settingsUri, "work") } just Runs
        every { context.getString(R.string.profile_base_name) } returns "N-Zik Fan"
        every { context.getString(R.string.import_completed_profile, "work") } returns "Imported into work"

        ImportChainRunner.runChain(context, "work", dbUri, settingsUri, null, onCompleted)

        verify { onCompleted() }
        assertFalse(RestartAppDialog.isActive)
        verify { Toaster.i("Imported into work") }
        // The target only existed in the picked files: it is registered in the profile
        // list, so the data written into it is not orphaned.
        val namesFile = File(files, PROFILE_NAMES_FILE_NAME)
        assertTrue(namesFile.exists())
        assertTrue(namesFile.readText().lineSequence().any { it.trim() == "work" })
    }

    @Test
    fun anImportIntoAnExistingProfileDoesNotDuplicateItsListLine() = runBlocking {
        val dbUri = mockUri()
        // "work" already exists locally (an old v1 line): the chain must not add it again.
        File(files, PROFILE_NAMES_FILE_NAME).writeText("work\n")
        coEvery { ImportDatabase.importTo(context, dbUri, "work") } just Runs
        every { context.getString(R.string.profile_base_name) } returns "N-Zik Fan"
        every { context.getString(R.string.import_completed_profile, "work") } returns "Imported into work"

        ImportChainRunner.runChain(context, "work", dbUri, null, null, onCompleted)

        verify { onCompleted() }
        val namesFile = File(files, PROFILE_NAMES_FILE_NAME)
        assertEquals(1, namesFile.readText().lineSequence().count { it.trim() == "work" })
    }

    @Test
    fun aSuccessfulDatabaseImportIntoTheActiveProfileResetsTheLoginState() = runBlocking {
        val dbUri = mockUri()
        coEvery { ImportDatabase.importTo(context, dbUri, "default") } just Runs
        MainApplication.cookieStatus = MainApplication.CookieStatus.VALID

        ImportChainRunner.runChain(context, "default", dbUri, null, null, onCompleted)

        // The replaced live database means the login state is stale: fresh start.
        assertEquals(MainApplication.CookieStatus.NOT_LOGGED_IN, MainApplication.cookieStatus)
        verify { plainEditor.remove("ytCookieExpired") }
        verify { onCompleted() }
        assertTrue(RestartAppDialog.isActive)
    }
}
