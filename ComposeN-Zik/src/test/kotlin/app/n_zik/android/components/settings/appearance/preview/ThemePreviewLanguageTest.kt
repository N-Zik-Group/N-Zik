package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "fr", application = Application::class)
class ThemePreviewLanguageTest {
    @Test
    fun previewUsesEnglishWithoutChangingTheApplicationLanguage() {
        val application = RuntimeEnvironment.getApplication()
        val preview = Robolectric.buildActivity(ThemePreviewActivity::class.java).get()

        assertEquals("en", preview.resources.configuration.locales[0].language)
        assertEquals("fr", application.resources.configuration.locales[0].language)
    }
}
