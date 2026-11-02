package app.n_zik.android.core.coil

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImageCacheSizePreferenceTest {
    @Test fun `selecting custom without submitting a size uses the displayed default and preserves saved sizes`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = context.getSharedPreferences("image-cache-size-defaults", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        assertEquals(CoilDiskCacheMaxSize.`128MB`.bytes, configuredImageCacheBytes(preferences))

        preferences.edit().putString(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.Custom.name).commit()
        assertEquals(32_000_000L, configuredImageCacheBytes(preferences))
        assertFalse(preferences.contains(coilCustomDiskCacheKey))

        preferences.edit().putInt(coilCustomDiskCacheKey, 64).commit()
        assertEquals(64_000_000L, configuredImageCacheBytes(preferences))
        preferences.edit().putString(coilDiskCacheMaxSizeKey, CoilDiskCacheMaxSize.`128MB`.name).commit()
        assertEquals(CoilDiskCacheMaxSize.`128MB`.bytes, configuredImageCacheBytes(preferences))
    }
}
