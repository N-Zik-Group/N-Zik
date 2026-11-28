package app.n_zik.android.playback.services.automotive.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPlaylistVisitsTest {
    @Test
    fun `only first source subscription or reopening starts a refresh`() {
        val visits = AutoPlaylistVisits<String>()
        assertNull(visits.enter("car", "playlist/42/on_device"))
        assertNull(visits.enter("car", "playlist/VLRemote"))
        assertEquals(42L, visits.enter("car", "playlist/42"))
        assertNull(visits.enter("car", "playlist/42"))
        assertNull(visits.enter("car", "playlist/42/all"))
        assertEquals(42L, visits.enter("other", "playlist/42"))

        visits.leave("car", "playlist/42")
        assertFalse(visits.contains("car", "playlist/42"))
        assertTrue(visits.contains("car", "playlist/42/all"))
        assertNull(visits.enter("car", "playlist/42"))
        visits.leave("car")
        assertEquals(42L, visits.enter("car", "playlist/42/all"))
        assertNull(visits.enter("other", "playlist/42"))
    }
}
