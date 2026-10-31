package app.n_zik.android.components.settings.common

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.R
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.defaultGlassQuality
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassProfileDefaults
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.just
import io.mockk.Runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class AllSettingsDefaultsTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun store(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val secure get() = store("all-settings-secure")

    @Before fun setUp() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        setActiveProfile("all-settings", context)
        context.preferences.edit().clear().putBoolean("pauseOnHeadphoneDisconnect", false)
            .putString("navigationBarPosition", "Left").putString("colorPaletteMode", "Light")
            .putBoolean("fullOfflineModeEnabled", true).putBoolean(glassEnabledKey, true)
            .putString("queue", "saved queue").putString("playlist-data", "saved playlists")
            .putBoolean("onboarding_completed", true).commit()
        store("app_settings").edit().putString("profile-records", "saved profiles").commit()
        store("preferences_untouched").edit().clear().putString("navigationBarPosition", "Left").commit()
        secure.edit().clear().putBoolean("enableYoutubeLogin", true).putBoolean("enableYoutubeSync", true)
            .putString("ytCookie", "cookie").putString("discordPersonalAccessToken", "token")
            .putString("lastfm_session_key", "session").putString("proxyPassword", "password").commit()
        GlassStore(context).apply { setProfile(GlassQuality.Quality); setFloat(glassFilmKey, .263f) }
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns secure
        mockkObject(BackupManager)
        every { BackupManager.scheduleBackup(any(), BackupManager.INTERVAL_NONE) } just Runs
    }

    @After fun tearDown() {
        unmockkAll()
        setActiveProfile("default", context)
    }

    @Test fun `all defaults are applied before one callback while user content and secrets survive repeatedly`() {
        val file = context.filesDir.resolve("all-settings-test-download").apply { writeText("download content") }
        val database = context.getDatabasePath("all-settings-test.db").apply { parentFile?.mkdirs(); writeText("database content") }
        var callbacks = 0
        try {
            repeat(2) {
                applyAllSettingsDefaults(context) {
                    callbacks++
                    assertEquals(true, context.preferences.getBoolean("pauseOnHeadphoneDisconnect", false))
                    assertEquals("BottomFloating", context.preferences.getString("navigationBarPosition", null))
                    assertEquals("System", context.preferences.getString("colorPaletteMode", null))
                    assertFalse(context.preferences.getBoolean("fullOfflineModeEnabled", true))
                    assertFalse(context.preferences.getBoolean(glassEnabledKey, true))
                    val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
                    assertEquals(glassProfileDefaults(defaultGlassQuality(Build.VERSION.SDK_INT, lowRam)), GlassStore(context).read())
                }
                assertEquals("cookie", secure.getString("ytCookie", null))
                assertEquals(true, secure.getBoolean("enableYoutubeLogin", false))
                assertEquals(false, secure.getBoolean("enableYoutubeSync", true))
                assertEquals("token", secure.getString("discordPersonalAccessToken", null))
                assertEquals("session", secure.getString("lastfm_session_key", null))
                assertEquals("password", secure.getString("proxyPassword", null))
                assertEquals("saved queue", context.preferences.getString("queue", null))
                assertEquals("saved playlists", context.preferences.getString("playlist-data", null))
                assertEquals(true, context.preferences.getBoolean("onboarding_completed", false))
                assertEquals("saved profiles", store("app_settings").getString("profile-records", null))
                assertEquals(mapOf("navigationBarPosition" to "Left"), store("preferences_untouched").all)
                assertEquals("download content", file.readText())
                assertEquals("database content", database.readText())
            }
            assertEquals(2, callbacks)
        } finally { file.delete(); database.delete() }
    }

    private fun renderReset() {
        compose.setContent {
            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, CircleShape, CircleShape, CircleShape),
            ) { SettingsResetCard((0..7).toList(), "", resetContent = {}) }
        }
    }

    @Test fun `selecting all then cancelling confirmation does not apply settings`() {
        renderReset()
        val before = context.preferences.all.toMap()
        val secureBefore = secure.all.toMap()
        compose.onNodeWithTag("settings.reset.open").performClick()
        compose.onNodeWithTag("settings.reset.all.open").performClick()
        compose.onNodeWithTag("settings.reset.all.confirmation").assertExists()
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("settings.reset.all.confirmation").performSemanticsAction(
            SemanticsActions.GetTextLayoutResult,
        ) { it(textLayouts) }
        assertEquals(DefaultDarkColorPalette.text, textLayouts.single().layoutInput.style.color)
        assertEquals(before, context.preferences.all)
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose.waitForIdle()
        assertEquals(before, context.preferences.all)
        assertEquals(secureBefore, secure.all)
    }

    @Test fun `confirming all applies defaults and preserves saved credentials`() {
        renderReset()
        compose.onNodeWithTag("settings.reset.open").performClick()
        compose.onNodeWithTag("settings.reset.all.open").performClick()
        assertEquals(false, context.preferences.getBoolean("pauseOnHeadphoneDisconnect", true))
        compose.onNodeWithText(context.getString(R.string.settings_reset_all_confirm)).performClick()
        compose.waitForIdle()
        assertEquals(true, context.preferences.getBoolean("pauseOnHeadphoneDisconnect", false))
        assertEquals("cookie", secure.getString("ytCookie", null))
        assertEquals("token", secure.getString("discordPersonalAccessToken", null))
    }
}
