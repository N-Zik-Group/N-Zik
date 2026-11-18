package app.n_zik.android.components.ui.screens.profiles

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.components.settings.appearance.preview.CreatorAvatar
import app.n_zik.android.components.settings.appearance.preview.CreatorMedia
import app.n_zik.android.components.settings.appearance.preview.CreatorProfile
import app.n_zik.android.components.settings.appearance.preview.StoreAuthor
import app.n_zik.android.components.settings.appearance.preview.ThemeStoreCatalog
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.staticImagePlaceholderVectors
import app.n_zik.android.core.network.fullOfflineModeKey
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.request.crossfade
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.unit.dp
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileFaceAvatarLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setupPreferences() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
    }

    @Test fun `creator avatar shows stable loading until resolution then restores absent-photo initials`() {
        val resolving = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = Typography.fallback,
                thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
            )) {
                ProfileFaceAvatar(
                    avatar = FaceAvatar.Initials("Creator Name"), faceName = "Creator Name",
                    modifier = Modifier.testTag("avatar"), isLoadingPhoto = resolving.value,
                    photoLoadIdentity = "creator-123",
                )
            }
        }
        compose.waitForIdle()
        val pending = pixels()
        compose.mainClock.advanceTimeBy(8_000)
        compose.waitForIdle()
        assertArrayEquals("Resolution must outlive the normal null-model fallback timeout", pending, pixels())
        compose.runOnIdle { resolving.value = false }
        compose.waitForIdle()
        assertFalse("Absent photo must reveal final initials", pending.contentEquals(pixels()))
    }

    @Test fun `catalog discovery and delayed avatar asset keep the same glyph until image succeeds`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val profile = mutableStateOf<CreatorProfile?>(null)
        val resolved = CompletableDeferred<Any?>()
        val releaseImage = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val draws = AtomicInteger()
        val catalog = mockk<ThemeStoreCatalog>()
        coEvery { catalog.creatorImage(any(), any()) } coAnswers { resolved.await() }
        val loader = ImageLoader.Builder(context).crossfade(false).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): SuccessResult {
                    requests.incrementAndGet()
                    releaseImage.await()
                    val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.YELLOW)
                    return SuccessResult(bitmap.asImage(), chain.request, DataSource.NETWORK)
                }
            })
        }.build()
        mockkObject(ImageCacheFactory, Random.Default)
        every { ImageCacheFactory.LOADER } returns loader
        every { Random.Default.nextInt(staticImagePlaceholderVectors.size) } answers {
            draws.getAndIncrement() % staticImagePlaceholderVectors.size
        }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides testAppearance()) {
                    CreatorAvatar(profile.value, StoreAuthor(123, "creator"), catalog, 48.dp, Modifier.testTag("avatar"))
                }
            }
            compose.waitUntil { requests.get() >= 1 }
            val pending = pixels()
            // Keep the initials underlay identical: antialiased circle-edge pixels blend
            // with it, independently of the loading glyph's identity and geometry.
            compose.runOnIdle { profile.value = creatorProfile().copy(displayName = "creator") }
            compose.waitForIdle()
            assertArrayEquals("Discovery must keep the existing loading frame", pending, pixels())
            compose.runOnIdle { resolved.complete("https://example.test/verified-avatar.png") }
            compose.waitUntil { requests.get() >= 2 }
            assertArrayEquals("Resolved model must keep the existing loading frame", pending, pixels())
            assertEquals("Catalog discovery and model resolution must not reroll", 1, draws.get())
            compose.runOnIdle { releaseImage.complete(Unit) }
            compose.waitUntil(timeoutMillis = 5_000) { pixels().any { it == android.graphics.Color.YELLOW } }
        } finally {
            releaseImage.complete(Unit)
            loader.shutdown()
            unmockkObject(ImageCacheFactory, Random.Default)
        }
    }

    @Test fun `catalog avatar resolution failure reveals final initials`() {
        val resolved = CompletableDeferred<Any?>()
        val called = AtomicInteger()
        val catalog = mockk<ThemeStoreCatalog>()
        coEvery { catalog.creatorImage(any(), any()) } coAnswers {
            called.incrementAndGet()
            resolved.await()
        }
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides testAppearance()) {
                CreatorAvatar(creatorProfile(), StoreAuthor(123, "creator"), catalog, 48.dp, Modifier.testTag("avatar"))
            }
        }
        compose.waitUntil { called.get() == 1 }
        val pending = pixels()
        compose.mainClock.advanceTimeBy(8_000)
        assertArrayEquals("Unresolved catalog asset keeps its loading frame", pending, pixels())
        compose.runOnIdle { resolved.completeExceptionally(IOException("asset unavailable")) }
        compose.waitForIdle()
        assertFalse("Failed catalog asset must reveal initials", pending.contentEquals(pixels()))
    }

    @Test fun `offline creator avatar filters resolved remote model before Coil`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.preferences.edit().putBoolean(fullOfflineModeKey, true).commit()
        val requests = AtomicInteger()
        val called = AtomicInteger()
        val catalog = mockk<ThemeStoreCatalog>()
        coEvery { catalog.creatorImage(any(), any()) } answers {
            called.incrementAndGet()
            "https://example.test/remote-avatar.png"
        }
        val loader = ImageLoader.Builder(context).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): SuccessResult {
                    requests.incrementAndGet()
                    return SuccessResult(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).asImage(),
                        chain.request, DataSource.NETWORK)
                }
            })
        }.build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides testAppearance()) {
                    CreatorAvatar(creatorProfile(), StoreAuthor(123, "creator"), catalog, 48.dp, Modifier.testTag("avatar"))
                }
            }
            compose.waitUntil { called.get() == 1 }
            compose.waitForIdle()
            assertEquals("Offline source filtering must prevent a remote Coil request", 0, requests.get())
        } finally {
            loader.shutdown()
            unmockkObject(ImageCacheFactory)
        }
    }

    @Test fun `resolved creator photo decode failure reveals initials`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val release = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val catalog = mockk<ThemeStoreCatalog>()
        coEvery { catalog.creatorImage(any(), any()) } returns "https://example.test/broken-avatar.png"
        val loader = ImageLoader.Builder(context).crossfade(false).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ErrorResult {
                    requests.incrementAndGet()
                    release.await()
                    return ErrorResult(image = null, request = chain.request, throwable = IOException("invalid image data"))
                }
            })
        }.build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides testAppearance()) {
                    CreatorAvatar(creatorProfile(), StoreAuthor(123, "creator"), catalog, 48.dp, Modifier.testTag("avatar"))
                }
            }
            compose.waitUntil { requests.get() == 1 }
            val pending = pixels()
            compose.runOnIdle { release.complete(Unit) }
            compose.waitUntil(timeoutMillis = 5_000) { !pending.contentEquals(pixels()) }
        } finally {
            release.complete(Unit)
            loader.shutdown()
            unmockkObject(ImageCacheFactory)
        }
    }

    private fun creatorProfile() = CreatorProfile(
        1, 123, "creator", "Creator Name", "", CreatorMedia("custom"), CreatorMedia("generated"),
    )

    private fun testAppearance() = Appearance(
        colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    private fun pixels(): IntArray {
        val bitmap = compose.onNodeWithTag("avatar").captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }
}
