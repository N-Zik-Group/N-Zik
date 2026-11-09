package app.n_zik.android.core.coil

import android.app.Application
import android.content.Context
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class VideoArtworkPreferenceTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var previous: Boolean? = null

    @Before fun setUp() {
        previous = context.preferences.all[cropVideoThumbnailsKey] as? Boolean
        context.preferences.edit().putBoolean(cropVideoThumbnailsKey, true).commit()
    }

    @After fun tearDown() {
        context.preferences.edit().apply {
            previous?.let { putBoolean(cropVideoThumbnailsKey, it) } ?: remove(cropVideoThumbnailsKey)
        }.commit()
    }

    @Test fun `visible artwork updates when the preference changes without recreating its screen`() {
        var scale: ContentScale? = null
        compose.setContent {
            scale = rememberVideoArtworkContentScale("https://i.ytimg.com/vi/video/hqdefault.jpg", ContentScale.FillHeight)
        }
        compose.runOnIdle {
            assertEquals(ContentScale.Crop, scale)
            context.preferences.edit().putBoolean(cropVideoThumbnailsKey, false).commit()
        }
        compose.runOnIdle { assertEquals(ContentScale.Fit, scale) }
    }
}
