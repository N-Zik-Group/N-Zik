package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsDetailedSearchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun localized(locale: Locale): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(locale) },
    )

    @Test fun allEntriesHaveUniqueIdsAndReachableParentCards() {
        assertTrue(SettingsEntryTargets.isNotEmpty())
        assertTrue(EmbeddedSettingsEntryTargets.isNotEmpty())
        val entries = AllSettingsEntryTargets
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        entries.forEach { entry ->
            assertTrue(entry.id.isNotBlank())
            val parent = entry.parentCard()
            assertEquals(entry.tab, parent.tab)
            assertEquals(entry.cardTitleRes, parent.titleRes)
            assertTrue(OrganizedSettingsCategories.any { it.id == parent.categoryId() })
            assertTrue(context.getString(entry.labelRes).isNotBlank())
            entry.keywordRes.forEach { assertTrue(context.getString(it).isNotBlank()) }
        }
    }

    @Test fun localizedLabelAndDescriptionFindTheExactEntry() {
        val french = localized(Locale.FRENCH)
        val skipSilence = AllSettingsEntryTargets.single {
            it.tab == 0 && it.cardTitleRes == R.string.audio_effects && it.labelRes == R.string.skip_silence
        }
        assertTrue(searchSettingsEntries(french, french.getString(R.string.skip_silence)).contains(skipSilence))
        assertTrue(searchSettingsEntries(french, french.getString(R.string.skip_silent_parts_during_playback)).contains(skipSilence))
        val embedded = EmbeddedSettingsEntryTargets.single { it.labelRes == R.string.listen_together_server_url }
        assertTrue(searchSettingsEntries(french, french.getString(embedded.labelRes)).contains(embedded))
    }

    @Test fun searchFoldsCaseAndAccentsWithoutChangingStableIdentity() {
        val french = localized(Locale.FRENCH)
        val english = localized(Locale.ENGLISH)
        val target = EmbeddedSettingsEntryTargets.single { it.labelRes == R.string.enable_log_debug }
        assertNotEquals(english.getString(target.labelRes), french.getString(target.labelRes))
        val frenchResult = searchSettingsEntries(french, "DEBOGAGE").single { it.id == target.id }
        val englishResult = searchSettingsEntries(english, english.getString(target.labelRes)).single { it.id == target.id }
        assertEquals(englishResult.id, frenchResult.id)
        assertEquals(englishResult.parentCard(), frenchResult.parentCard())
    }

    @Test fun blankAndUnknownQueriesAreEmptyAndBroadMatchesNeverDuplicateIds() {
        assertTrue(searchSettingsEntries(context, "  ").isEmpty())
        assertTrue(searchSettingsEntries(context, "zzzz-unmatched-setting-945726").isEmpty())
        val results = searchSettingsEntries(localized(Locale.ENGLISH), "backup")
        assertFalse(results.isEmpty())
        assertEquals(results.size, results.map { it.id }.toSet().size)
    }
}
