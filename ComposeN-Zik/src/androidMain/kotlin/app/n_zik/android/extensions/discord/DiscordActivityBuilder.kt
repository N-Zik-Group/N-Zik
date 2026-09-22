package app.n_zik.android.extensions.discord

import com.metrolist.music.discordrpc.entities.Button

/**
 * Media info needed to render a presence line (title/artist/album/song id).
 * The title may already carry the playback-speed suffix (" [1.50x]") when built by
 * [DiscordPresenceManager.sendPlayingPresence] (upstream parity: the suffix is applied
 * to the title before template rendering).
 */
data class DiscordMediaInfo(
    val title: String,
    val artist: String,
    val albumName: String?,
    val songId: String,
)

/**
 * Rendered presence content: activity name, state line, details line and buttons.
 * Images/timestamps are resolved by the caller (the manager), not here.
 */
data class DiscordPresenceContent(
    val name: String,
    val state: String,
    val details: String,
    val buttons: List<Button>,
)

/**
 * Localized strings needed to build presence content (item 6).
 *
 * Resolved by the caller from strings.xml (settings UI: `stringResource`, service:
 * `Context.getString`) — the builder itself keeps no hardcoded user-facing text.
 */
data class DiscordStrings(
    /** Fixed NZik activity name (normal mode + advanced name fallback). */
    val nameFallback: String,
    /** Default Button 1 label ("Get N-Zik"). */
    val buttonGetNZik: String,
    /** Default Button 2 label ("Listen to YTMusic"). */
    val buttonListenYtmusic: String,
    /** Default paused-line template ("⏸︎ Paused: {song.name}"). */
    val pausedLineDefault: String,
    /** Localized fallback for `{album.name}` when the album is unknown. */
    val unknownAlbum: String,
    /** The app version name — feeds the `{app.version}` placeholder (resolved by the caller). */
    val appVersion: String = "",
)

/**
 * Builds the activity content from the current media + advanced settings (item 6).
 *
 * - Normal mode: the current NZik identity, unchanged — name "N-Zik", details = title,
 *   state = artist, "Get N-Zik" / "Listen to YTMusic" buttons (identity frozen 2026-09-21,
 *   all user-facing strings localized via [DiscordStrings]).
 * - Advanced mode: name/state/details/buttons rendered from the templates with the
 *   upstream placeholder set; an empty template falls back to its default; an empty
 *   activity name falls back to the localized "N-Zik" (fork divergence: upstream falls
 *   back to the artist name — see spec Implementation Notes).
 *
 * Pure object — no context, unit-testable.
 */
object DiscordActivityBuilder {

    private const val URL_N_ZIK_GITHUB = "https://github.com/N-Zik-Group/N-Zik/"
    private const val URL_YOUTUBE_WATCH = "https://music.youtube.com/watch?v="

    /**
     * Default templates and button URLs (placeholder tokens + URLs only — not
     * user-facing text). The template dialogs show these as the effective default
     * in the field background when the field is empty.
     */
    const val DEFAULT_STATE_TEMPLATE = "{artist.name}"
    const val DEFAULT_DETAILS_TEMPLATE = "{song.name}"
    const val DEFAULT_BUTTON1_URL = URL_N_ZIK_GITHUB
    const val DEFAULT_BUTTON2_URL = "$URL_YOUTUBE_WATCH{song.id}"

    /**
     * Default logo (small image) text template — the manager resolves it live to
     * "v<app version>" (`v${str.appVersion}`); this const is its template form for the
     * settings UI (entry fallback + preview tooltip).
     */
    const val DEFAULT_LOGO_TEXT_TEMPLATE = "v{app.version}"

    /**
     * Default artwork (large image) text template — the album name. Like the state/details
     * defaults, this const is the field's effective default when it is empty: the settings
     * entry, the dialog placeholder, the idle preview line and the artwork tooltip show it
     * as-is (the template IS the fallback when nothing plays). The real presence renders it
     * live to the album value (metadata or DB), falling back to the current configuration's
     * "details - state" combination when the album is unknown.
     */
    const val DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE = "{album.name}"

    private fun render(template: String, info: DiscordMediaInfo, str: DiscordStrings): String =
        DiscordTemplateRenderer.render(
            template,
            info.title,
            info.artist,
            info.albumName,
            info.songId,
            str.unknownAlbum,
            str.appVersion,
        )

