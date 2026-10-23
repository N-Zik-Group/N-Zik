package app.n_zik.android.bridge

import android.content.SharedPreferences
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import kotlinx.serialization.Serializable

/**
 * `GET /api/v1/ui/settings` (contract §10.5, since 1.10.0, feature `ui.settings`): the phone's
 * Top N of its Top tab. Since 1.11.0 the payload carries only this setting (the 19 appearance
 * settings the client PC stopped consuming are no longer served — the phone's own screens read
 * their preferences directly).
 */
@Serializable
internal data class UiSettingsDto(
    /** The "Top N" the phone shows: its `MaxTopPlaylistItems.toInt(custom)`; `null` = unlimited. */
    val topN: Int? = 10,
)

/** Reads [UiSettingsDto] from the active profile's preferences, with the phone's defaults. */
internal object BridgeUiSettings {

    /** Minimal read view of the preferences (the test seam). */
    interface Source {
        fun string(key: String): String?
        fun int(key: String, default: Int): Int
    }

    fun of(preferences: SharedPreferences): Source = object : Source {
        override fun string(key: String): String? = runCatching { preferences.getString(key, null) }.getOrNull()
        override fun int(key: String, default: Int) = runCatching { preferences.getInt(key, default) }.getOrDefault(default)
    }

    fun read(source: Source): UiSettingsDto {
        // Both preferences are only read to compute the effective Top N: the wire carries `topN` alone.
        //

        // The fallback is the phone's own default selection, so a default change on the phone
        // propagates here without a silent desync (every other read site uses the same enum default)
        val maxItems = source.string(MaxTopPlaylistItemsKey) ?: MaxTopPlaylistItems.`10`.name
        val custom = source.int(MaxTopPlaylistItemsCustomValueKey, 10)
        return UiSettingsDto(topN = topN(maxItems, custom))
    }

    /** The phone's `MaxTopPlaylistItems.toInt(customValue)`; `null` for `Unlimited`, `10` for an unknown name. */
    fun topN(name: String, custom: Int): Int? = when (name) {
        "Unlimited" -> null
        "Custom" -> custom.coerceAtLeast(1)
        else -> name.toIntOrNull() ?: 10
    }
}
