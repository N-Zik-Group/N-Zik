package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import app.it.fast4x.rimusic.utils.preferences
import org.json.JSONArray

/**
 * Resolves the display order of an automotive home category from the in-app
 * settings: the phone's Home*SettingsDialog stores a JSON array of category
 * ids under its order key (homeSongsOrderKey, homeAlbumsOrderKey,
 * homeArtistsOrderKey, homePlaylistsOrderKey). Unknown or corrupted stored
 * values fall back to [defaultOrder]; ids missing from the stored order are
 * appended in default order — the same parsing rules as the phone dialogs.
 */
internal fun homeCategoryOrder( context: Context, orderKey: String, defaultOrder: List<String> ): List<String> {
    val stored = try { context.preferences.getString( orderKey, "" ) } catch ( e: Exception ) { null }
    if ( stored.isNullOrBlank() ) return defaultOrder
    return try {
        val arr = JSONArray( stored )
        val parsed = ( 0 until arr.length() ).map { arr.getString( it ) }
        val ordered = parsed.filter { it in defaultOrder }.toMutableList()
        for ( id in defaultOrder ) {
            if ( id !in ordered ) ordered.add( id )
        }
        ordered
    } catch (_: Exception) { defaultOrder }
}
