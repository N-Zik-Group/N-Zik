package app.n_zik.android.components.settings.appearance.preview

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import app.n_zik.android.R
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.preferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Runs the activity's real composition, cancellation callbacks and preparation coroutine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ThemePreviewActivityCancellationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = RuntimeEnvironment.getApplication()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var controller: ActivityController<ThemePreviewActivity>? = null
    private var resumePreparation: (() -> Unit)? = null

    @Before
    fun setUp() {
        context.preferences.edit().clear()
            .putString(colorPaletteNameKey, ColorPaletteName.Default.name).commit()
        mockkObject(ThemePreviewAssets, ThemePreviewDemo)
        // Deliberately model a late non-cooperative callback, which resumes even after cancellation.
        coEvery { ThemePreviewAssets.prepare(any()) } coAnswers {
            suspendCoroutine { pending ->
                resumePreparation = { pending.resume(File(context.filesDir, "prepared-assets")) }
            }
        }
        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().build() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        compose.runOnUiThread {
            controller?.pause()?.stop()?.destroy()
        }
        unmockkAll()
    }

    @Test
    fun `actual local cancel returns to gallery and late preparation cannot create the player`() {
        val activity = launch(submission = false)
        compose.onNodeWithText(context.getString(R.string.common_action_cancel)).performClick()
        compose.onNodeWithText(activity.getString(R.string.theme_preview_generate)).assertIsDisplayed()
        resumeLatePreparation()
        assertFalse(activity.isFinishing)
        assertCancelledBeforePlayerCreation(activity)
        compose.onNodeWithText(activity.getString(R.string.theme_preview_generate)).assertIsDisplayed()
    }

    @Test
    fun `actual submission UI cancel stays canceled after late preparation resumes`() {
        val activity = launch(submission = true)
        compose.onNodeWithText(context.getString(R.string.common_action_cancel)).performClick()
        resumeLatePreparation()
        assertTrue(activity.isFinishing)
        assertCancelledBeforePlayerCreation(activity)
    }

    @Test
    fun `actual submission notification cancel stays canceled after late preparation resumes`() {
        val activity = launch(submission = true)
        val action = notifications.activeNotifications.single().notification.actions.single().actionIntent
        compose.runOnUiThread { action.send() }
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitUntil { activity.isFinishing }
        resumeLatePreparation()
        assertCancelledBeforePlayerCreation(activity)
    }

    private fun launch(submission: Boolean): ThemePreviewActivity {
        compose.runOnUiThread {
            val intent = if (submission) ThemePreviewActivity.submissionIntent(context, defaultThemeAppearanceDocument())
            else ThemePreviewActivity.intent(context)
            controller = Robolectric.buildActivity(ThemePreviewActivity::class.java, intent).setup().visible()
        }
        compose.waitUntil { resumePreparation != null }
        compose.onNodeWithText(context.getString(R.string.common_action_cancel)).assertIsDisplayed()
        assertEquals(1, notifications.activeNotifications.size)
        return checkNotNull(controller).get()
    }

    private fun resumeLatePreparation() {
        compose.runOnUiThread { checkNotNull(resumePreparation).invoke() }
        compose.waitForIdle()
    }

    private fun assertCancelledBeforePlayerCreation(activity: ThemePreviewActivity) {
        verify(exactly = 0) { ThemePreviewDemo.useAssets(any()) }
        verify(exactly = 0) { anyConstructed<ExoPlayer.Builder>().build() }
        val player = ThemePreviewActivity::class.java.getDeclaredField("player").apply { isAccessible = true }
        assertNull(player.get(activity))
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertNull(shadowOf(activity).resultIntent)
        assertTrue(notifications.activeNotifications.isEmpty())
    }
}
