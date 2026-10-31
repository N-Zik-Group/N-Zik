package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassGlobalIndicationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `global styling preserves null exclusions and native indication identity when disabled`() {
        val enabled = mutableStateOf(false)
        val native = ripple(bounded = false)
        var absent: Indication? = native
        var actual: Indication? = null
        var supplied: Indication? = null
        compose.setContent {
            GlassRippleProvider(GlassSettings(enabled = enabled.value)) {
                supplied = LocalIndication.current
                absent = glassGlobalIndication(null)
                actual = glassGlobalIndication(native)
            }
        }
        for (active in listOf(false, true, false)) {
            compose.runOnIdle { enabled.value = active }
            compose.waitForIdle()
            compose.runOnIdle {
                assertNull("An explicit no-ripple choice stays excluded", absent)
                assertSame("Native feedback is restyled only while Glass is enabled", if (active) supplied else native, actual)
            }
        }
    }
}
