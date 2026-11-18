package app.n_zik.android.components.dialog.song

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.R
import coil3.ImageLoader
import coil3.EventListener
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "fr-w320dp-h640dp")
class EditMetadataDialogCoverArtLayoutTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var loader: ImageLoader
    private val loadedCovers = AtomicInteger()
    private val decodeThreads = CopyOnWriteArrayList<Thread>()
    private val decodedCover = AtomicReference<DecodeResult?>()

    @Before
    fun setupImageLoader() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        loader = ImageLoader.Builder(context)
            .eventListener(object : EventListener() {
                override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
                    if (request.data is ByteArray) decodeThreads += Thread.currentThread()
                }

                override fun decodeEnd(
                    request: ImageRequest,
                    decoder: Decoder,
                    options: Options,
                    result: DecodeResult?,
                ) {
                    if (request.data is ByteArray) {
                        decodeThreads += Thread.currentThread()
                        decodedCover.set(result)
                    }
                }

                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    if (request.data is ByteArray) loadedCovers.incrementAndGet()
                }
            })
            .build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
    }

    @After
    fun cleanupImageLoader() {
        loader.shutdown()
        unmockkObject(ImageCacheFactory)
    }

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Test
    fun `large cover decodes off main at thumbnail size beside a readable narrow editor action`() {
        val bitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        checkCoverActionLayout(coverBytes = bytes, fontScale = 1f)
        assertTrue("both decode boundaries must be observed", decodeThreads.size >= 2)
        assertTrue("image decoding must stay off Main", decodeThreads.all { it != Looper.getMainLooper().thread })
        val decoded = checkNotNull(decodedCover.get()) { "the real PNG should decode successfully" }
        val context = ApplicationProvider.getApplicationContext<Application>()
        val thumbnailPixels = ceil(56 * context.resources.displayMetrics.density).toInt()
        // Bitmap decoders may round sampling to the next power of two.
        val upperBound = thumbnailPixels * 2
        assertTrue("large covers should be sampled", decoded.isSampled)
        assertTrue("decoded width ${decoded.image.width} exceeds $upperBound", decoded.image.width in 1..upperBound)
        assertTrue("decoded height ${decoded.image.height} exceeds $upperBound", decoded.image.height in 1..upperBound)
    }

    @Test
    fun `cover action label remains readable and clickable at enlarged font size without a cover`() {
        checkCoverActionLayout(coverBytes = null, fontScale = 1.5f)
    }

    private fun checkCoverActionLayout(coverBytes: ByteArray?, fontScale: Float) {
        var picks = 0
        var label = ""
        var coverDescription = ""
        composeRule.setContent {
            val density = LocalDensity.current
            label = LocalContext.current.getString(R.string.metadata_action_change_cover)
            coverDescription = LocalContext.current.getString(R.string.metadata_cover_art)
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                // The real editor leaves a narrow content area after dialog and body padding.
                Box(Modifier.width(240.dp)) {
                    val dialog = EditMetadataDialog { null }
                    dialog.CoverArtField(coverBytes, onChangeCover = { picks++ })
                }
            }
        }
        composeRule.waitForIdle()
        if (coverBytes != null) {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                loadedCovers.get() > 0 &&
                    composeRule.onAllNodesWithContentDescription(coverDescription).fetchSemanticsNodes().size == 1
            }
        }
        composeRule.onAllNodesWithText(label).assertCountEquals(1)
        val text = composeRule.onNodeWithText(label, useUnmergedTree = true)
        text.assertIsDisplayed()
        val bounds = text.getBoundsInRoot()
        assertTrue("cover action label needs usable horizontal space: $bounds", bounds.right - bounds.left >= 100.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue("the French cover action label should wrap", layout.lineCount > 1)
        assertTrue("wrapping must not degrade to a vertical letter column", layout.lineCount <= 6)
        assertTrue("all cover action text remains visible", !layout.hasVisualOverflow)
        composeRule.onNodeWithText(label).performClick()
        composeRule.runOnIdle { assertEquals("cover dialog opens once", 1, picks) }
    }
}
