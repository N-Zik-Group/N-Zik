package it.fast4x.innertube.utils

@Deprecated("Session is now managed via direct property assignment in MainApplication.onCreate(). This holder is unused.")
object YoutubePreferences {
    var preference: YoutubePreferenceItem? = null
}

data class YoutubePreferenceItem(
    var cookie: String?,
    var visitordata: String?,
    var dataSyncId: String?
)