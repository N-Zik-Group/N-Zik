package app.n_zik.android.core.database

import android.os.Parcel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import app.n_zik.android.musicbrainz.models.ExternalLink
import kotlinx.serialization.json.Json

@TypeConverters
object Converters {

    private val externalLinksJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @TypeConverter
    @JvmStatic
    fun stringToExternalLinks(value: String): List<ExternalLink> {
        return runCatching {
            externalLinksJson.decodeFromString(externalLinksListSerializer, value)
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    @JvmStatic
    fun externalLinksToString(externalLinks: List<ExternalLink>): String {
        return externalLinksJson.encodeToString(externalLinksListSerializer, externalLinks)
    }

    private val externalLinksListSerializer = kotlinx.serialization.builtins.ListSerializer(ExternalLink.serializer())

    @TypeConverter
    @JvmStatic
    fun fromString(stringListString: String): List<String> {
        return stringListString.split(",").map { it }
    }

    @TypeConverter
    @JvmStatic
    fun toString(stringList: List<String>): String {
        return stringList.joinToString(separator = ",")
    }

    @TypeConverter
    @JvmStatic
    @UnstableApi
    fun mediaItemFromByteArray(value: ByteArray?): MediaItem? {
        return value?.let { byteArray ->
            runCatching {
                val parcel = Parcel.obtain()
                parcel.unmarshall(byteArray, 0, byteArray.size)
                parcel.setDataPosition(0)
                val bundle = parcel.readBundle(MediaItem::class.java.classLoader)
                parcel.recycle()

                bundle?.let(MediaItem::fromBundle)
            }.getOrNull()
        }
    }

    @TypeConverter
    @JvmStatic
    @UnstableApi
    fun mediaItemToByteArray(mediaItem: MediaItem?): ByteArray? {
        // artworkData contains the raw image bytes. If the image is very large (e.g. custom cover),
        // Android converts it into a Binder object (ashmem) when toBundle() is called.
//
        // Parcel.marshall() does not support Binder objects and crashes with RuntimeException.
        // We purge artworkData before saving, as the database does not need to store the raw image.
//
        // Optimization: only rebuild if artworkData is not null to avoid massive GC pauses.
        val sanitizedMediaItem = if (mediaItem?.mediaMetadata?.artworkData != null) {
            mediaItem.buildUpon().setMediaMetadata(
                mediaItem.mediaMetadata.buildUpon()
                    .setArtworkData(null, null)
                    .build()
            ).build()
        } else {
            mediaItem
        }

        return sanitizedMediaItem?.toBundle()?.let { persistableBundle ->
            val parcel = Parcel.obtain()
            parcel.writeBundle(persistableBundle)
            val bytes = parcel.marshall()
            parcel.recycle()

            bytes
        }
    }
}
