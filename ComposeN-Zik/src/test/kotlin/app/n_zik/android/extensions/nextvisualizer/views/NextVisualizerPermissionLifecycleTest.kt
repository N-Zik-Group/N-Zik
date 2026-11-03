package app.n_zik.android.extensions.nextvisualizer.views

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassOverlayHost
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.privacy.PermissionUsageJournal
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class NextVisualizerPermissionLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setUp() {
        setActiveProfile("default", application)
        application.preferences.edit().clear()
            .putBoolean(visualizerEnabledKey, true)
            .putBoolean(PermissionUsageJournal.PAUSED_KEY, true).commit()
        shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @Test fun `own fade requests permission only while opened and removes denied UI after exit`() {
        verifyPermissionLifecycle(hosted = false)
    }

    @Test fun `player hosted fade requests permission only while opened and removes denied UI after exit`() {
        verifyPermissionLifecycle(hosted = true)
    }

    private fun verifyPermissionLifecycle(hosted: Boolean) {
        val active = mutableStateOf(false)
        val registry = PermissionRegistry()
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        val message = application.getString(R.string.visualizer_microphone_permission_message)
        val settingsLabel = application.getString(R.string.visualizer_action_open_permission_settings)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val palette = DefaultDarkColorPalette
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalPlayerServiceBinder provides null,
                LocalAppearance provides Appearance(
                    colorPalette = palette,
                    typography = typographyOf(palette.text, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
            ) {
                Box(Modifier.size(320.dp)) {
                    if (hosted) {
                        GlassOverlayHost(active.value, GlassSettings(enabled = false), fadeDurationMillis = 500) {
                            NextVisualizer(active.value, onCover = false, fadeHosted = true)
                        }
                    } else {
                        NextVisualizer(active.value, onCover = false)
                    }
                }
            }
        }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { assertEquals(emptyList<String>(), registry.permissions) }

        repeat(2) { opening ->
            compose.runOnIdle { active.value = true }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithText(settingsLabel).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(List(opening + 1) { Manifest.permission.RECORD_AUDIO }, registry.permissions)
                registry.denyLastRequest()
            }
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.runOnIdle { active.value = false }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(600)
            compose.onNodeWithText(message).assertDoesNotExist()
            compose.onNodeWithText(settingsLabel).assertDoesNotExist()
            compose.runOnIdle { assertEquals(opening + 1, registry.permissions.size) }
        }
    }

    private class PermissionRegistry : ActivityResultRegistry() {
        val permissions = mutableListOf<String>()
        private var lastRequestCode: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            check(contract is ActivityResultContracts.RequestPermission)
            permissions.add(input as String)
            lastRequestCode = requestCode
        }

        fun denyLastRequest() {
            check(dispatchResult(requireNotNull(lastRequestCode), false))
        }
    }
}
