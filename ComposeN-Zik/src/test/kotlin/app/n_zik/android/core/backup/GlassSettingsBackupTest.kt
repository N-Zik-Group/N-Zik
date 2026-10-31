package app.n_zik.android.core.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.glass.*
import app.n_zik.android.core.rescue.RescueFiles
import app.it.fast4x.rimusic.utils.ytCookieKey
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassSettingsBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val plain get() = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val glass get() = context.getSharedPreferences(SettingsPreferencesBridge.GLASS_PREFERENCES, Context.MODE_PRIVATE)
    private val credentials get() = context.getSharedPreferences("backup_test_secure", Context.MODE_PRIVATE)
    private fun uri(name: String) = Uri.fromFile(File(context.cacheDir, name))

    @Before fun reset() {
        plain.edit().clear().commit()
        glass.edit().clear().commit()
        credentials.edit().clear().commit()
        RescueFiles.initialize(context)
        RescueFiles.setRescueProfile("default")
    }

    @Test fun `real CSV export import restores complete independent Glass store without key collisions`() {
        plain.edit().putBoolean(glassEnabledKey, false).putString("profile", "plain profile")
            .putString(SettingsPreferencesBridge.GLASS_PREFERENCES, "ordinary setting").commit()
        glass.edit().putString(glassQualityKey, "Quality").putString(glassTintSourceKey, "Custom")
            .putInt(glassCustomColorKey, 0xffaabbcc.toInt()).putFloat(glassBlurKey, 13.25f)
            .putFloat(glassTintStrengthKey, .42f).putFloat(glassVibrancyKey, 1.7f)
            .putFloat(glassFilmKey, .71f).putFloat(glassIndicatorOpacityKey, .27f)
            .putFloat(glassRimKey, .82f).putFloat(glassDepthKey, .31f).putFloat(glassAmountKey, .61f)
            .putBoolean(glassDispersionKey, true).putFloat(glassDialogDimmingKey, .38f)
            .putFloat(glassMenuDimmingKey, .63f).putString(GlassArea.Menus.preferenceKey, "GlassOnly")
            .putString(GlassArea.Nav.preferenceKey, "ContourOnly").putBoolean(glassDepthEffectKey, true).commit()
        val expected = glass.all.toMap()
        credentials.edit().putString(ytCookieKey, "secret value").commit()
        val destination = uri("roundtrip.csv")
        assertTrue(RescueFiles.exportSettings(context, destination, Result.success(credentials)).isSuccess)
        val csv = File(requireNotNull(destination.path)).readText()
        assertFalse(csv.contains("secret value"))
        plain.edit().clear().commit()
        glass.edit().clear().putString("stale", "override").commit()
        assertTrue(RescueFiles.importSettings(context, destination).isSuccess)
        assertEquals(expected, glass.all)
        assertFalse(plain.getBoolean(glassEnabledKey, true))
        assertEquals("plain profile", plain.getString("profile", null))
        assertEquals("ordinary setting", plain.getString(SettingsPreferencesBridge.GLASS_PREFERENCES, null))
        assertFalse(plain.contains(glassBlurKey))
    }

    @Test fun `credential flags and encrypted routing remain independent from Glass snapshot`() {
        credentials.edit().putString(ytCookieKey, "selected credential").commit()
        glass.edit().putString("profile", "Quality").commit()
        val file = uri("selected-credential.csv")
        assertTrue(RescueFiles.exportSettings(context, file, Result.success(credentials), includeYtb = true).isSuccess)
        credentials.edit().clear().commit()
        assertTrue(RescueFiles.importSettings(context, file, Result.success(credentials)).isSuccess)
        assertEquals("selected credential", credentials.getString(ytCookieKey, null))
        assertFalse(plain.contains(ytCookieKey))
        assertEquals("Quality", glass.getString("profile", null))
    }

    @Test fun `older CSV preserves Glass overrides and empty snapshot restores defaults`() {
        glass.edit().putFloat(glassBlurKey, 9f).commit()
        val older = uri("older.csv")
        File(requireNotNull(older.path)).writeText("Type,Key,Value\nBoolean,liquidGlassEnabled,true\n")
        assertTrue(RescueFiles.importSettings(context, older).isSuccess)
        assertEquals(9f, glass.getFloat(glassBlurKey, 0f), 0f)
        glass.edit().clear().commit()
        val empty = uri("empty-glass.csv")
        assertTrue(RescueFiles.exportSettings(context, empty).isSuccess)
        glass.edit().putFloat(glassBlurKey, 21f).commit()
        assertTrue(RescueFiles.importSettings(context, empty).isSuccess)
        assertTrue(glass.all.isEmpty())
    }

    @Test fun `malformed Glass snapshot never commits plain or Glass edits`() {
        plain.edit().putString("before", "kept").commit()
        glass.edit().putFloat(glassBlurKey, 9f).commit()
        val file = uri("invalid.csv")
        File(requireNotNull(file.path)).outputStream().use {
            RescueFiles.writeSettingsCsv(it, listOf(Triple("String", "before", "changed"),
                Triple("NzikGlassPreferencesV2", "nzik_square_glass_v2", "not JSON")))
        }
        assertTrue(RescueFiles.importSettings(context, file).isFailure)
        assertEquals("kept", plain.getString("before", null))
        assertEquals(9f, glass.getFloat(glassBlurKey, 0f), 0f)
    }

    @Test fun `shared export excludes retired selectors while preserving player and Glass choices`() {
        plain.edit().putString("UiType", "ViMusic").putString("messageType", "Essential")
            .putString("playerType", "Essential").putBoolean("showSearchTab", false).commit()
        glass.edit().putFloat(glassBlurKey, 12f).commit()

        val entries = SettingsPreferencesBridge.export(context, plain)

        assertFalse(entries.any { it.second == "UiType" || it.second == "messageType" })
        assertTrue(entries.contains(Triple("String", "playerType", "Essential")))
        assertTrue(entries.contains(Triple("Boolean", "showSearchTab", false)))
        assertTrue(entries.any { SettingsPreferencesBridge.isGlassRecord(it.first) })
        assertEquals(12f, glass.getFloat(glassBlurKey, 0f), 0f)
        // Export is a snapshot; cleaning the source store belongs to profile access/import.
        assertEquals("ViMusic", plain.getString("UiType", null))
    }

    @Test fun `old profile CSV cannot restore selectors and preserves unrelated values and credentials`() {
        RescueFiles.setRescueProfile("retired_profile")
        val profile = context.getSharedPreferences("preferences_retired_profile", Context.MODE_PRIVATE)
        profile.edit().clear().putString("UiType", "ViMusic").putString("messageType", "Essential")
            .putString("untouched", "kept").commit()
        glass.edit().putFloat(glassBlurKey, 12f).commit()
        val file = uri("retired-profile.csv")
        File(requireNotNull(file.path)).writeText(
            "Type,Key,Value\nString,UiType,ViMusic\nString,messageType,invalid\n" +
                "String,playerType,Essential\nBoolean,showSearchTab,false\nString,ytCookie,credential\n"
        )

        assertTrue(RescueFiles.importSettings(context, file, Result.success(credentials)).isSuccess)

        assertFalse(profile.contains("UiType"))
        assertFalse(profile.contains("messageType"))
        assertEquals("Essential", profile.getString("playerType", null))
        assertFalse(profile.getBoolean("showSearchTab", true))
        assertEquals("kept", profile.getString("untouched", null))
        assertEquals("credential", credentials.getString(ytCookieKey, null))
        assertFalse(profile.contains(ytCookieKey))
        assertEquals(12f, glass.getFloat(glassBlurKey, 0f), 0f)
        assertFalse(SettingsPreferencesBridge.export(context, profile).any {
            it.second == "UiType" || it.second == "messageType"
        })
    }

    @Test fun `CSV containing only retired selectors succeeds as cleanup without resetting settings`() {
        plain.edit().putString("UiType", "ViMusic").putString("messageType", "Modern")
            .putBoolean("persistentQueue", true).commit()
        val file = uri("retired-only.csv")
        File(requireNotNull(file.path)).writeText("Type,Key,Value\nString,UiType,invalid\nString,messageType,Essential\n")

        assertTrue(RescueFiles.importSettings(context, file).isSuccess)
        assertEquals(mapOf("persistentQueue" to true), plain.all)
    }

    @Test fun `retired selectors are cleaned when remaining credentials cannot be restored`() {
        plain.edit().putString("UiType", "ViMusic").putBoolean("persistentQueue", true).commit()
        val file = uri("retired-with-unavailable-credentials.csv")
        File(requireNotNull(file.path)).writeText("Type,Key,Value\nString,UiType,invalid\nString,ytCookie,credential\n")

        val outcome = RescueFiles.importSettings(context, file).getOrThrow()

        assertTrue(outcome.encryptedSkipped)
        assertEquals(mapOf("persistentQueue" to true), plain.all)
    }
}
