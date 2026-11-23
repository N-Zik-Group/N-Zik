package app.n_zik.android.components.ui.app

import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import app.n_zik.android.MainActivity
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.thumbnailRoundnessDpKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityPreferenceChangesTest {
    @Test
    fun `appearance changes use current state without recreating the activity`() = runTest {
        val activity = mockk<MainActivity>(relaxUnitFun = true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("activity-preference-changes-test", Context.MODE_PRIVATE)
        preferences.edit().putEnum(colorPaletteModeKey, ColorPaletteMode.System).commit()
        var systemIsDark = true
        var appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = Typography.fallback,
            thumbnailShape = RoundedCornerShape(9.dp),
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        val callbacks = mutableListOf<String>()
        val listener = activity.mainActivityPreferenceChanges(
            coroutineScope = this,
            systemIsDark = { systemIsDark },
            appearance = { appearance },
            setAppearance = { appearance = it },
            updateAppearance = { updated, animate ->
                callbacks += "update animate=$animate"
                appearance = updated
            },
            refreshDynamicPalette = { callbacks += "refresh dynamic" },
            cancelDynamicAppearance = { callbacks += "cancel dynamic" },
        )

        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            preferences.edit().putFloat(thumbnailRoundnessDpKey, 48f).commit()
            assertEquals(CircleShape, appearance.thumbnailShape)
            assertEquals(emptyList<String>(), callbacks)

            systemIsDark = false
            preferences.edit().putEnum(colorPaletteNameKey, ColorPaletteName.Default).commit()

            assertEquals(DefaultLightColorPalette, appearance.colorPalette)
            assertEquals(DefaultLightColorPalette.text, appearance.typography.s.color)
            assertEquals(CircleShape, appearance.thumbnailShape)
            assertEquals(listOf("cancel dynamic", "update animate=true"), callbacks)
            verify(exactly = 0) { activity.recreate() }
        } finally {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            preferences.edit().clear().commit()
        }
    }
}
