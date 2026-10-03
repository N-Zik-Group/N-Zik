package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import it.fast4x.innertube.Innertube
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Startup restoration of [Innertube.useLoginForSearch] by [applyUseLoginForSearch]
 * (called from `MainApplication.onCreate`, next to the `useLoginForBrowse` restore —
 * the call-site wiring itself is fixed by [StartupSearchRestoreWiringTest]):
 *
 * - a persisted OFF is restored as OFF (matrix line "app restart");
 * - an absent preference falls back to the default ON.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform, with the plain [Application] so the app's heavy init
 * (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ApplyUseLoginForSearchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        Innertube.useLoginForSearch = true
    }

    @Test
    fun `a persisted OFF is restored at startup`() {
        context.preferences.edit { putBoolean(useLoginForSearchKey, false) }

        applyUseLoginForSearch(context)

        assertFalse(Innertube.useLoginForSearch)
    }

    @Test
    fun `an absent preference falls back to the default ON`() {
        applyUseLoginForSearch(context)

        assertTrue(Innertube.useLoginForSearch)
    }
}
