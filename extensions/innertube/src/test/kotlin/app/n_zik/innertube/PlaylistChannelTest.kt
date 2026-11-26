package app.n_zik.innertube

import app.n_zik.innertube.models.MusicResponsiveHeaderRenderer
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.models.Runs
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaylistChannelTest {
    @Test
    fun `linked owner is retained while plain author text and other links are ignored`() {
        val header = Runs(listOf(
            Runs.Run("Playlist", null),
            linkedRun("Unrelated playlist", "VLPL123"),
            linkedRun("Owner", "UCowner"),
        ))

        assertEquals("UCowner", YtMusic.playlistChannel(header)?.endpoint?.browseId)
        assertEquals("Owner", YtMusic.playlistChannel(header)?.name)
        assertNull(YtMusic.playlistChannel(Runs(listOf(Runs.Run("Name only", null)))))
    }

    @Test
    fun `an ambiguous header does not guess a playlist owner`() {
        val header = Runs(listOf(
            linkedRun("First channel", "UCfirst"),
            linkedRun("Second channel", "UCsecond"),
        ))

        assertNull(YtMusic.playlistChannel(header))
    }

    @Test
    fun `responsive playlist header reads the linked owner from the facepile`() {
        val header = Json { explicitNulls = false; ignoreUnknownKeys = true }
            .decodeFromString<MusicResponsiveHeaderRenderer>(
                """
                {
                    "title": {"runs": [{"text": "Example playlist"}]},
                    "subtitle": {"runs": [{"text": "Playlist"}, {"text": "2026"}]},
                    "facepile": {"avatarStackViewModel": {
                        "text": {"content": "leebeldadjr"},
                        "rendererContext": {"commandContext": {"onTap": {"innertubeCommand": {
                            "browseEndpoint": {"browseId": "UCsseSBZAuQVrdhlH_ybPijQ"}
                        }}}}
                    }}
                }
                """.trimIndent()
            )

        val owner = YtMusic.playlistChannel(header)
        assertEquals("leebeldadjr", owner?.name)
        assertEquals("UCsseSBZAuQVrdhlH_ybPijQ", owner?.endpoint?.browseId)
    }

    private fun linkedRun(name: String, browseId: String) = Runs.Run(
        text = name,
        navigationEndpoint = NavigationEndpoint(
            watchEndpoint = null,
            watchPlaylistEndpoint = null,
            browseEndpoint = NavigationEndpoint.Endpoint.Browse(browseId = browseId),
            searchEndpoint = null,
        ),
    )
}
