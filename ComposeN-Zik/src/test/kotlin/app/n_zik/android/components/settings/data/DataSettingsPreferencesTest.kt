package app.n_zik.android.components.settings.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import app.it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.pauseListenHistoryKey
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile

/** Pins the reset values and profile boundaries to the original Data page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DataSettingsPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val expected = mapOf<String, Any>(
        coilDiskCacheMaxSizeKey to CoilDiskCacheMaxSize.`128MB`.name,
        exoPlayerDiskCacheMaxSizeKey to ExoPlayerDiskCacheMaxSize.`2GB`.name,
        exoPlayerDiskDownloadCacheMaxSizeKey to ExoPlayerDiskDownloadCacheMaxSize.`2GB`.name,
        exoPlayerCacheLocationKey to ExoPlayerCacheLocation.System.name,
        exoPlayerCustomCacheKey to 32,
        coilCustomDiskCacheKey to 32,
        pauseSearchHistoryKey to false,
        pauseListenHistoryKey to false,
    )

    @Before fun setUp() {
        setActiveProfile("data-test", context)
        context.preferences.edit().clear().commit()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() { setActiveProfile("default", context) }

    @Test fun `reset restores all eight baseline values without touching other profiles`() {
        context.preferences.edit().putString("sentinel", "keep").putInt(exoPlayerCustomCacheKey, 1000).putBoolean(pauseListenHistoryKey, true).commit()
        val base = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        base.edit().putBoolean(pauseSearchHistoryKey, true).commit()
        applyDataDefaults(context)
        assertEquals(expected + mapOf("sentinel" to "keep"), context.preferences.all)
        assertEquals(mapOf(pauseSearchHistoryKey to true), base.all)
    }

    @Test fun `reset does not keep enforcing defaults after the action`() {
        applyDataDefaults(context)
        context.preferences.edit().putInt(coilCustomDiskCacheKey, 256).putBoolean(pauseListenHistoryKey, true).commit()
        assertEquals(256, context.preferences.getInt(coilCustomDiskCacheKey, 0))
        assertTrue(context.preferences.getBoolean(pauseListenHistoryKey, false))
    }

    @Test fun `target inventory preserves card order and eight distinct preference keys`() {
        assertEquals(listOf("data.cache", "data.backup", "data.auto-backup", "data.history", "data.reset"), DataSettingTargets.cards)
        assertEquals(expected.keys, DataSettingTargets.preferenceKeys.toSet())
        assertEquals(8, DataSettingTargets.preferenceKeys.size)
        assertEquals(DataSettingTargets.actions.size, DataSettingTargets.actions.toSet().size)
        assertEquals("history", DataSettingTargets.HISTORY_FOCUS)
    }
}
