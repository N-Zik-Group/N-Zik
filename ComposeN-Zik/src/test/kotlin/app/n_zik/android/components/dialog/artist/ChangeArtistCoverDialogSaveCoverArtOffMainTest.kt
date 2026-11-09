package app.n_zik.android.components.dialog.artist

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import app.n_zik.android.components.image.PosixAtomicFileShadow
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, shadows = [PosixAtomicFileShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChangeArtistCoverDialogSaveCoverArtOffMainTest {
    @Test
    fun `invalid picked image preserves previous cover`() {
        val context = RuntimeEnvironment.getApplication()
        val target = File(context.filesDir, "cover-tests/cover_existing.jpg").apply { parentFile?.mkdirs(); writeText("original") }
        val invalid = File(context.cacheDir, "invalid-cover").apply { writeText("not an image") }
        val before = target.readBytes()
        assertNull(ChangeArtistCoverDialog.saveCoverArt(context, Uri.fromFile(invalid), "cover-tests", target.name))
        assertArrayEquals(before, target.readBytes())
        assertArrayEquals("not an image".toByteArray(), invalid.readBytes())
    }

    @Test
    fun `confirmed image replaces owned cover off caller thread without deleting source`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val target = File(context.filesDir, "cover-tests/cover_saved.jpg")
        val picked = File(context.cacheDir, "picked-cover.png")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }.let { bitmap ->
            picked.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val original = picked.readBytes()
        val caller = Thread.currentThread()
        val result = withContext(NzikDispatchers.DATA) {
            assertNotEquals(caller, Thread.currentThread())
            ChangeArtistCoverDialog.saveCoverArt(context, Uri.fromFile(picked), "cover-tests", target.name)
        }
        assertNotNull(result)
        assertArrayEquals(original, picked.readBytes())
    }
}
