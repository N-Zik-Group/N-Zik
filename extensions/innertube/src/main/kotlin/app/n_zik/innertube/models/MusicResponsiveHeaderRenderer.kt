package app.n_zik.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicResponsiveHeaderRenderer(
    val thumbnail: ThumbnailRenderer?,
    val buttons: List<Button>? = null,
    val title: Runs,
    val subtitle: Runs,
    val secondSubtitle: Runs?,
    val straplineTextOne: Runs?,
    val description: Description?,
    val facepile: Facepile? = null,
) {
    @Serializable
    data class Facepile(val avatarStackViewModel: AvatarStack? = null) {
        @Serializable
        data class AvatarStack(
            val text: Text? = null,
            val rendererContext: RendererContext? = null,
        )

        @Serializable
        data class Text(val content: String? = null)

        @Serializable
        data class RendererContext(val commandContext: CommandContext? = null)

        @Serializable
        data class CommandContext(val onTap: Tap? = null)

        @Serializable
        data class Tap(val innertubeCommand: NavigationEndpoint? = null)
    }

    @Serializable
    data class Description(
        val musicDescriptionShelfRenderer: SectionListRenderer.Content.MusicDescriptionShelfRenderer?,
    )

    @Serializable
    data class Button(
        val musicPlayButtonRenderer: MusicPlayButtonRenderer?,
        val menuRenderer: Menu.MenuRenderer?
    ) {
        @Serializable
        data class MusicPlayButtonRenderer(
            val playNavigationEndpoint: NavigationEndpoint?,
        )
    }
}
