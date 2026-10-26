package app.n_zik.android.components.settings.network

/** Stable local inventory; global search activation remains separate. */
internal object NetworkSettingTargets {
    val cards = listOf(
        NetworkCardTargets("network.card.server", emptyList(), listOf("bridgeServer"), "pcserver"),
        NetworkCardTargets("network.card.status", emptyList(), listOf("image-status", "audio-status", "download-status")),
        NetworkCardTargets("network.card.quality", listOf("audioQualityFormat", "imageQualityFormat", "downloadQualityFormat"), listOf("update-downloads", "restart-player")),
        NetworkCardTargets("network.card.connection", listOf("isConnectionMeteredEnabled")),
        NetworkCardTargets("network.card.offline", listOf("fullOfflineModeEnabled"), focusId = "offline"),
        NetworkCardTargets("network.card.download", listOf("autoDownloadSong", "autoDownloadSongWhenLiked", "autoDownloadSongWhenAlbumBookmarked")),
        NetworkCardTargets("network.card.reset", emptyList(), listOf("applyNetworkDefaults")),
    )
    val ids = cards.flatMap { card ->
        listOf(card.id) + card.preferenceKeys.map { "${card.id}.setting.$it" } +
            card.actions.map { "${card.id}.action.$it" }
    }
}

internal data class NetworkCardTargets(
    val id: String,
    val preferenceKeys: List<String>,
    val actions: List<String> = emptyList(),
    val focusId: String? = null,
)
