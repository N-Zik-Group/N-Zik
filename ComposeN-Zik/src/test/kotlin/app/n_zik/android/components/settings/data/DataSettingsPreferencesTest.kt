package app.n_zik.android.components.settings.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.n_zik.android.enums.CoilDiskCacheMaxSize
import app.n_zik.android.enums.ExoPlayerCacheLocation
import app.n_zik.android.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.android.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.core.coil.PROFILE_IMAGE_CACHE_CUSTOM_MB
import app.n_zik.android.utils.coilDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerCacheLocationKey
import app.n_zik.android.utils.exoPlayerCustomCacheKey
import app.n_zik.android.utils.exoPlayerDiskCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.n_zik.android.utils.exoPlayerMinTimeForEventKey
import app.n_zik.android.utils.pauseListenHistoryKey
import app.n_zik.android.utils.pauseSearchHistoryKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.writeProfileEntries
import app.n_zik.android.core.profiles.ProfileCacheLocationMigration
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.profileDownloadSpanBytes
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

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
        PROFILE_IMAGE_CACHE_CUSTOM_MB to 32,
    )

    @Before fun setUp() {
        check(context.writeProfileEntries(listOf("data-test" to "Data Test")))
        setActiveProfile("data-test", context)
        context.preferences.edit().clear().commit()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() { setActiveProfile("default", context) }

    @Test fun `reset reports a download limit retained to protect existing files`() {
        val originalLimit = ExoPlayerDiskDownloadCacheMaxSize.entries.first { it.bytes > ExoPlayerDiskDownloadCacheMaxSize.`2GB`.bytes }
        context.preferences.edit().putString(exoPlayerDiskDownloadCacheMaxSizeKey, originalLimit.name).commit()
        mockkStatic(::profileDownloadSpanBytes)
        try {
            every { profileDownloadSpanBytes(context, "data-test") } returns
                ExoPlayerDiskDownloadCacheMaxSize.`2GB`.bytes + 1
            var retained = false
            assertTrue(applyDataDefaults(context, onDownloadLimitRetained = { retained = true }))
            assertTrue(retained)
            assertEquals(originalLimit.name, context.preferences.getString(exoPlayerDiskDownloadCacheMaxSizeKey, null))
        } finally {
            unmockkStatic(::profileDownloadSpanBytes)
        }
    }

    @Test fun `reset restores storage without changing search history or other profiles`() {
        context.preferences.edit().putString("sentinel", "keep").putInt(exoPlayerCustomCacheKey, 1000).putBoolean(pauseListenHistoryKey, true).commit()
        val base = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        base.edit().putBoolean(pauseSearchHistoryKey, true).commit()
        applyDataDefaults(context)
        assertEquals((expected - exoPlayerCacheLocationKey) + mapOf("sentinel" to "keep", pauseListenHistoryKey to true), context.preferences.all)
        assertEquals(mapOf(pauseSearchHistoryKey to true), base.all)
    }

    @Test fun `reset restores automatic backup defaults for the current profile and keeps its destination`() {
        val prefs = context.preferences
        prefs.edit()
            .putInt(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_DAILY)
            .putInt(BackupManager.PREF_MAX_BACKUPS, 15)
            .putBoolean(BackupManager.PREF_PRE_INSTALL, true)
            .putString(BackupManager.PREF_URI, "content://backup-folder")
            .commit()
        val other = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        other.edit().putInt(BackupManager.PREF_INTERVAL, BackupManager.INTERVAL_DAILY)
            .putString(BackupManager.PREF_URI, "content://other-backup-folder").commit()
        val cancelled = mutableListOf<String>()

        assertTrue(applyDataDefaults(context) { cancelled += it.id })
        assertTrue(applyDataDefaults(context) { cancelled += it.id })

        assertEquals(listOf("data-test"), cancelled)
        assertFalse(BackupManager.isEnabled(prefs))
        assertFalse(prefs.contains(BackupManager.PREF_INTERVAL))
        assertFalse(prefs.contains(BackupManager.PREF_MAX_BACKUPS))
        assertFalse(prefs.contains(BackupManager.PREF_PRE_INSTALL))
        assertEquals("content://backup-folder", prefs.getString(BackupManager.PREF_URI, null))
        assertEquals(BackupManager.INTERVAL_DAILY, other.getInt(BackupManager.PREF_INTERVAL, -1))
        assertEquals("content://other-backup-folder", other.getString(BackupManager.PREF_URI, null))
    }

    @Test fun `reset stages a return from Private without switching the effective root`() {
        context.preferences.edit().putString(exoPlayerCacheLocationKey, ExoPlayerCacheLocation.Private.name).commit()

        assertFalse(hasPendingDataResetCacheMove(context, "data-test"))
        applyDataDefaults(context)

        val owner = checkNotNull(profileStorageIdentity(context, "data-test"))
        assertTrue(hasPendingDataResetCacheMove(context, "data-test"))
        assertEquals(ExoPlayerCacheLocation.System, ProfileCacheLocationMigration.requested(context, owner))
        assertEquals(ExoPlayerCacheLocation.Private.name, context.preferences.getString(exoPlayerCacheLocationKey, null))
    }

    @Test fun `reset does not keep enforcing defaults after the action`() {
        applyDataDefaults(context)
        context.preferences.edit().putInt(PROFILE_IMAGE_CACHE_CUSTOM_MB, 256).putBoolean(pauseListenHistoryKey, true).commit()
        assertEquals(256, context.preferences.getInt(PROFILE_IMAGE_CACHE_CUSTOM_MB, 0))
        assertTrue(context.preferences.getBoolean(pauseListenHistoryKey, false))
    }

    @Test fun `target inventory preserves card order and eight distinct preference keys`() {
        assertEquals(listOf("data.cache", "data.backup", "data.auto-backup", "data.history", "data.reset"), DataSettingTargets.cards)
        assertEquals(expected.keys, DataSettingTargets.preferenceKeys.toSet())
        assertEquals(9, DataSettingTargets.preferenceKeys.size)
        assertEquals(DataSettingTargets.actions.size, DataSettingTargets.actions.toSet().size)
        assertEquals("history", DataSettingTargets.HISTORY_FOCUS)
    }
}
