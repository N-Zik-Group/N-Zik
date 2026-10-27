package app.n_zik.android.components.dialog.common

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import kotlin.coroutines.coroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DialogPresenceZeroDurationTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = 0f
    })
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null

    @Before fun isolate() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("dialog_presence_zero_test", context)
        context.preferences.edit().clear().commit()
        compose.mainClock.autoAdvance = false
    }

    @After fun restore() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test fun `disabled system motion leaves no retiring window after the next frame`() {
        val visible = mutableStateOf(true)
        var disposed = false
        var observedScale: Float? = null
        compose.setContent {
            LaunchedEffect(Unit) { observedScale = coroutineContext[MotionDurationScale]?.scaleFactor }
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = ColorPalette.fallback, typography = Typography.fallback,
                thumbnailShape = RoundedCornerShape(16.dp), uiRoundnessShape = RoundedCornerShape(16.dp),
                artistThumbnailShape = RoundedCornerShape(16.dp)
            )) {
                DialogPresence(visible = visible.value) {
                    DisposableEffect(Unit) { onDispose { disposed = true } }
                    DialogFrame(onDismiss = {}, title = "Disabled motion", footer = { BasicText("Done") }) {
                        BasicText("Body")
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("the actual composition uses disabled motion", 0f, requireNotNull(observedScale), 0f)
        compose.runOnIdle {
            visible.value = false
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue(disposed)
        compose.onNodeWithText("Disabled motion").assertDoesNotExist()
    }
}
