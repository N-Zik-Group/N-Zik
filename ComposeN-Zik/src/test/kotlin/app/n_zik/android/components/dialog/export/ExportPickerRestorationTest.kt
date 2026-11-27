package app.n_zik.android.components.dialog.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import android.app.Application
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

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
        val outputClosed = AtomicBoolean()
        val notifiedAfterClose = AtomicBoolean()
        val successDelivered = CountDownLatch(1)
        val output = object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                outputClosed.set(true)
            }
        }
        Shadows.shadowOf(application.contentResolver).registerOutputStream(uri, output)
        val selection = mutableStateOf(true)
        val checked = Song.makePlaceholder("checked")
        val unchecked = Song.makePlaceholder("unchecked")
        var browsingReads = 0
        lateinit var dialog: ExportSongsToCSVDialog
        val registry = DocumentRegistry()
        mockkObject(Toaster)
        mockkStatic(::appContext)
        every { appContext() } returns application
        every { Toaster.done() } answers {
            notifiedAfterClose.set(outputClosed.get())
            successDelivered.countDown()
        }
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

            assertTrue("The CSV export did not report success", successDelivered.await(5, TimeUnit.SECONDS))
            assertTrue("Success was reported before closing the CSV", notifiedAfterClose.get())
            val rows = output.toByteArray().inputStream().use { csvReader().readAllWithHeader(it) }
            assertEquals(listOf("checked"), rows.map { it["MediaId"] })
            assertEquals(listOf("Songs"), rows.map { it["PlaylistName"] })
            assertEquals(0, browsingReads)
        } finally {
            unmockkStatic(::appContext)
            unmockkObject(Toaster)
        }
    }

    @Test fun `a document provider without an output stream reports an export failure`() =
        assertExportFailure(output = null)

    @Test fun `an output stream that fails to write reports an export failure`() =
        assertExportFailure(object : OutputStream() {
            override fun write(value: Int) = throw IOException("Cannot write test document")
        })

    @Test fun `an output stream that fails to close reports an export failure`() =
        assertExportFailure(object : ByteArrayOutputStream() {
            override fun close() = throw IOException("Cannot close test document")
        })

    @Test fun `a failed browsing load removes its empty export document`() =
        assertFailedBrowsingExport(contents = "")

    @Test fun `a failed browsing load leaves a nonempty export document alone`() =
        assertFailedBrowsingExport(contents = "Existing provider data")

    private fun assertFailedBrowsingExport(contents: String) {
        val application = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("failed-browsing-export", ".csv", application.cacheDir)
        file.writeText(contents)
        val provider = BrowsingExportProvider(file)
        provider.attachInfo(application, ProviderInfo().apply {
            authority = BROWSING_EXPORT_AUTHORITY
            exported = true
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal(BROWSING_EXPORT_AUTHORITY, provider)
        val registry = DocumentRegistry()
        lateinit var dialog: ExportSongsToCSVDialog
        mockkObject(Toaster)
        mockkStatic(::appContext)
        every { appContext() } returns application
        every { Toaster.e(R.string.common_load_failed, duration = any()) } just Runs
        every { Toaster.done() } just Runs
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                    dialog = ExportSongsToCSVDialog.deferred("Songs", capture = {
                        suspend {
                            Toaster.e(R.string.common_load_failed)
                            null
                        }
                    })
                }
            }
            compose.runOnIdle {
                dialog.onShortClick()
                dialog.onSet("browsing")
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, registry.launchedIntent?.action)
                registry.complete(provider.uri)
            }
            assertTrue("The export did not check the created document", provider.readOpened.await(5, TimeUnit.SECONDS))
            if (contents.isEmpty()) {
                assertTrue("The empty document was not removed", provider.deleted.await(5, TimeUnit.SECONDS))
                assertTrue(!file.exists())
            } else {
                assertFalse("The nonempty document was removed", provider.deleted.await(300, TimeUnit.MILLISECONDS))
                assertEquals(contents, file.readText())
            }
            verify(exactly = 1) { Toaster.e(R.string.common_load_failed, duration = any()) }
            verify(exactly = 0) { Toaster.done() }
        } finally {
            file.delete()
            unmockkStatic(::appContext)
            unmockkObject(Toaster)
        }
    }

    private fun assertExportFailure(output: OutputStream?) {
        val uri = Uri.parse("content://test.documents/failed.csv")
        val resolver = mockk<ContentResolver>()
        every { resolver.openOutputStream(uri) } returns output
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val registry = DocumentRegistry()
        lateinit var dialog: ExportSongsToCSVDialog
        mockkObject(Toaster)
        mockkStatic(::appContext)
        every { appContext() } returns context
        every { Toaster.e(R.string.common_error_title, duration = any()) } just Runs
        every { Toaster.done() } just Runs
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                    dialog = ExportSongsToCSVDialog("Songs", { listOf(Song.makePlaceholder("track")) })
                }
            }
            compose.runOnIdle {
                dialog.onShortClick()
                dialog.onSet("failed")
                registry.complete(uri)
            }
            verify(timeout = 5_000, exactly = 1) { Toaster.e(R.string.common_error_title, duration = any()) }
            verify(exactly = 0) { Toaster.done() }
        } finally {
            unmockkStatic(::appContext)
            unmockkObject(Toaster)
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
        var launchedIntent: Intent? = null
            private set

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            this.requestCode = requestCode
            launchedIntent = contract.createIntent(RuntimeEnvironment.getApplication(), input)
        }

        fun complete(uri: Uri) {
            assertTrue(dispatchResult(requireNotNull(requestCode), uri))
        }
    }

    private class BrowsingExportProvider(private val file: File) : ContentProvider() {
        val uri: Uri = Uri.parse("content://$BROWSING_EXPORT_AUTHORITY/document/playlist")
        val readOpened = CountDownLatch(1)
        val deleted = CountDownLatch(1)

        override fun onCreate() = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            require(mode == "r")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also {
                readOpened.countDown()
            }
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            if (method != "android:deleteDocument") return super.call(method, arg, extras)
            check(file.delete())
            deleted.countDown()
            return Bundle()
        }

        override fun getType(uri: Uri) = "text/csv"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null
    }

    private companion object {
        const val BROWSING_EXPORT_AUTHORITY = "nzik.playlist.export.test"
    }
}
