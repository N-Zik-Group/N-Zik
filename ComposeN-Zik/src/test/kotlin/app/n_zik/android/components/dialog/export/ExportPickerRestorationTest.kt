package app.n_zik.android.components.dialog.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import android.app.Application
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.models.Song

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ExportPickerRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the document picker exports only the captured selection after leaving selection mode`() {
        val application = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://test.documents/selected.csv")
        val outputClosed = CountDownLatch(1)
        val output = object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                outputClosed.countDown()
            }
        }
        Shadows.shadowOf(application.contentResolver).registerOutputStream(uri, output)
        val selection = mutableStateOf(true)
        val checked = Song.makePlaceholder("checked")
        val unchecked = Song.makePlaceholder("unchecked")
        var browsingReads = 0
        lateinit var dialog: ExportSongsToCSVDialog
        val registry = DocumentRegistry()
        mockkStatic(::appContext)
        every { appContext() } returns application
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                    dialog = ExportSongsToCSVDialog("Songs", {
                        if (selection.value) listOf(checked)
                        else {
                            browsingReads++
                            listOf(checked, unchecked)
                        }
                    }, selectionActive = { selection.value })
                }
            }
            compose.runOnIdle {
                dialog.onShortClick()
                dialog.onSet("selected")
                selection.value = false
            }
            compose.runOnIdle { registry.complete(uri) }

            assertTrue("The selected CSV output was not closed", outputClosed.await(5, TimeUnit.SECONDS))
            val rows = output.toByteArray().inputStream().use { csvReader().readAllWithHeader(it) }
            assertEquals(listOf("checked"), rows.map { it["MediaId"] })
            assertEquals(listOf("Songs"), rows.map { it["PlaylistName"] })
            assertEquals(0, browsingReads)
        } finally {
            unmockkStatic(::appContext)
        }
    }

    @Test fun `a restored selected picker never exports the browsing collection`() {
        val application = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("restored-selection", ".csv", application.cacheDir)
        val selection = mutableStateOf(true)
        val checked = Song.makePlaceholder("checked")
        val unchecked = Song.makePlaceholder("unchecked")
        var browsingReads = 0
        lateinit var dialog: ExportSongsToCSVDialog
        val registry = DocumentRegistry()
        val restoration = StateRestorationTester(compose)
        mockkObject(Toaster)
        mockkStatic(::appContext)
        every { appContext() } returns application
        every { Toaster.e(R.string.export_selection_lost, duration = any()) } just Runs
        try {
            restoration.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                    dialog = ExportSongsToCSVDialog("Songs", {
                        if (selection.value) listOf(checked)
                        else {
                            browsingReads++
                            listOf(checked, unchecked)
                        }
                    }, selectionActive = { selection.value })
                }
            }
            compose.runOnIdle {
                dialog.onShortClick()
                dialog.onSet("selected")
                selection.value = false
            }
            restoration.emulateSavedInstanceStateRestore()
            compose.runOnIdle { registry.complete(Uri.fromFile(file)) }

            verify(timeout = 5_000) { Toaster.e(R.string.export_selection_lost, duration = any()) }
            assertEquals(0, browsingReads)
            assertEquals(0L, file.length())
        } finally {
            file.delete()
            unmockkObject(Toaster)
            unmockkStatic(::appContext)
        }
    }

    private class DocumentRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        private var requestCode: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            this.requestCode = requestCode
        }

        fun complete(uri: Uri) {
            assertTrue(dispatchResult(requireNotNull(requestCode), uri))
        }
    }
}
