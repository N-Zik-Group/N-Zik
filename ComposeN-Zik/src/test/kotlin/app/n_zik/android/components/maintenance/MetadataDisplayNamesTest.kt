package app.n_zik.android.components.maintenance

import app.n_zik.android.core.maintenance.DbCleanupLinkRecord
import app.n_zik.android.core.maintenance.DownloadRevalidationRecord
import app.n_zik.android.core.maintenance.MaintenanceDbCleanupState
import app.n_zik.android.core.maintenance.MaintenanceDownloadRevalidationState
import app.n_zik.android.core.maintenance.MaintenanceDownloadSelfHealState
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataDisplayNamesTest {
    @Test
    fun `cleanup rows clean song and artist labels without changing stored records`() {
        val link = DbCleanupLinkRecord("modified:e:Title: live", "modified:Artist")
        val state = MaintenanceDbCleanupState(0, 1, listOf(link))

        assertEquals(listOf(GroupLine("Title: live", "Artist")), dbCleanupLines(state))
        assertEquals("modified:e:Title: live", state.removedLinks.single().songTitle)
        assertEquals("modified:Artist", state.removedLinks.single().artistName)
    }

    @Test
    fun `download detail rows clean labels while retaining the exact diagnostic ids`() {
        val id = "modified:track-id"
        val record = DownloadRevalidationRecord(id, "modified:Artist - modified:Title: live")
        val revalidation = MaintenanceDownloadRevalidationState(0, 1, listOf(record))
        val selfHeal = MaintenanceDownloadSelfHealState(0, id, "modified:Title: live")

        assertEquals(listOf(GroupLine("Artist - Title: live", id)), downloadRevalidationLines(revalidation))
        assertEquals(listOf(GroupLine("Title: live", id)), downloadSelfHealLines(selfHeal))
        assertEquals("modified:Artist - modified:Title: live", record.title)
        assertEquals("modified:Title: live", selfHeal.title)
    }
}
