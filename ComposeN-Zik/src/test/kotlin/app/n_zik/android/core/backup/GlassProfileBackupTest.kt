package app.n_zik.android.core.backup

import java.io.File
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.mode
import app.n_zik.android.components.glass.glassOptimizationEnabledKey
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.components.import.ImportSettings
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.rescue.RescueFiles
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassProfileBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() {
        listOf(GlassProfilePreferences.LEGACY_NAME, GlassProfilePreferences.MIGRATION_NAME,
            GlassProfilePreferences.fileName("work"), "preferences", "preferences_work", "profile_preferences")
            .forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        setActiveProfile(DEFAULT_PROFILE_ID, context)
        RescueFiles.initialize(context)
        RescueFiles.setRescueProfile("work")
    }

    @Test fun `rescue exports and restores the selected inactive profiles Glass configuration`() {
        val base = GlassStore(context)
        val work = GlassStore(context, "work")
        base.setFloat(glassBlurKey, 4f)
        work.setFloat(glassBlurKey, 17f)
        work.setAreaMode(GlassArea.Switches, GlassAreaMode.ContourOnly)
        work.setBoolean(glassOptimizationEnabledKey, false)
        val file = File(context.cacheDir, "work-glass.csv")
        val uri = Uri.fromFile(file)
        assertTrue(RescueFiles.exportSettings(context, uri).isSuccess)
        work.setFloat(glassBlurKey, 22f)
        work.setAreaMode(GlassArea.Switches, GlassAreaMode.GlassAndContour)
        work.setBoolean(glassOptimizationEnabledKey, true)
        assertTrue(RescueFiles.importSettings(context, uri).isSuccess)
        assertEquals(17f, work.read().blur, 0f)
        assertEquals(GlassAreaMode.ContourOnly, work.read().mode(GlassArea.Switches))
        assertFalse(work.read().optimizationEnabled)
        assertEquals(4f, base.read().blur, 0f)
    }

    @Test fun `empty imported snapshot stays empty and never repeats legacy migration`() {
        context.getSharedPreferences(GlassProfilePreferences.LEGACY_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(glassBlurKey, 12f).commit()
        val rows = listOf(Triple("NzikGlassPreferencesV2", SettingsPreferencesBridge.GLASS_PREFERENCES, "[]"))
        assertTrue(requireNotNull(SettingsPreferencesBridge.stageGlass(context, rows, "work")).commit())
        assertTrue(GlassProfilePreferences.preferences(context, "work").all.isEmpty())
        assertEquals(2f, GlassStore(context, "work").read().blur, 0f)
        assertEquals(12f, GlassStore(context).read().blur, 0f)
    }

    @Test fun `settings import routes Glass into its explicit target instead of the active profile`() {
        val base = GlassStore(context)
        val work = GlassStore(context, "work")
        base.setFloat(glassBlurKey, 6f)
        work.setFloat(glassBlurKey, 15f)
        val file = File(context.cacheDir, "target-glass.csv")
        file.outputStream().use {
            RescueFiles.writeSettingsCsv(it, SettingsPreferencesBridge.export(context,
                profileDataPrefs(context, "work"), "work"))
        }
        work.setFloat(glassBlurKey, 21f)
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileSecurePrefsKt")
        try {
            every { profileSecurePrefs(context, "work") } returns
                context.getSharedPreferences("glass_test_secure_work", Context.MODE_PRIVATE)
            file.inputStream().use { ImportSettings.onImport(context, it, "work") }
        } finally {
            unmockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileSecurePrefsKt")
        }
        assertEquals(15f, work.read().blur, 0f)
        assertEquals(6f, base.read().blur, 0f)
    }

    @Test fun `rescue settings reset clears only its target and keeps Glass in its backup`() {
        GlassStore(context).setFloat(glassBlurKey, 5f)
        GlassStore(context, "work").setFloat(glassBlurKey, 19f)
        assertTrue(RescueFiles.resetSettings(context).isSuccess)
        assertEquals(2f, GlassStore(context, "work").read().blur, 0f)
        assertEquals(5f, GlassStore(context).read().blur, 0f)
        assertTrue(File(context.filesDir,
            "rescue_backups/${GlassProfilePreferences.fileName("work")}.xml").exists())
    }
}
