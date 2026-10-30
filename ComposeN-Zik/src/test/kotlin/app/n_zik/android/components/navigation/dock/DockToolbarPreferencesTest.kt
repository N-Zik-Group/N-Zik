package app.n_zik.android.components.navigation.dock

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DockToolbarPreferencesTest {
    private val available = listOf("songs_icon", "artists_icon", "albums_icon", "library_icon")

    @Test fun `empty or malformed order preserves actual registered metadata order`() {
        listOf("", "broken", "{}", "[").forEach { serialized ->
            assertEquals(available, DockToolbarPreferences.orderedIds(available, serialized))
        }
    }

    @Test fun `custom order sorts registered ids and appends newly registered tabs`() {
        assertEquals(listOf("library_icon", "songs_icon", "artists_icon", "albums_icon"),
            DockToolbarPreferences.orderedIds(available, "[\"library_icon\",\"songs_icon\"]"))
    }

    @Test fun `removed and unknown ids do not create tabs or suppress remaining registered tabs`() {
        assertEquals(listOf("artists_icon", "songs_icon", "albums_icon", "library_icon"),
            DockToolbarPreferences.orderedIds(available, "[\"removed_icon\",\"artists_icon\",\"unknown_route\"]"))
    }

    @Test fun `duplicate stored and registered ids preserve first position and stable uniqueness`() {
        assertEquals(listOf("albums_icon", "songs_icon", "artists_icon", "library_icon"),
            DockToolbarPreferences.orderedIds(available + available,
                "[\"albums_icon\",\"albums_icon\",\"songs_icon\",\"songs_icon\"]"))
    }

    @Test fun `serialization persists stable ids with escaping and first-occurrence order`() {
        val ids = listOf("songs_icon", "quote\"id", "slash\\id", "songs_icon")
        val serialized = DockToolbarPreferences.serializeOrder(ids)
        assertEquals(ids.distinct(), DockToolbarPreferences.parseOrder(serialized))
    }

    @Test fun `empty availability ignores every stored id`() {
        assertEquals(emptyList<String>(), DockToolbarPreferences.orderedIds(emptyList(), "[\"songs_icon\"]"))
    }
}
