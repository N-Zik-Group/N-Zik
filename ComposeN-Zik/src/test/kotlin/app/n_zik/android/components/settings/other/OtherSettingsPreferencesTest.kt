package app.n_zik.android.components.settings.other

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.defaultFolderKey
import app.it.fast4x.rimusic.utils.isKeepScreenOnEnabledKey
import app.it.fast4x.rimusic.utils.isProxyEnabledKey
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.proxyHostnameKey
import app.it.fast4x.rimusic.utils.proxyModeKey
import app.it.fast4x.rimusic.utils.proxyPortKey
import app.it.fast4x.rimusic.utils.proxyUsernameKey
import app.it.fast4x.rimusic.utils.regionOverrideKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showFoldersOnDeviceKey
import it.fast4x.innertube.Innertube
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OtherSettingsPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var originalRegion = ""
    private var originalRegionActive = false
    private val expected = mapOf<String, Any>(
        isProxyEnabledKey to false,
        proxyHostnameKey to "",
        proxyPortKey to 1080,
        proxyModeKey to Proxy.Type.HTTP.name,
        regionOverrideKey to "",
        defaultFolderKey to "/",
        isKeepScreenOnEnabledKey to false,
        showFoldersOnDeviceKey to true,
        parentalControlEnabledKey to false,
        logDebugEnabledKey to false,
    )

    @Before fun setUp() {
        originalRegion = Innertube.regionOverride
        originalRegionActive = Innertube.regionOverrideActive
        setActiveProfile("other-settings-test", context)
        context.preferences.edit().clear().commit()
    }

    @After fun tearDown() {
        Innertube.regionOverrideActive = false
        Innertube.regionOverride = originalRegion
        Innertube.regionOverrideActive = originalRegionActive
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
    }

    @Test fun `reset restores region and preserves retired preferences and proxy credentials`() {
        val retained = mapOf(proxyUsernameKey to "retained-user", "proxyPasswordEncrypted" to "retained-encrypted", "unrelated" to "keep")
        context.preferences.edit().apply {
            retained.forEach { (key, value) -> putString(key, value) }
            putString(regionOverrideKey, "FR")
            putBoolean("extraspace", true)
            putBoolean(isProxyEnabledKey, true)
            putInt(proxyPortKey, 9999)
            putBoolean(showFoldersOnDeviceKey, false)
        }.commit()
        Innertube.regionOverride = "FR"
        Innertube.regionOverrideActive = true
        applyOtherDefaults(context)
        assertEquals("", Innertube.regionOverride)
        assertFalse(Innertube.regionOverrideActive)
        assertEquals(expected + retained + ("extraspace" to true), context.preferences.all)
        context.preferences.edit().putInt(proxyPortKey, 42).commit()
        Innertube.regionOverride = "FR"
        Innertube.regionOverrideActive = true
        applyOtherDefaults(context)
        assertEquals("", Innertube.regionOverride)
        assertFalse(Innertube.regionOverrideActive)
        assertEquals(expected + retained + ("extraspace" to true), context.preferences.all)
    }

    @Test fun `reset is scoped to the active profile`() {
        setActiveProfile("other-settings-untouched", context)
        context.preferences.edit().clear().putInt(proxyPortKey, 8123).commit()
        setActiveProfile("other-settings-test", context)
        applyOtherDefaults(context)
        assertEquals(expected, context.preferences.all)
        setActiveProfile("other-settings-untouched", context)
        assertEquals(mapOf(proxyPortKey to 8123), context.preferences.all)
        context.preferences.edit().clear().commit()
        setActiveProfile("other-settings-test", context)
    }

    @Test fun `inventory keeps existing order and all focus aliases without accepting unknown focus`() {
        assertEquals(listOf("other.on-device", "other.service-lifetime", "other.proxy", "other.parental-control", "other.maintenance", "other.extensions", "other.rescue", "other.reset"), OtherSettingTargets.cards)
        assertEquals(OtherSettingTargets.preferenceKeys.size, OtherSettingTargets.preferenceKeys.toSet().size)
        assertEquals(OtherSettingTargets.actions.size, OtherSettingTargets.actions.toSet().size)
        assertEquals(mapOf("debug" to "other.maintenance", "maintenance" to "other.maintenance", "extensions" to "other.extensions", "rescue" to "other.rescue"), OtherSettingTargets.focusAliases)
        assertNull(OtherSettingTargets.focusAliases["unknown"])
    }
}
