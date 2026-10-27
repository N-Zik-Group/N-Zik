package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsEntrySearchIndexTest {
    @Test
    fun `repeated queries reuse normalized resources and keep stable rank ordering`() {
        val strings = mapOf(1 to "Other", 2 to "Préfix", 3 to "pré", 4 to "A pré", 5 to "pré second")
        val entries = listOf(
            SettingsEntryTarget("keyword", 0, 1, 1, listOf(3)),
            SettingsEntryTarget("prefix", 0, 1, 2),
            SettingsEntryTarget("exact", 0, 1, 3),
            SettingsEntryTarget("contains", 0, 1, 4),
            SettingsEntryTarget("prefix2", 0, 1, 5),
            SettingsEntryTarget("description", 0, 1, 1),
        )
        val resolved = mutableListOf<Int>()
        val index = SettingsEntrySearchIndex(entries, mapOf("description" to 3)) {
            resolved.add(it)
            strings.getValue(it)
        }
        val expected = listOf("exact", "prefix", "prefix2", "contains", "keyword", "description")
        repeat(10) {
            assertEquals(expected, index.search(" PRE ").map { it.id })
            assertEquals(emptyList<SettingsEntryTarget>(), index.search("missing"))
            assertEquals(emptyList<SettingsEntryTarget>(), index.search(" "))
        }
        assertEquals(strings.keys.sorted(), resolved.sorted())
    }

    @Test
    fun `cache reuses same resources and invalidates on configuration changes`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        ).resources
        val first = settingsEntrySearchIndex(english)
        assertSame(first, settingsEntrySearchIndex(english))
        val original = Configuration(english.configuration)
        try {
            val frenchConfig = Configuration(original).apply { setLocale(Locale.FRENCH) }
            @Suppress("DEPRECATION")
            english.updateConfiguration(frenchConfig, english.displayMetrics)
            val second = settingsEntrySearchIndex(english)
            assertNotSame(first, second)
            assertSame(second, settingsEntrySearchIndex(english))
            val target = EmbeddedSettingsEntryTargets.single { it.id.endsWith("enable_log_debug") }
            assertEquals(target, second.search("DEBOGAGE").single { it.id == target.id })
        } finally {
            @Suppress("DEPRECATION")
            english.updateConfiguration(original, english.displayMetrics)
        }
    }
}
