package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.app.Application
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StagedCoverDialogTest {
    private val scope = CoroutineScope(SupervisorJob() + NzikDispatchers.DATA)
    private val context get() = RuntimeEnvironment.getApplication()
    private var currentId = "song-one"
    private var writes = 0

    private fun dialog() = object : StagedCoverDialog(
        mutableStateOf(false), mutableStateOf(TextFieldValue()), context, scope,
        getTarget = { CoverEditTarget(currentId, "https://example.com/original.jpg") { writes++ } },
        save = { _, _, _, _, _ -> writes++; error("Unexpected file save") },
    ) {}

    @After
    fun cleanup() { scope.cancel() }

    @Test
    fun `temporary URI cannot be confirmed without its live edited draft`() {
        val dialog = dialog().apply { showDialog() }
        val source = File(context.cacheDir, "image_edit/input.jpg").apply { parentFile?.mkdirs(); writeText("draft") }
        dialog.onSet("content://${context.packageName}.fileprovider/image_edit/input.jpg")
        assertEquals(0, writes)
        assertTrue(dialog.isActive)
        assertEquals("draft", source.readText())
    }

    @Test
    fun `changed entity after opening cannot receive confirmation`() {
        val dialog = dialog().apply { showDialog() }
        currentId = "song-two"
        dialog.onSet("https://example.com/new.jpg")
        assertEquals(0, writes)
        assertFalse(dialog.isActive)
    }

    @Test
    fun `changed profile after opening cannot receive confirmation`() {
        val dialog = dialog().apply { showDialog() }
        setActiveProfile("other-profile", context)
        dialog.onSet("https://example.com/new.jpg")
        assertEquals(0, writes)
        assertFalse(dialog.isActive)
    }
}
