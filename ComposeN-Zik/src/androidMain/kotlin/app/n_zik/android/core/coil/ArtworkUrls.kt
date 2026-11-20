package app.n_zik.android.core.coil

import android.net.Uri
import androidx.core.net.toUri
import app.n_zik.innertube.models.Thumbnail

fun String.resize(width: Int? = null, height: Int? = null): String {
    if (width == null && height == null) return this

    if (contains("googleusercontent.com") || startsWith("https://yt3.ggpht.com")) {
        val w = width ?: height ?: 0
        val h = height ?: width ?: 0
        val s = width ?: height ?: 0

        if (contains("=")) {
            val lastEquals = lastIndexOf('=')
            val base = substring(0, lastEquals)
            val params = substring(lastEquals)
            val modParams = params
                .replace(Regex("([=-])w\\d+(?![0-9a-zA-Z])"), "$1w$w")
                .replace(Regex("([=-])h\\d+(?![0-9a-zA-Z])"), "$1h$h")
                .replace(Regex("([=-])s\\d+(?![0-9a-zA-Z])"), "$1s$s")
            return base + modParams
        } else {
            return replace(Regex("([=/-])w\\d+(?![0-9a-zA-Z])"), "$1w$w")
                .replace(Regex("([=/-])h\\d+(?![0-9a-zA-Z])"), "$1h$h")
                .replace(Regex("([=/-])s\\d+(?![0-9a-zA-Z])"), "$1s$s")
        }
    }

    return this
}

internal fun String.getYouTubeId(): String? {
    return try {
        when {
            contains("i.ytimg.com/vi/") -> substringAfter("/vi/").substringBefore("/")
            contains("i.ytimg.com/pl_c/") -> substringAfter("/pl_c/").substringBefore("/")
            contains("i.ytimg.com/podcasts_artwork/") -> substringAfter("/podcasts_artwork/").substringBefore("/")
            else -> null
        }?.takeIf { it.isNotEmpty() && it != this }
    } catch (e: Exception) { null }
}

internal const val ORIGINAL_SOURCE_SIZE = 0

fun String?.thumbnail(size: Int): String? {
    if (this == null) return this

    val original = size == ORIGINAL_SOURCE_SIZE
    val targetSize = if (original) ImageCacheFactory.NetworkQuality.HIGH.size else size

    // Threshold increased from 600 to 700 to ensure HQ cache usage (Learning Mode)
    // We use >= 700 because the MEDIUM size is now 700.
    val quality = if (targetSize >= 700) ImageCacheFactory.NetworkQuality.HIGH else ImageCacheFactory.NetworkQuality.LOW

    if (contains("i.ytimg.com/pl_c/") || contains("i.ytimg.com/podcasts_artwork/")) {
        val id = getYouTubeId()
        if (id != null) {
            // Absolute priority to the learned (HQ) version if it exists
            val learnedHigh = ImageCacheFactory.PlaylistThumbnailStore.getHighUrl(id)
            if (learnedHigh != null) {
                return learnedHigh
            }

            // [STRICT LEARNING MODE]
            // We only swap if there is no 'rs=' signature.
//
            // We manage mwEIC (Low) and mwEUC (Medium) towards mwEKC (High).
            if (quality == ImageCacheFactory.NetworkQuality.HIGH) {
                return if (!contains("rs=")) {
                    replace("mwEIC", "mwEKC")
                        .replace("mwEUC", "mwEKC")
                        .replace("mwESC", "mwEKC")
                } else {
                    // If we have a signed "Medium" (EUC) link, still try to see if we can "clean" it
                    // or if the store has better. (The store was already checked above via learnedHigh)
                    this
                }
            } else {
                val smallerUrl = ImageCacheFactory.PlaylistThumbnailStore.getLowUrl(id)
                if (smallerUrl != null && smallerUrl != this) {
                    return smallerUrl
                }
            }
        }
        return this
    }

    // i.ytimg: modify video thumbnails based on size (Un-signing)
    when {
        contains("i.ytimg.com/vi/") -> {
            val suffix = when {
                targetSize >= 700 -> "maxresdefault.jpg" // Forcer maxres si possible
                targetSize > 300 -> "sddefault.jpg"
                else -> "hqdefault.jpg"
            }
            // "Un-signing": Strip parameters and force suffix for best quality
            // Remove all sqp/rs parameters for videos as they limit resolution
            return replace(Regex("/[^/?]+\\.jpg(\\?.*)?$"), "/$suffix")
        }
        // Playlists and Podcasts (/pl_c/, /podcasts_artwork/) are usually signed
        // and highly sensitive to path changes. We leave them as is.
    }

    // googleusercontent & yt3.ggpht: modify resolution parameters safely
    if (contains("googleusercontent.com") || contains("yt3.ggpht.com")) {
        if (original) {
            val separator = lastIndexOf('=')
            if (separator > lastIndexOf('/') && substring(separator + 1).matches(Regex("[swh]\\d+(?:-[a-zA-Z0-9]+)*"))) {
                return substring(0, separator + 1) + "s0"
            }
            return this
        }
        if (contains("=")) {
            val lastEquals = lastIndexOf('=')
            val base = substring(0, lastEquals)
            val params = substring(lastEquals)
            val modParams = params
                .replace(Regex("([=-])w\\d+(?![0-9a-zA-Z])"), "$1w$size")
                .replace(Regex("([=-])h\\d+(?![0-9a-zA-Z])"), "$1h$size")
                .replace(Regex("([=-])s\\d+(?![0-9a-zA-Z])"), "$1s$size")
            return base + modParams
        } else {
            return replace(Regex("([=/-])w\\d+(?![0-9a-zA-Z])"), "$1w$size")
                .replace(Regex("([=/-])h\\d+(?![0-9a-zA-Z])"), "$1h$size")
                .replace(Regex("([=/-])s\\d+(?![0-9a-zA-Z])"), "$1s$size")
        }
    }

    return this
}

internal fun String?.getNextYouTubeFallback(): String? {
    if (this == null || !contains("i.ytimg.com/vi/")) return null
    return when {
        contains("maxresdefault.jpg") -> replace("maxresdefault.jpg", "sddefault.jpg")
        contains("sddefault.jpg") -> replace("sddefault.jpg", "hqdefault.jpg")
        contains("hqdefault.jpg") -> replace("hqdefault.jpg", "mqdefault.jpg")
        else -> null
    }
}

fun String?.thumbnail(): String? = this

fun Uri?.thumbnail(size: Int): Uri? = this?.toString()?.thumbnail(size)?.toUri()

fun Thumbnail.size(size: Int): String = url.thumbnail(size) ?: url

internal fun String.getYouTubeQualityScore(): Int {
    return when {
        contains("mwEKC") || contains("maxresdefault") || contains("hq720") -> 10
        contains("mwEHKC") -> 9
        contains("mwEUC") -> 5
        contains("mwEIC") -> 2
        contains("mwESC") -> 1
        else -> 0
    }
}

internal fun String.isYouTubeHighRes(): Boolean = getYouTubeQualityScore() >= 5
