package app.n_zik.android.components.settings.appearance

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.R
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h800dp")
class VideoCropSettingVisibilityTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val tag = "settings.target.appearance.cover_and_thumbnail.crop_video_thumbnails"
    private var original = emptyMap<String, Any?>()

    @Before fun preservePreferences() {
        original = listOf(showthumbnailKey, cropVideoThumbnailsKey, playerTypeKey, playerBackgroundColorsKey)
            .associateWith { context.preferences.all[it] }
    }

    @After fun restorePreferences() {
        context.preferences.edit().apply {
            original.forEach { (key, value) -> when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                else -> remove(key)
            } }
        }.commit()
    }

    @Test fun `video crop stays available with hidden player artwork in all player backgrounds`() {
        val revision = mutableStateOf(0)
        val landscape = mutableStateOf(false)
        val query = mutableStateOf(TextFieldValue(""))
        val targets = SettingsTargetRegistry()
        context.preferences.edit().putBoolean(showthumbnailKey, false).commit()
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape.value) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalSettingsTargets provides targets,
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)) {
                key(revision.value) { AppearanceCoverCard(Search(initialVisible = true, inputState = query)) }
            }
        }
        for (player in PlayerType.entries) for (background in PlayerBackgroundColors.entries) for (wide in listOf(false, true)) {
            compose.runOnIdle {
                context.preferences.edit().putString(playerTypeKey, player.name)
                    .putString(playerBackgroundColorsKey, background.name).commit()
                landscape.value = wide
                revision.value++
            }
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
        // The card itself must also survive a direct search for the crop setting.
        compose.runOnIdle {
            query.value = TextFieldValue(context.getString(R.string.settings_player_crop_video_artwork_label))
            context.preferences.edit().putBoolean(cropVideoThumbnailsKey, true).commit()
            revision.value++
        }
        compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(context.preferences.getBoolean(cropVideoThumbnailsKey, true)) }
    }
}
