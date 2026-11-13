package app.n_zik.innertube.utils


import app.n_zik.innertube.models.MusicResponsiveListItemRenderer
import app.n_zik.innertube.models.Runs

object PageHelper {
    fun extractRuns(columns: List<MusicResponsiveListItemRenderer.FlexColumn>, typeLike: String): List<Runs.Run> {
        val filteredRuns = mutableListOf<Runs.Run>()
        for (column in columns) {
            val runs = column.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                ?: continue

            for (run in runs) {
                val typeStr = run.navigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    ?: run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                    ?: continue

                if (typeLike in typeStr) {
                    filteredRuns.add(run)
                }
            }
        }
        return filteredRuns
    }
}