package app.n_zik.android.components.image

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.CoverEditTarget
import app.n_zik.android.components.dialog.common.StagedCoverDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverImageExportPickerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val finished = CountDownLatch(1)

    @Before
    fun mockToasts() {
        mockkObject(Toaster)
        every { Toaster.done() } answers { finished.countDown() }
        every { Toaster.e(R.string.cover_download_failed) } answers { finished.countDown() }
    }

    @After
    fun unmockToasts() {
        unmockkObject(Toaster)
    }

    @Test
    fun `download writes the prepared image to the SAF result after activity recreation`() {
        checkExport(failOnOpen = false)
    }

    @Test
    fun `download removes the created SAF document on a provider error`() {
        checkExport(failOnOpen = true)
    }

    private fun checkExport(failOnOpen: Boolean) {
        val activity = composeRule.activity
        val payload = imageBytes()
        val provider = DestinationProvider(
            File.createTempFile("cover-export-provider-", ".png", activity.cacheDir), failOnOpen,
        )
        provider.attachInfo(activity, ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        composeRule.setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val dialog = remember {
                object : StagedCoverDialog(
                    mutableStateOf(false), mutableStateOf(TextFieldValue()), context, scope,
                    getTarget = { CoverEditTarget("cover", null, currentBytes = payload) },
                ) {}.apply { showDialog() }
            }
            val appearance = Appearance(
                ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape,
            )
            CompositionLocalProvider(LocalAppearance provides appearance) {
                dialog.DialogBody()
            }
        }
        try {
            composeRule.onNodeWithText(activity.getString(R.string.cover_action_download)).performClick()
            val shadow = shadowOf(activity)
            composeRule.waitUntil(10_000) { shadow.peekNextStartedActivityForResult() != null }
            val outerIntent = checkNotNull(shadow.nextStartedActivityForResult?.intent)
            val controller = Robolectric.buildActivity(CoverImageExportActivity::class.java, outerIntent).setup()
            try {
                val launched = checkNotNull(shadowOf(controller.get()).nextStartedActivityForResult)
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, launched.intent.action)
                assertEquals("image/png", launched.intent.type)
                assertEquals("cover.png", launched.intent.getStringExtra(Intent.EXTRA_TITLE))

                controller.recreate()
                assertNull(shadowOf(controller.get()).peekNextStartedActivityForResult())
                assertTrue(controller.get().activityResultRegistry.dispatchResult(
                    launched.requestCode, Activity.RESULT_OK, Intent().setData(provider.uri),
                ))
                assertTrue(finished.await(10, TimeUnit.SECONDS))
                if (failOnOpen) {
                    assertTrue(provider.deleted)
                } else {
                    assertArrayEquals(payload, provider.file.readBytes())
                }
            } finally {
                controller.pause().stop().destroy()
            }
        } finally {
            provider.file.delete()
        }
    }

    private fun imageBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        } finally {
            bitmap.recycle()
        }
        output.toByteArray()
    }

    private class DestinationProvider(val file: File, private val failOnOpen: Boolean) : ContentProvider() {
        val uri: Uri = Uri.parse("content://$AUTHORITY/document/cover")
        var deleted = false

        override fun onCreate() = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (failOnOpen) throw FileNotFoundException("Simulated provider failure")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            if (method != "android:deleteDocument") return super.call(method, arg, extras)
            deleted = file.delete()
            return Bundle()
        }

        override fun getType(uri: Uri) = "image/png"
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
        const val AUTHORITY = "nzik.cover.export.test"
    }
}
