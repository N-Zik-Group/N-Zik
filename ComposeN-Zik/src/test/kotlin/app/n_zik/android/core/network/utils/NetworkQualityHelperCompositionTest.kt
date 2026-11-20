package app.n_zik.android.core.network.utils

import java.util.concurrent.atomic.AtomicInteger

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NetworkQualityHelperCompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `unrelated recomposition reuses the synchronous network read`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val uiReads = AtomicInteger()
        val context = object : ContextWrapper(application) {
            override fun getSystemService(name: String): Any? {
                if (name == CONNECTIVITY_SERVICE && Looper.myLooper() == Looper.getMainLooper()) {
                    uiReads.incrementAndGet()
                }
                return super.getSystemService(name)
            }
        }
        val counter = mutableIntStateOf(0)

        compose.setContent {
            val revision = counter.intValue
            val online = NetworkQualityHelper.isNetworkAvailableComposable(context).value
            BasicText("$revision / $online")
        }

        compose.onNodeWithText("0 / ", substring = true).assertIsDisplayed()
        assertEquals(1, uiReads.get())
        compose.runOnIdle { counter.intValue = 1 }
        compose.onNodeWithText("1 / ", substring = true).assertIsDisplayed()

        assertEquals(1, uiReads.get())
    }
}