    /** name/state/details/buttons for a playing (or paused) media item. */
    fun buildForPlaying(
        info: DiscordMediaInfo,
        s: DiscordAdvancedSettings,
        str: DiscordStrings,
    ): DiscordPresenceContent {
        return if (s.advancedMode) {
            DiscordPresenceContent(
                name = renderedName(s.activityName, info, str),
                // Disabled sections render as empty lines — the manager normalizes them
                // to null before the module call (the JSON field is then omitted).
                state = if (s.showState) render(s.stateTemplate.ifEmpty { DEFAULT_STATE_TEMPLATE }, info, str) else "",
                details = if (s.showDetails) render(s.detailsTemplate.ifEmpty { DEFAULT_DETAILS_TEMPLATE }, info, str) else "",
                buttons = advancedButtons(s, info, str),
            )
        } else {
            // Normal mode: the frozen NZik identity — the section toggles do not apply.
            DiscordPresenceContent(
                name = str.nameFallback,
                state = info.artist,
                details = info.title,
                buttons = normalButtons(info, str),
            )
        }
    }

    /**
     * The details line shown while paused (item 6: dedicated pause template). The pause
     * line renders INTO the details line, so the details section toggle hides it too
     * (advanced mode only; normal mode keeps the fixed representation).
     */
    fun buildPausedLine(
        info: DiscordMediaInfo,
        s: DiscordAdvancedSettings,
        str: DiscordStrings,
    ): String {
        if (s.advancedMode && !s.showDetails) return ""
        return render(
            if (s.advancedMode) s.pauseTemplate.ifEmpty { str.pausedLineDefault }
            else str.pausedLineDefault,
            info,
            str,
        )
    }

    /**
     * Fallback preview content for the idle state (2026-09-21 UI fix): the template
     * variables as-is — the current customization without any rendered media — so the
     * settings preview shows what the configured presence looks like at a glance.
     */
    fun buildIdlePreview(s: DiscordAdvancedSettings, str: DiscordStrings): DiscordPresenceContent {
        return DiscordPresenceContent(
            name = s.activityName.ifBlank { str.nameFallback },
            // The disabled sections stay hidden in the preview too (advanced mode only).
            state = if (s.advancedMode && !s.showState) "" else s.stateTemplate.ifBlank { DEFAULT_STATE_TEMPLATE },
            details = if (s.advancedMode && !s.showDetails) "" else s.detailsTemplate.ifBlank { DEFAULT_DETAILS_TEMPLATE },
            buttons = idleButtons(s, str),
        )
    }

    private fun idleButtons(s: DiscordAdvancedSettings, str: DiscordStrings): List<Button> = when {
        s.advancedMode -> buildList {
            if (s.button1Enabled) {
                add(Button(s.button1Label.ifBlank { str.buttonGetNZik }, DEFAULT_BUTTON1_URL))
            }
            if (s.button2Enabled) {
                add(Button(s.button2Label.ifBlank { str.buttonListenYtmusic }, DEFAULT_BUTTON2_URL))
            }
        }
        else -> normalButtons(DiscordMediaInfo("", "", null, ""), str)
    }

    /**
     * Fork divergence (documented in the spec Implementation Notes): upstream falls back
     * to the artist name for an empty advanced activity name; NZik keeps its fixed
     * identity ("N-Zik") instead.
     */
    private fun renderedName(activityName: String, info: DiscordMediaInfo, str: DiscordStrings): String =
        if (activityName.isBlank()) str.nameFallback else render(activityName, info, str)

    private fun normalButtons(info: DiscordMediaInfo, str: DiscordStrings): List<Button> = listOf(
        Button(label = str.buttonGetNZik, url = URL_N_ZIK_GITHUB),
        Button(label = str.buttonListenYtmusic, url = "$URL_YOUTUBE_WATCH${info.songId}"),
    )

    private fun advancedButtons(s: DiscordAdvancedSettings, info: DiscordMediaInfo, str: DiscordStrings): List<Button> {
        val buttons = mutableListOf<Button>()
        if (s.button1Enabled) {
            buttons += Button(
                label = render(s.button1Label.ifEmpty { str.buttonGetNZik }, info, str),
                url = render(s.button1Url.ifEmpty { DEFAULT_BUTTON1_URL }, info, str),
            )
        }
        if (s.button2Enabled) {
            buttons += Button(
                label = render(s.button2Label.ifEmpty { str.buttonListenYtmusic }, info, str),
                url = render(s.button2Url.ifEmpty { DEFAULT_BUTTON2_URL }, info, str),
            )
        }
        return buttons
    }
}
