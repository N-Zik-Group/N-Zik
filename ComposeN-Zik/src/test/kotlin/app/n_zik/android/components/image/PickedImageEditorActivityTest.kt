package app.n_zik.android.components.image

import java.io.File
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.PopupMenu
import com.yalantis.ucrop.view.UCropView
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.android.controller.ActivityController
import app.n_zik.android.utils.coroutines.NzikDispatchers
import com.yalantis.ucrop.R as CropR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PickedImageEditorActivityTest {
    @Test
    fun `real uCrop activity inflates loads isolated image and cancellation cleans only its session`() {
        // JUnit synchronously waits while explicitly draining Android's paused main looper.
        runBlocking {
            val caller = Robolectric.buildActivity(Activity::class.java).setup()
            val original = File(caller.get().cacheDir, "ucrop-smoke-original.png")
            val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
            original.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val bytes = original.readBytes()
            val result = async(NzikDispatchers.UI) { PickedImageEditor.edit(caller.get(), Uri.fromFile(original)) }
            var editor: ActivityController<PickedImageEditorActivity>? = null
            try {
                var launched: Intent? = null
                drainUntil {
                    launched = launched ?: shadowOf(caller.get()).nextStartedActivity
                    launched != null
                }
                val intent = requireNotNull(launched)
                assertEquals(PickedImageEditorActivity::class.java.name, intent.component?.className)
                val session = requireNotNull(PickedImageEditor.session(intent.getStringExtra(PickedImageEditor.SESSION_EXTRA)))
                val directory = requireNotNull(session.source.parentFile)
                assertArrayEquals(bytes, session.source.readBytes())
                editor = Robolectric.buildActivity(PickedImageEditorActivity::class.java, intent).setup().visible()
                val activity = editor.get()
                val crop = activity.findViewById<UCropView>(CropR.id.ucrop)
                assertNotNull(crop)
                drainUntil { crop.cropImageView.viewBitmap != null || activity.isFinishing }
                assertFalse(activity.isFinishing)
                assertNotNull(crop.cropImageView.viewBitmap)
                for (id in listOf(CropR.id.state_aspect_ratio, CropR.id.state_rotate, CropR.id.state_scale)) {
                    assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
                }
                val menu = PopupMenu(activity, crop).menu
                assertTrue(activity.onCreateOptionsMenu(menu))
                activity.onPrepareOptionsMenu(menu)
                assertTrue(requireNotNull(menu.findItem(CropR.id.menu_crop)).isVisible)
                assertFalse(result.isCompleted)
                activity.onBackPressedDispatcher.onBackPressed()
                drainUntil { result.isCompleted }
                assertNull(result.await())
                assertFalse(directory.exists())
                assertArrayEquals(bytes, original.readBytes())
            } finally {
                result.cancel()
                drainUntil { result.isCompleted }
                editor?.pause()?.stop()?.destroy()
                caller.pause().stop().destroy()
                original.delete()
            }
        }
    }

    @Test
    fun `applying a square crop returns the cropped pixels to the caller`() = withEditor { editor, awaitResult ->
        val activity = editor.get()
        val crop = awaitImage(activity)
        crop.cropImageView.targetAspectRatio = 1f
        crop.cropImageView.setImageToWrapCropBounds(false)
        val menu = PopupMenu(activity, crop).menu
        assertTrue(activity.onCreateOptionsMenu(menu))
        activity.onPrepareOptionsMenu(menu)
        val apply = requireNotNull(menu.findItem(CropR.id.menu_crop))
        assertTrue(apply.isVisible)
        assertTrue(activity.onOptionsItemSelected(apply))

        requireNotNull(awaitResult()).use { prepared ->
            val bitmap = requireNotNull(BitmapFactory.decodeFile(requireNotNull(prepared.uri.path)))
            try {
                assertTrue(activity.isFinishing)
                assertEquals(80, bitmap.width)
                assertEquals(80, bitmap.height)
                assertTrue(Color.blue(bitmap.getPixel(3, 40)) > 200)
                assertTrue(Color.green(bitmap.getPixel(76, 40)) > 200)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun `recreating the activity preserves the selected crop coordinates and ratio`() = withEditor { editor, awaitResult ->
        val crop = awaitImage(editor.get())
        val image = crop.cropImageView
        image.targetAspectRatio = 4f / 3f
        image.zoomInImage(image.currentScale * 1.7f)
        image.postRotate(17f)
        image.setImageToWrapCropBounds(false)
        val expected = selectedBitmapCoordinates(crop)

        editor.recreate()
        val recreated = awaitImage(editor.get())
        drainUntil { recreated.cropImageView.targetAspectRatio == 4f / 3f }
        assertEquals(17f, recreated.cropImageView.currentAngle, 0.05f)
        assertArrayEquals(expected, selectedBitmapCoordinates(recreated), 0.75f)
        editor.get().onBackPressedDispatcher.onBackPressed()
        assertNull(awaitResult())
    }

    private suspend fun awaitImage(activity: PickedImageEditorActivity): UCropView {
        val crop = activity.findViewById<UCropView>(CropR.id.ucrop)
        drainUntil { activity.isFinishing || (crop.cropImageView.viewBitmap != null && !crop.overlayView.cropViewRect.isEmpty) }
        assertFalse(activity.isFinishing)
        return crop
    }

    private fun selectedBitmapCoordinates(crop: UCropView): FloatArray {
        val rect = crop.overlayView.cropViewRect
        val image = crop.cropImageView
        val coordinates = floatArrayOf(
            rect.left - image.paddingLeft, rect.top - image.paddingTop,
            rect.right - image.paddingLeft, rect.top - image.paddingTop,
            rect.right - image.paddingLeft, rect.bottom - image.paddingTop,
            rect.left - image.paddingLeft, rect.bottom - image.paddingTop,
        )
        val inverse = Matrix()
        assertTrue(image.imageMatrix.invert(inverse))
        inverse.mapPoints(coordinates)
        return coordinates
    }

    private fun withEditor(
        verify: suspend (ActivityController<PickedImageEditorActivity>, suspend () -> PreparedImage?) -> Unit,
    ) {
        // JUnit synchronously waits while explicitly draining Android's paused main looper.
        runBlocking {
            val caller = Robolectric.buildActivity(Activity::class.java).setup()
            val original = File.createTempFile("ucrop-source-", ".png", caller.get().cacheDir)
            val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
            for (x in 0 until bitmap.width) {
                val color = when (x) {
                    in 0..19, in 100..119 -> Color.RED
                    in 20..59 -> Color.BLUE
                    else -> Color.GREEN
                }
                for (y in 0 until bitmap.height) bitmap.setPixel(x, y, color)
            }
            original.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val bytes = original.readBytes()
            val result = async(NzikDispatchers.UI) { PickedImageEditor.edit(caller.get(), Uri.fromFile(original)) }
            var editor: ActivityController<PickedImageEditorActivity>? = null
            try {
                var launched: Intent? = null
                drainUntil {
                    launched = launched ?: shadowOf(caller.get()).nextStartedActivity
                    launched != null
                }
                val controller = Robolectric.buildActivity(PickedImageEditorActivity::class.java, requireNotNull(launched))
                    .setup().visible()
                editor = controller
                verify(controller) {
                    drainUntil { result.isCompleted }
                    result.await()
                }
                assertArrayEquals(bytes, original.readBytes())
            } finally {
                result.cancel()
                drainUntil { result.isCompleted }
                editor?.pause()?.stop()?.destroy()
                caller.pause().stop().destroy()
                original.delete()
            }
        }
    }

    private suspend fun drainUntil(condition: () -> Boolean) {
        withTimeout(10_000) {
            while (!condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }
    }
}
