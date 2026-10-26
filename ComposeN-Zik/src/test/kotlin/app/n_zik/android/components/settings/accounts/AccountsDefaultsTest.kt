package app.n_zik.android.components.settings.accounts

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Golden reset contract from AccountsSettings at 89c9a371, independent of UI composition. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AccountsDefaultsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val secure: SharedPreferences = context.getSharedPreferences("accounts-test-secure", Context.MODE_PRIVATE)
    private val expected: Map<String, Any> = mapOf(
        "enableYoutubeLogin" to false,
        "enableYoutubeSync" to false,
        "isDiscordPresenceEnabled" to false,
        "isDiscordAdvancedMode" to false,
        "discordAdvancedActivityType" to 2,
        "discordAdvancedPausePresenceEnabled" to true,
        "discordAdvancedName" to "",
        "discordAdvancedStateTemplate" to "",
        "discordAdvancedDetailsTemplate" to "",
        "discordAdvancedPauseTemplate" to "",
        "discordAdvancedButton1Enabled" to true,
        "discordAdvancedButton1Label" to "",
        "discordAdvancedButton1Url" to "",
        "discordAdvancedButton2Enabled" to true,
        "discordAdvancedButton2Label" to "",
        "discordAdvancedButton2Url" to "",
    )

    @Before fun setUp() {
        setActiveProfile("accounts-test", context)
        context.preferences.edit().clear().commit()
        secure.edit().clear().commit()
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns secure
    }

    @After fun tearDown() {
        unmockkAll()
        setActiveProfile("default", context)
    }

    @Test fun resetWritesOnlyTheSeventeenOriginalDefaultsToTheirOriginalStores() {
        val editor = secure.edit()
        expected.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, !value)
                is Int -> editor.putInt(key, 7)
                is String -> editor.putString(key, "seeded")
            }
        }
        editor.commit()
        context.preferences.edit().putBoolean("restartActivity", true).commit()

        applyAccountsDefaults(context)

        assertEquals(expected, secure.all)
        assertEquals(mapOf("restartActivity" to false), context.preferences.all)
    }

    @Test fun resetPreservesCredentialsAndAdvancedOptionsNotInTheOriginalResetSet() {
        secure.edit()
            .putString("ytCookie", "test-cookie")
            .putString("discordPersonalAccessToken", "test-token")
            .putBoolean("discordAdvancedShowArtwork", false)
            .putInt("discordAdvancedRefreshIntervalMs", 17000)
            .commit()
        context.preferences.edit().putBoolean("useLoginForSearch", false).commit()

        applyAccountsDefaults(context)
        applyAccountsDefaults(context)

        assertEquals("test-cookie", secure.getString("ytCookie", null))
        assertEquals("test-token", secure.getString("discordPersonalAccessToken", null))
        assertFalse(secure.getBoolean("discordAdvancedShowArtwork", true))
        assertEquals(17000, secure.getInt("discordAdvancedRefreshIntervalMs", 0))
        assertFalse(context.preferences.getBoolean("useLoginForSearch", true))
        assertEquals(expected.keys + setOf("ytCookie", "discordPersonalAccessToken", "discordAdvancedShowArtwork", "discordAdvancedRefreshIntervalMs"), secure.all.keys)
    }
}
