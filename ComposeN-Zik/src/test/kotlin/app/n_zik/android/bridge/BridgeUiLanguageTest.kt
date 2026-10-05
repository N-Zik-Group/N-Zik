package app.n_zik.android.bridge

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.utils.languageAppKey
import app.it.fast4x.rimusic.utils.preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The mirror guarantee of [BridgeUiLanguage] (contract §5, `ui.language`, since 1.9.0): the bridge
 * serves exactly what the phone's own UI reads from the active profile's preferences — an explicit
 * choice as its BCP-47 code, `System` as the configured locale (never the tag `und`).
 * JUnit 4 + [RobolectricTestRunner] through the junit-vintage-engine, with the plain [Application].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BridgeUiLanguageTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        app.preferences.edit().clear().commit()
    }

    @Test
    fun `an explicit language choice is served as its BCP-47 code`() {
        app.preferences.edit().putString(languageAppKey, Languages.French.name).commit()

        assertEquals("fr", BridgeUiLanguage.effective(app))
    }

    @Test
    fun `an explicit choice with a regional code keeps the region`() {
        app.preferences.edit().putString(languageAppKey, Languages.PortugueseBrazilian.name).commit()

        assertEquals("pt-BR", BridgeUiLanguage.effective(app))
    }

    @Test
    fun `System mirrors the configured locale`() {
        val context = app.createConfigurationContext(
            Configuration(app.resources.configuration).also { it.setLocales(LocaleList(Locale.GERMAN)) },
        )

        assertEquals("de", BridgeUiLanguage.effective(context))
    }

    @Test
    fun `an undetermined locale is served as null, never the tag und`() {
        val context = app.createConfigurationContext(
            Configuration(app.resources.configuration).also { it.setLocales(LocaleList(Locale.forLanguageTag("und"))) },
        )

        assertNull(BridgeUiLanguage.effective(context))
    }

    @Test
    fun `an unreadable stored value degrades to the System default`() {
        app.preferences.edit().putString(languageAppKey, "NoSuchLanguage").commit()

        // `getEnum` falls back to the default (`System`): the mirror of the OS locale, never a crash
        assertEquals(
            app.resources.configuration.locales.get(0).toLanguageTag(),
            BridgeUiLanguage.effective(app),
        )
    }

    @Test
    fun `an empty locale list falls back to the JVM default locale`() {
        val context = app.createConfigurationContext(
            Configuration(app.resources.configuration).also { it.setLocales(LocaleList()) },
        )

        assertEquals(Locale.getDefault().toLanguageTag(), BridgeUiLanguage.effective(context))
    }

    @Config(sdk = [33], application = BrokenResourcesApplication::class)
    @Test
    fun `a context whose resources throw degrades to null`() {
        // The app is created by the harness with working resources; break it only from the test.
        val context = RuntimeEnvironment.getApplication() as BrokenResourcesApplication
        context.broken = true

        assertNull(BridgeUiLanguage.effective(context))
    }

    @Test
    fun `an und regional locale is served as null, never the region alone`() {
        val context = app.createConfigurationContext(
            Configuration(app.resources.configuration).also { it.setLocales(LocaleList(Locale.forLanguageTag("und-FR"))) },
        )

        assertNull(BridgeUiLanguage.effective(context))
    }
}

/**
 * An [Application] whose [Resources] are unavailable once [broken] is set (the harness itself reads
 * the resources while creating the app): exercises the `runCatching` degradation of
 * [BridgeUiLanguage.effective].
 */
class BrokenResourcesApplication : Application() {
    @Volatile var broken = false

    override fun getResources(): Resources =
        if (broken) throw IllegalStateException("resources unavailable") else super.getResources()
}
