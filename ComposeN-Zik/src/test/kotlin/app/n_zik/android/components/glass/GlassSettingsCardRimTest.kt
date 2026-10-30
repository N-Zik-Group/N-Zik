package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassSettingsCardRimTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: GlassStore

    @Before fun clearSettings() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, false).commit()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        store = GlassStore(context)
        store.setFloat(glassRimKey, 1f)
        store.setFloat(glassTintStrengthKey, 1f)
        store.setColor(0xFFFF0000.toInt())
        store.setBoolean(glassImageRimsKey, false)
    }

    @Test fun `mounted card follows master area tint and rim preferences without changing fill or content`() {
        val fill = Color(0xFF165080)
        composeRule.setContent {
            Card(
                modifier = Modifier.size(80.dp).testTag("card").glassSettingsCardRim(RectangleShape),
                shape = RectangleShape,
                colors = CardDefaults.cardColors(containerColor = fill),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(16.dp).background(Color.Yellow))
                }
            }
        }
        fun capture() = composeRule.onNodeWithTag("card").captureToImage().toPixelMap()
        val original = capture()
        composeRule.runOnIdle { context.preferences.edit().putBoolean(glassEnabledKey, true).commit() }
        val rimmed = capture()
        assertNotEquals(original[0, original.height / 2], rimmed[0, rimmed.height / 2])
        for (y in rimmed.height / 4 until rimmed.height * 3 / 4) {
            for (x in rimmed.width / 4 until rimmed.width * 3 / 4) {
                assertEquals(original[x, y], rimmed[x, y])
            }
        }
        composeRule.runOnIdle { store.setColor(0xFF00FF00.toInt()) }
        val green = capture()
        assertTrue(green[0, green.height / 2].green > rimmed[0, rimmed.height / 2].green)
        assertEquals(Color.Yellow, green[green.width / 2, green.height / 2])
        listOf<() -> Unit>(
            { store.setBoolean(glassSettingsCardRimsKey, false) },
            { store.setBoolean(glassSettingsCardRimsKey, true); store.setFloat(glassRimKey, 0f) },
            { store.setFloat(glassRimKey, 1f); context.preferences.edit().putBoolean(glassEnabledKey, false).commit() },
        ).forEach { disable ->
            composeRule.runOnIdle(disable)
            val disabled = capture()
            for (y in 0 until disabled.height) {
                for (x in 0 until disabled.width) assertEquals(original[x, y], disabled[x, y])
            }
        }
    }

    @Test fun `mounted card contour follows its original live shape`() {
        var shape: Shape by mutableStateOf(RectangleShape)
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        composeRule.setContent {
            Box(Modifier.size(80.dp).background(Color.Black).testTag("frame")) {
                Card(
                    modifier = Modifier.size(80.dp).glassSettingsCardRim(shape),
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = Color.Blue),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {}
            }
        }
        val square = composeRule.onNodeWithTag("frame").captureToImage().toPixelMap()
        assertNotEquals(Color.Black, square[0, 0])
        composeRule.runOnIdle { shape = RoundedCornerShape(20.dp) }
        val rounded = composeRule.onNodeWithTag("frame").captureToImage().toPixelMap()
        assertEquals(Color.Black, rounded[0, 0])
        assertNotEquals(Color.Blue, rounded[0, rounded.height / 2])
        assertEquals(Color.Blue, rounded[rounded.width / 2, rounded.height / 2])
    }
}
