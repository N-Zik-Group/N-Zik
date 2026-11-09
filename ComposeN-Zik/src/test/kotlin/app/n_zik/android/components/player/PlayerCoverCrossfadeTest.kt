package app.n_zik.android.components.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.preferences
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerCoverCrossfadeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `video theme fills tall viewport including rotated corners and reacts to crop preference`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val previousCrop = context.preferences.all[cropVideoThumbnailsKey] as? Boolean
        context.preferences.edit().putBoolean(cropVideoThumbnailsKey, true).commit()
        val source = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
        }
        val rotation = mutableStateOf(0f)
        val frame = PlayerCoverFrame<Painter>(
            PlayerCoverIdentity("video", "https://i.ytimg.com/vi/video/hqdefault.jpg"),
            BitmapPainter(source.asImageBitmap()),
        )
        try {
            compose.setContent {
                // Match PlayerCurrentCover's centered square and diagonal rotation overscan.
                Box(Modifier.size(80.dp, 160.dp).clipToBounds().background(Color.Black)
                    .testTag("viewport"), contentAlignment = Alignment.Center) {
                    PlayerCoverCrossfade(frame, Modifier.requiredSize(160.dp).graphicsLayer {
                        scaleX = sqrt(80f * 80f + 160f * 160f) / 160f
                        scaleY = scaleX
                        rotationZ = rotation.value
                    })
                }
            }
            fun assertCovered() {
                val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
                for (x in listOf(2, pixels.width / 2, pixels.width - 3)) {
                    for (y in listOf(2, pixels.height / 2, pixels.height - 3)) {
                        assertTrue("Theme must cover viewport at ($x,$y)", pixels[x, y].green > 0.9f)
                    }
                }
            }
            assertCovered()
            compose.runOnIdle { rotation.value = 37f }
            compose.waitForIdle()
            assertCovered()
            compose.runOnIdle {
                rotation.value = 0f
                context.preferences.edit().putBoolean(cropVideoThumbnailsKey, false).commit()
            }
            compose.waitForIdle()
            val fitted = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
            assertTrue("Off retains the fitted background", fitted[fitted.width / 2, 2].green < 0.1f)
            assertTrue(fitted[fitted.width / 2, fitted.height / 2].green > 0.9f)
            compose.runOnIdle { context.preferences.edit().putBoolean(cropVideoThumbnailsKey, true).commit() }
            compose.waitForIdle()
            assertCovered()
        } finally {
            context.preferences.edit().apply {
                previousCrop?.let { putBoolean(cropVideoThumbnailsKey, it) } ?: remove(cropVideoThumbnailsKey)
            }.commit()
        }
    }

    @Test fun `loaded cover fades instead of switching immediately`() {
        val frame = mutableStateOf(PlayerCoverFrame<Painter>(PlayerCoverIdentity("a", "a"), ColorPainter(Color.Red)))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlayerCoverCrossfade(frame.value, Modifier.size(80.dp).testTag("cover"))
        }
        fun pixel() = compose.onNodeWithTag("cover").captureToImage().toPixelMap().let {
            it[it.width / 2, it.height / 2]
        }
        assertTrue(pixel().red > 0.9f)
        compose.runOnIdle {
            frame.value = PlayerCoverFrame(PlayerCoverIdentity("b", "b"), ColorPainter(Color.Blue))
        }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("The outgoing image remains at the start", pixel().red > 0.9f)
        compose.mainClock.advanceTimeBy(PLAYER_COVER_FADE_MS / 2L)
        val middle = pixel()
        assertTrue("Both images participate during the fade", middle.red > 0.05f && middle.blue > 0.05f)
        compose.mainClock.advanceTimeBy(PLAYER_COVER_FADE_MS.toLong())
        assertTrue("The replacement finishes fully visible", pixel().blue > 0.9f)
    }
}
