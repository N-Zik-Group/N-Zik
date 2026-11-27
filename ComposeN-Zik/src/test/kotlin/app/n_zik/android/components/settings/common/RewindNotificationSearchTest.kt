package app.n_zik.android.components.settings.common

import app.n_zik.android.R
import app.n_zik.android.utils.DataStoreUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RewindNotificationSearchTest {
    @Test
    fun `hidden notification card and heading name each setting that can show them`() {
        val expected = listOf(
            R.string.rewind_settings_monthly,
            R.string.rewind_settings_yearly,
            R.string.rewind_settings_playlists_monthly,
            R.string.rewind_settings_playlists_yearly,
        )
        for (target in listOf(RewindNotificationSectionTarget, "embedded.rewind.section.alerts")) {
            assertEquals(expected, unavailableRewindNotificationParents(target) { it == DataStoreUtils.KEY_REWIND_ENABLED })
        }
    }

    @Test
    fun `notification group does not suggest parents when disabled globally or already visible`() {
        val target = RewindNotificationSectionTarget
        assertTrue(unavailableRewindNotificationParents(target) { false }.isEmpty())
        val enabled = setOf(DataStoreUtils.KEY_REWIND_ENABLED, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED)
        assertTrue(unavailableRewindNotificationParents(target, enabled::contains).isEmpty())
    }
}
