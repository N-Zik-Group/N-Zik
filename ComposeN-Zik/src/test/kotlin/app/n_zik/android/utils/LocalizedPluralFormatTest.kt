package app.n_zik.android.utils

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalizedPluralFormatTest {
    private fun localized(locale: Locale): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(locale) })
    }

    @Test
    fun `English uses the new singular and plural source forms`() {
        val resources = localized(Locale.ENGLISH).resources
        assertEquals("1 recommended song", resources.getQuantityString(R.plurals.recommendations_quick_picks_song_count, 1, 1))
        assertEquals("3 recommended songs", resources.getQuantityString(R.plurals.recommendations_quick_picks_song_count, 3, 3))
    }
}
