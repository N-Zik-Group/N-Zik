package app.n_zik.android.components.image

import java.io.ByteArrayOutputStream
import java.io.File
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverImageExportTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `export writes the snapshot bytes with their original format`() {
        // JUnit waits for the export's DATA dispatcher work.
        runBlocking {
            val bytes = ByteArrayOutputStream().use { output ->
                val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.BLUE)
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                } finally {
                    bitmap.recycle()
                }
                output.toByteArray()
            }
            val source = File(context.cacheDir, "original.jpg").apply { writeBytes(bytes) }
            val destination = File(context.cacheDir, "chosen.png")
            try {
                CoverImageExport.prepare(context, Uri.fromFile(source)).use { export ->
                    assertEquals("image/png", export.mimeType)
                    assertEquals("cover.png", export.suggestedName)
                    source.delete()
                    export.writeTo(context, Uri.fromFile(destination))
                    assertArrayEquals(bytes, destination.readBytes())
                }
            } finally {
                source.delete()
                destination.delete()
            }
        }
    }
}
