package app.n_zik.android.components.image

import java.io.File
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
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
            var editor: org.robolectric.android.controller.ActivityController<PickedImageEditorActivity>? = null
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

    private suspend fun drainUntil(condition: () -> Boolean) {
        withTimeout(10_000) {
            while (!condition()) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(10)
            }
        }
    }
}
