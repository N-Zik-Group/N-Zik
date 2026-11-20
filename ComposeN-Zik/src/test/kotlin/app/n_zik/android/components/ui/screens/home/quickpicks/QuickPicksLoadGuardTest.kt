package app.n_zik.android.components.ui.screens.home.quickpicks

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.HomePage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuickPicksLoadGuardTest {
    @Test
    fun `account changes detach cached home data and late results`() {
        val first = quickPicksAccountIdentity("SAPISID=test-account-a", true)
        val second = quickPicksAccountIdentity("SAPISID=test-account-b", true)
        val owner = QuickPicksAccountCache()
        val firstCache = owner.forAccount(first)
        firstCache.homePage.value = homeWithSections(20)
        firstCache.loaded.value = true

        assertSame(firstCache, owner.forAccount(first))
        val secondCache = owner.forAccount(second)
        firstCache.homePage.value = homeWithSections(30)

        assertNotSame(firstCache, secondCache)
        assertNull(secondCache.homePage.value)
        assertFalse(secondCache.loaded.value)
        assertNull(owner.forAccount(first).homePage.value)
    }

    @Test
    fun `partial refresh cannot displace a richer cached home`() {
        assertFalse(shouldReplaceCachedQuickPicksHome(homeWithSections(20), homeWithSections(4)))
        assertFalse(shouldReplaceCachedQuickPicksHome(homeWithSections(20), homeWithSections(0)))
        assertTrue(shouldReplaceCachedQuickPicksHome(homeWithSections(20), homeWithSections(16)))
        assertTrue(shouldReplaceCachedQuickPicksHome(homeWithSections(3), homeWithSections(4)))
    }

    private fun homeWithSections(count: Int) = HomePage(
        sections = List(count) { index ->
            val song = Innertube.SongItem(null, null, null, null, null).copy(
                info = Innertube.Info("Song $index", NavigationEndpoint.Endpoint.Watch(videoId = "song-$index")),
            )
            HomePage.Section("Section $index", null, null, null, listOf(song))
        },
        chips = null,
    )

    @Test
    fun `arriving songs replace loading slots in order`() {
        val initialSlots = visibleQuickPicksSlots(viewportWidth = 300f, itemWidth = 210f)
        assertEquals((0..5).toList(), quickPicksLoadingSlots(0, loading = true, initialSlots).toList())
        assertEquals((2..5).toList(), quickPicksLoadingSlots(2, loading = true, initialSlots).toList())
        assertTrue(quickPicksLoadingSlots(9, loading = true, initialSlots).isEmpty())
        assertTrue(quickPicksLoadingSlots(2, loading = false, initialSlots).isEmpty())
    }

    @Test
    fun `skip load when loaded flag true and home page data present`() {
        assertTrue(shouldSkipQuickPicksLoad(loadedData = true, homePagePresent = true))
    }

    @Test
    fun `reload when loaded flag true but home page data missing (stuck state)`() {
        assertFalse(shouldSkipQuickPicksLoad(loadedData = true, homePagePresent = false))
    }

    @Test
    fun `reload when loaded flag false even if home page data present`() {
        assertFalse(shouldSkipQuickPicksLoad(loadedData = false, homePagePresent = true))
    }

    @Test
    fun `reload when loaded flag false and home page data missing`() {
        assertFalse(shouldSkipQuickPicksLoad(loadedData = false, homePagePresent = false))
    }
}
