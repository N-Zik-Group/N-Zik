package app.n_zik.android.components.dialog.playlist

import java.io.File
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.appContext
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.image.PosixAtomicFileShadow
import app.n_zik.android.components.image.PreparedImage
import app.n_zik.android.core.coil.ImageCacheFactory
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [PosixAtomicFileShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChangePlaylistCoverDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setup() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(ImageCacheFactory, PickedImageEditor)
        every { ImageCacheFactory.clearCacheForKey(any(), any(), any()) } just Runs
    }

    @After
    fun cleanup() {
        unmockkObject(ImageCacheFactory, PickedImageEditor)
        unmockkStatic("app.n_zik.android.GlobalVarsKt")
    }

    @Test
    fun `confirmation publishes the permanent thumbnail URI to the open playlist`() {
        val target = File(context.filesDir, "thumbnail/playlist_42")
        val permanentUri = Uri.fromFile(target)
        val initialThumbnail = target.toURI().toString()
        val draft = File(context.cacheDir, "playlist-cover-draft.png")
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.MAGENTA)
            draft.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        val staged = mockk<PreparedImage>(relaxed = true)
        val stagedUri = Uri.fromFile(draft)
        every { staged.uri } returns stagedUri
        coEvery { PickedImageEditor.edit(any(), Uri.parse(initialThumbnail)) } returns staged
        val thumbnailUrl = mutableStateOf(initialThumbnail)
        lateinit var dialog: ChangePlaylistCoverDialog
        compose.setContent {
            dialog = ChangePlaylistCoverDialog(
                getPlaylistId = { 42L },
                getThumbnail = { thumbnailUrl.value },
                setThumbnail = { thumbnailUrl.value = it },
            )
        }
        compose.runOnIdle {
            dialog.showDialog()
            dialog.editCurrentCover()
        }
        compose.waitUntil(timeoutMillis = 5_000) { dialog.value.text == stagedUri.toString() }
        compose.runOnIdle {
            assertEquals(initialThumbnail, thumbnailUrl.value)
            dialog.onSet(dialog.value.text)
        }
        compose.waitUntil(timeoutMillis = 5_000) { !dialog.isActive }
        assertEquals(permanentUri.toString(), thumbnailUrl.value)
        assertTrue(target.length() > 0)
    }
}
