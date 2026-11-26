package app.n_zik.android.components.settings

import app.n_zik.android.components.settings.appearance.preview.ThemeStoreCreatorTarget
import app.n_zik.android.components.settings.appearance.preview.creatorProfileSettingsRoute
import app.n_zik.android.components.settings.appearance.preview.visibleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStoreCreatorTargetTest {
    @Test fun `signed out creator destinations bring GitHub access into view`() {
        listOf(
            ThemeStoreCreatorTarget.Profile,
            ThemeStoreCreatorTarget.PublicInformation,
            ThemeStoreCreatorTarget.PublishProfile,
            ThemeStoreCreatorTarget.Publish,
            ThemeStoreCreatorTarget.EditPublished,
            ThemeStoreCreatorTarget.MyThemes,
            ThemeStoreCreatorTarget.Submissions,
        ).forEach { target ->
            assertEquals(ThemeStoreCreatorTarget.PublishingAccess, target.visibleTarget(hasCreatorAccount = false))
        }
    }

    @Test fun `signed out local destinations and the GitHub card retain their own target`() {
        listOf(
            ThemeStoreCreatorTarget.PublishingAccess,
            ThemeStoreCreatorTarget.CreateLocal,
            ThemeStoreCreatorTarget.EditLocal,
        ).forEach { target ->
            assertEquals(target, target.visibleTarget(hasCreatorAccount = false))
        }
        assertNull(null.visibleTarget(hasCreatorAccount = false))
    }

    @Test fun `connected creators keep the requested destination`() {
        ThemeStoreCreatorTarget.entries.forEach { target ->
            assertEquals(target, target.visibleTarget(hasCreatorAccount = true))
        }
    }

    @Test fun `profile actions carry their exact target to the editor route`() {
        assertTrue(ThemeStoreCreatorTarget.PublicInformation.opensProfileEditor)
        assertTrue(ThemeStoreCreatorTarget.PublishProfile.opensProfileEditor)
        assertEquals("themeStoreCreatorSettings?target=public-information", creatorProfileSettingsRoute(ThemeStoreCreatorTarget.PublicInformation))
        assertEquals("themeStoreCreatorSettings?target=publish-profile", creatorProfileSettingsRoute(ThemeStoreCreatorTarget.PublishProfile))
    }
}
