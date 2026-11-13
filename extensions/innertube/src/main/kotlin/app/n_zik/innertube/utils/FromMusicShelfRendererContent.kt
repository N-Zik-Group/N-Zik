package app.n_zik.innertube.utils

import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.MusicShelfRenderer
import app.n_zik.innertube.models.NavigationEndpoint

fun Innertube.SongItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.SongItem? {
    val (mainRuns, otherRuns) = content.runs

    // Possible configurations:
    // "song" • author(s) • album • duration
    // "song" • author(s) • duration
    // author(s) • album • duration
    // author(s) • duration

    val album: Innertube.Info<NavigationEndpoint.Endpoint.Browse>? = otherRuns
        .getOrNull(otherRuns.lastIndex - 1)
        ?.firstOrNull()
        ?.takeIf { run ->
            run
                .navigationEndpoint
                ?.browseEndpoint
                ?.type == "MUSIC_PAGE_TYPE_ALBUM"
        }
        ?.let { Innertube.Info(it.text, it.navigationEndpoint?.browseEndpoint) }

    val isExplicit = content.musicResponsiveListItemRenderer
                                      ?.badges
                                      ?.any {
                                          it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                      } ?: false

    return Innertube.SongItem(
        info = mainRuns
            .firstOrNull()
            ?.let { Innertube.Info(it.text, it.navigationEndpoint?.watchEndpoint) },
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - if (album == null) 1 else 2)
            ?.map { Innertube.Info(it.text, it.navigationEndpoint?.browseEndpoint) },
        album = album,
        durationText = otherRuns
            .lastOrNull()
            ?.firstOrNull()?.text,
        thumbnail = content
            .thumbnail,
        explicit = isExplicit
    ).takeIf { it.info?.endpoint?.videoId != null }
}

fun Innertube.VideoItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.VideoItem? {
    val (mainRuns, otherRuns) = content.runs

    val linkedAuthors = otherRuns.filter { runs ->
        runs.any { it.navigationEndpoint?.browseEndpoint != null }
    }.flatten().map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it.text, it.navigationEndpoint?.browseEndpoint) }

    val duration = otherRuns.flatten()
        .find { it.text?.matches(Regex("\\d+:\\d+")) == true }
        ?.text

    val views = otherRuns.flatten()
        .find { 
            val text = it.text ?: ""
            (text.any { it.isDigit() } && !text.contains(":")) && !text.matches(Regex("\\d{4}"))
        }?.text

    val fallbackAuthor = if (linkedAuthors.isEmpty()) {
        val nonDigitNonYear = otherRuns.flatten().filter { run ->
            val text = run.text ?: ""
            text.isNotBlank() && text != duration && text != views && !text.any { it.isDigit() }
        }
        // Take the last non-digit segment as the fallback author (skips leading type labels)
        nonDigitNonYear.lastOrNull()?.let { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it.text, null) }
    } else null

    return Innertube.VideoItem(
        info = mainRuns
            .firstOrNull()
            ?.let { Innertube.Info(it.text, it.navigationEndpoint?.watchEndpoint) },
        authors = linkedAuthors.ifEmpty { fallbackAuthor?.let { listOf(it) } },
        viewsText = views,
        durationText = duration,
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.videoId != null }
}

fun Innertube.AlbumItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.AlbumItem? {
    val (mainRuns, otherRuns) = content.runs

    val linkedAuthors = otherRuns.filter { runs ->
        runs.any { it.navigationEndpoint?.browseEndpoint != null }
    }.flatten().map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it.text, it.navigationEndpoint?.browseEndpoint) }

    val year = otherRuns.flatten()
        .find { it.text?.matches(Regex("\\d{4}")) == true }
        ?.text

    val fallbackAuthors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>> = if (linkedAuthors.isEmpty()) {
        val nonDigitNonYear = otherRuns.flatten().filter { run ->
            val text = run.text ?: ""
            text.isNotBlank() && text != year && !text.any { it.isDigit() }
        }
        // Skip the first segment (likely type label like "Album", "Playlist") IF there are multiple
        if (nonDigitNonYear.size > 1) {
            nonDigitNonYear.drop(1).map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it.text, null) }
        } else {
            nonDigitNonYear.map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it.text, null) }
        }
    } else emptyList()

    return Innertube.AlbumItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        authors = linkedAuthors.ifEmpty { fallbackAuthors.ifEmpty { null } },
        year = year,
        songCount = null,
        description = otherRuns.filter { runs ->
            val isAuthor = runs.any { it.navigationEndpoint?.browseEndpoint != null }
            val isYear = runs.size == 1 && runs.first().text?.matches(Regex("\\d{4}")) == true
            val isFallbackAuthor = fallbackAuthors.any { fallback -> runs.any { it.text == fallback.name } }
            val hasDigits = runs.any { it.text?.any { char -> char.isDigit() } == true }
            !isAuthor && !isYear && !isFallbackAuthor && hasDigits
        }.flatten().joinToString(" • ") { it.text ?: "" }.takeIf { it.isNotBlank() },
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.ArtistItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.ArtistItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.ArtistItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        subscribersCountText = null,
        songCount = null,
        description = otherRuns.filter { runs ->
            runs.any { it.text?.any { char -> char.isDigit() } == true }
        }.flatten().joinToString(" • ") { it.text ?: "" }.takeIf { it.isNotBlank() },
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.PlaylistItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.PlaylistItem? {
    val (mainRuns, otherRuns) = content.runs

    val linkedChannel = otherRuns.filter { runs ->
        runs.any { it.navigationEndpoint?.browseEndpoint != null }
    }.flatten().map { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it.text, it.navigationEndpoint?.browseEndpoint) }.firstOrNull()

    val fallbackChannel = if (linkedChannel == null) {
        val nonDigit = otherRuns.flatten().filter { run ->
            val text = run.text ?: ""
            text.isNotBlank() && !text.any { it.isDigit() }
        }
        // Skip first label if multiple exist (to skip "Playlist", "Podcast" types)
        if (nonDigit.size > 1) {
            nonDigit.drop(1).firstOrNull()?.let { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it.text, null) }
        } else {
            nonDigit.firstOrNull()?.let { Innertube.Info<NavigationEndpoint.Endpoint.Browse>(it.text, null) }
        }
    } else null

    return Innertube.PlaylistItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        channel = linkedChannel ?: fallbackChannel,
        songCount = null,
        thumbnail = content
            .thumbnail,
        description = otherRuns.filter { runs ->
            val isChannel = runs.any { it.navigationEndpoint?.browseEndpoint != null }
            val isFallbackChannel = fallbackChannel != null && runs.any { it.text == fallbackChannel.name }
            val hasDigits = runs.any { it.text?.any { char -> char.isDigit() } == true }
            !isChannel && !isFallbackChannel && hasDigits
        }.flatten().joinToString(" • ") { it.text ?: "" }.takeIf { it.isNotBlank() },
        isEditable = false
    ).takeIf { it.info?.endpoint?.browseId != null }
}
