package app.n_zik.android.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StringResourceIdentityTest {
    @Test
    fun `renamed resource names resolve to the identities published before migration`() {
        assertEquals("songs_shuffled", StringResourceIdentity.legacyName("player_shuffled_song_count_format"))
        assertEquals("app_behavior", StringResourceIdentity.legacyName("settings_app_behavior_title"))
        assertEquals("theme", StringResourceIdentity.legacyName("common_color_source_theme"))
        assertEquals("swipe_to_action", StringResourceIdentity.legacyName("settings_swipe_actions_title"))
    }

    @Test
    fun `unchanged legacy unknown and ambiguous merged names pass through`() {
        listOf("audio_effects", "songs_shuffled", "new_resource_name", "common_action_cancel", "")
            .forEach { assertEquals(it, StringResourceIdentity.legacyName(it)) }
    }
}
