package app.n_zik.android.core.database

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.ArtistConjunctions

/**
 * Resolves the display names of a YTM track list's author entries.
 *
 * One YTM author entry = one artist (one channel): the entry name is kept
 * whole and is NEVER split on "&" / "," / conjunctions — "Bigflo & OLi" is
 * one duo with one channel, not two artists. The legacy `parseArtists()`
 * split single entry names into parts ("Bigflo" + "OLi"), which lost the
 * channel identity and fed "Bigflo, OLi" to every live YTM list display
 * (artist page, online search, album/playlist pages — `SongItem.asSong` /
 * `VideoItem.asSong`).
 *
 * Entry rules match `Database.upsert` (v6, channel-identity mapping):
 * trim + NBSP-normalize, skip pure-separator and standalone-conjunction
 * entries, so the displayed `artistsText` converges with the stored one.
 */
fun List<Innertube.Info<*>?>?.artistEntryNames(): List<String> =
    this.orEmpty().mapNotNull { author ->
        val name = author?.name?.trim()?.replace('\u00a0', ' ')
        if (name.isNullOrBlank()) return@mapNotNull null
        if (name.matches(Regex("^[,&]+$"))) return@mapNotNull null
        if (ArtistConjunctions.conjunctions.any { name.equals(it, ignoreCase = true) }) return@mapNotNull null
        name
    }
