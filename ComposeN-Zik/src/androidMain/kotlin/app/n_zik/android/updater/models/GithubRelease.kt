package app.n_zik.android.updater.models

import android.text.format.Formatter
import app.n_zik.android.appContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime

@Serializable
data class GithubRelease(
    val id: UInt,
    @SerialName("tag_name") val tagName: String,
    val name: String,
    val body: String,
    val prerelease: Boolean = false,
    @SerialName("assets") val builds: List<Build>
) {

    @Serializable
    data class Build(
        val id: UInt,
        val url: String,
        val name: String,
        val size: UInt,
        @SerialName("created_at") val createdAt: String,
        @SerialName("browser_download_url") val downloadUrl: String
    ) {
        val buildTime: ZonedDateTime by lazy {
            ZonedDateTime.parse( createdAt )
        }

        val readableSize: String
            // Don't remember with lazy because format mat be changed
            // when [appContext] is changed.
            get() = Formatter.formatShortFileSize( appContext(), this.size.toLong() )
    }
}

