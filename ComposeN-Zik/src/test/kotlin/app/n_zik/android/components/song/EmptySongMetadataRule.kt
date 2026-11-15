package app.n_zik.android.components.song

import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import org.junit.rules.ExternalResource

/** Supplies the persisted metadata flow for real song rows in UI-only tests. */
class EmptySongMetadataRule : ExternalResource() {
    private var installed = false

    fun stub() {
        val songTable = mockk<SongTable>()
        every { songTable.findById(any()) } returns flowOf(null)
        mockkObject(Database)
        installed = true
        every { Database.songTable } returns songTable
    }

    override fun after() {
        if (installed) unmockkObject(Database)
    }
}
