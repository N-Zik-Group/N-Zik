package app.n_zik.android.bridge

import android.app.Application
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Persistence of the auto-stop settings (contract §11.2): what [AutoStopPreferences] writes and
 * reads back.
 * JUnit 4 + [RobolectricTestRunner] through the junit-vintage-engine, with the plain [Application].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
// Conscrypt would be installed JVM-wide and break javax.crypto (HMAC) for the bridge tests that follow
@ConscryptMode(ConscryptMode.Mode.OFF)
class BridgeAutoStopSettingsTest {

    private lateinit var app: Application
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        prefs = app.preferences
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `writing the inactivity delay writes only the inactivity key`() {
        AutoStopPreferences.writeInactivity(prefs, 30)

        assertEquals(30, prefs.getInt(AutoStopPreferences.INACTIVITY_KEY, -1))
        assertFalse(prefs.contains(AutoStopPreferences.NO_CLIENT_KEY))
    }

    @Test
    fun `stored values are read back`() {
        prefs.edit().putInt(AutoStopPreferences.NO_CLIENT_KEY, 60).putInt(AutoStopPreferences.INACTIVITY_KEY, 5).commit()

        assertEquals(AutoStopSettings(noClientMinutes = 60, inactivityMinutes = 5), AutoStopPreferences.read(prefs))
    }

    @Test
    fun `missing keys give the defaults`() {
        assertEquals(AutoStopSettings(noClientMinutes = 15, inactivityMinutes = 0), AutoStopPreferences.read(prefs))
    }

    @Test
    fun `unknown stored values give the defaults`() {
        prefs.edit().putInt(AutoStopPreferences.NO_CLIENT_KEY, 7).putInt(AutoStopPreferences.INACTIVITY_KEY, 999).commit()

        assertEquals(AutoStopSettings(), AutoStopPreferences.read(prefs))
    }
}
