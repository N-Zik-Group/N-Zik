package app.n_zik.android.components.maintenance

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The [passStatusText] formatting point (spec "Maintenance — état de l'app en un
 * regard"): every non-zero state part is rendered on its own, the parts are joined by
 * the shared " · " separator, the count is interpolated into the %d placeholder, and
 * the fixed "No data" / "Nothing to clean" sentences render without a count.
 *
 * JUnit 4 + [RobolectricTestRunner] + [createComposeRule] (vintage engine) — stringResource
 * needs a composition, so the plain-JVM mapping tests cannot exercise this point.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MaintenancePassStatusTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every part is rendered and joined by the separator`() {
        var text: String? = null
        composeRule.setContent {
            text = passStatusText(
                PassStatus(
                    listOf(
                        PassStatusPart(R.string.maintenance_pass_cleaned, 2),
                        PassStatusPart(R.string.maintenance_pass_skipped, 3),
                        PassStatusPart(R.string.maintenance_pass_deferred, 1),
                    ),
                ),
            )
        }
        val expected = listOf(
            context.getString(R.string.maintenance_pass_cleaned, 2),
            context.getString(R.string.maintenance_pass_skipped, 3),
            context.getString(R.string.maintenance_pass_deferred, 1),
        ).joinToString(" · ")
        assertEquals("every part must be rendered, joined by the shared separator", expected, text)
    }

    @Test
    fun `the count is interpolated into the placeholder`() {
        var text: String? = null
        composeRule.setContent {
            text = passStatusText(PassStatus(listOf(PassStatusPart(R.string.maintenance_pass_cleaned, 1))))
        }
        assertEquals(
            "the count must be interpolated into the %d placeholder",
            context.getString(R.string.maintenance_pass_cleaned, 1),
            text,
        )
    }

    @Test
    fun `the fixed sentences render without a count`() {
        var noData: String? = null
        var nothingToClean: String? = null
        composeRule.setContent {
            noData = passStatusText(PassStatus.NoData)
            nothingToClean = passStatusText(PassStatus.NothingToClean)
        }
        assertEquals("the No data sentence must render without a count", context.getString(R.string.maintenance_no_data), noData)
        assertEquals(
            "the Nothing to clean sentence must render without a count",
            context.getString(R.string.maintenance_pass_clean),
            nothingToClean,
        )
    }
}
