package app.n_zik.android.components.search

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SearchHistoryStateTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun submittedQueriesAndPageSwitchesReachMountedConsumersImmediately() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().preferences
        val page = SearchHistoryStore.PAGE_LOCAL
        prefs.edit().clear().commit()
        lateinit var queries: State<List<String>>
        composeRule.setContent { queries = rememberSearchHistory(page, false) }
        composeRule.runOnIdle { SearchHistoryStore.record(prefs, page, "Saved query") }
        composeRule.runOnIdle {
            assertEquals(listOf("Saved query"), filterSearchHistory(queries.value, "saved"))
            prefs.edit().putBoolean(SearchHistoryStore.enabledKey(page), false).apply()
        }
        composeRule.runOnIdle {
            assertEquals(emptyList<String>(), queries.value)
            prefs.edit().putBoolean(SearchHistoryStore.enabledKey(page), true).apply()
        }
        composeRule.runOnIdle { assertEquals(listOf("Saved query"), queries.value) }
        composeRule.runOnIdle { SearchHistoryStore.clearAll(prefs) }
        composeRule.runOnIdle { assertEquals(emptyList<String>(), queries.value) }
    }
}
