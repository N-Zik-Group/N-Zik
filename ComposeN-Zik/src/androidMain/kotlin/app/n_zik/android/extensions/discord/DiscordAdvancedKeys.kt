package app.n_zik.android.extensions.discord

import android.content.Context
import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.encryptedPreferences

/**
 * Keys of the advanced Discord presence settings (item 6).
 *
 * Declared in this new app file rather than in the legacy `EncryptedPreferences.kt`
 * (out of scope): all values live in the same encrypted SharedPreferences, read and
 * written through the existing `context.encryptedPreferences` helper.
 */
const val isDiscordAdvancedModeKey = "isDiscordAdvancedMode"
const val discordAdvancedActivityTypeKey = "discordAdvancedActivityType"
const val discordAdvancedPausePresenceEnabledKey = "discordAdvancedPausePresenceEnabled"
const val discordAdvancedNameKey = "discordAdvancedName"
const val discordAdvancedStateTemplateKey = "discordAdvancedStateTemplate"
const val discordAdvancedDetailsTemplateKey = "discordAdvancedDetailsTemplate"
const val discordAdvancedPauseTemplateKey = "discordAdvancedPauseTemplate"
const val discordAdvancedButton1EnabledKey = "discordAdvancedButton1Enabled"
const val discordAdvancedButton1LabelKey = "discordAdvancedButton1Label"
const val discordAdvancedButton1UrlKey = "discordAdvancedButton1Url"
const val discordAdvancedButton2EnabledKey = "discordAdvancedButton2Enabled"
const val discordAdvancedButton2LabelKey = "discordAdvancedButton2Label"
const val discordAdvancedButton2UrlKey = "discordAdvancedButton2Url"
// Per-section visibility toggles (advanced mode only; default on = current behavior).
const val discordAdvancedShowStateKey = "discordAdvancedShowState"
const val discordAdvancedShowDetailsKey = "discordAdvancedShowDetails"
const val discordAdvancedShowArtworkKey = "discordAdvancedShowArtwork"
const val discordAdvancedShowSmallImageKey = "discordAdvancedShowSmallImage"
const val discordAdvancedShowTimestampsKey = "discordAdvancedShowTimestamps"
// Image tooltip templates (advanced mode; empty = the built-in default).
const val discordAdvancedLargeImageTextKey = "discordAdvancedLargeImageText"
const val discordAdvancedSmallImageTextKey = "discordAdvancedSmallImageText"
// Inactivity timer toggles (advanced mode; default on = current behavior).
const val discordAdvancedPauseClearEnabledKey = "discordAdvancedPauseClearEnabled"
const val discordAdvancedIdleCloseEnabledKey = "discordAdvancedIdleCloseEnabled"

/** All advanced keys, for the service's encrypted-prefs listener re-sync (item 6). */
val discordAdvancedSettingKeys: Set<String> = setOf(
    isDiscordAdvancedModeKey,
    discordAdvancedActivityTypeKey,
    discordAdvancedPausePresenceEnabledKey,
    discordAdvancedNameKey,
    discordAdvancedStateTemplateKey,
    discordAdvancedDetailsTemplateKey,
    discordAdvancedPauseTemplateKey,
    discordAdvancedButton1EnabledKey,
    discordAdvancedButton1LabelKey,
    discordAdvancedButton1UrlKey,
    discordAdvancedButton2EnabledKey,
    discordAdvancedButton2LabelKey,
    discordAdvancedButton2UrlKey,
    discordAdvancedShowStateKey,
    discordAdvancedShowDetailsKey,
    discordAdvancedShowArtworkKey,
    discordAdvancedShowSmallImageKey,
    discordAdvancedShowTimestampsKey,
    discordAdvancedLargeImageTextKey,
    discordAdvancedSmallImageTextKey,
    discordAdvancedPauseClearEnabledKey,
    discordAdvancedIdleCloseEnabledKey,
)

/**
 * User status carried by every presence update: fixed to "online" (PW-2 — the
 * user-status selector was removed; Discord manages idle itself, `since = 0` is
 * always carried so the "online since" timestamp is never reset by us).
 */
const val DISCORD_STATUS_ONLINE = "online"

/**
 * Snapshot of the advanced Discord presence settings (item 6), read from the encrypted
 * prefs on demand (the fork prefs are EncryptedSharedPreferences — there is no Flow to
 * collect; the service's prefs listener triggers the re-sync instead).
 *
 * [activityType] is the module `ActivityType` code: 0=playing, 2=listening (default),
 * 3=watching, 5=competing.
 *
 * The `show*` flags control individual presence sections (advanced mode only — normal
 * mode keeps its frozen identity): the state line, the details line, the album artwork
 * (large image), the app logo (small image + version text) and the progress bar
 * (timestamps). All default to `true` (the sections are shown = current behavior).
 */
data class DiscordAdvancedSettings(
    val advancedMode: Boolean,
    val activityType: Int,
    val pausePresenceEnabled: Boolean,
    val activityName: String,
    val stateTemplate: String,
    val detailsTemplate: String,
    val pauseTemplate: String,
    val button1Enabled: Boolean,
    val button1Label: String,
    val button1Url: String,
    val button2Enabled: Boolean,
    val button2Label: String,
    val button2Url: String,
    val showState: Boolean,
    val showDetails: Boolean,
    val showArtwork: Boolean,
    val showSmallImage: Boolean,
    val showTimestamps: Boolean,
    /** Advanced-mode template for the large image tooltip (empty = the live album value, then "details - state"). */
    val largeImageTextTemplate: String,
    /** Advanced-mode template for the small image tooltip (empty = "v{app.version}"). */
    val smallImageTextTemplate: String,
    /**
     * Auto-clear of the stale presence 60 s after a pause (only reachable with the pause
     * presence disabled — with it enabled the paused presence is the active state and
     * stays). Default on = current behavior.
     */
    val pauseClearEnabled: Boolean,
    /** Close the RPC connection after 10 min with no media event. Default on = current behavior. */
    val idleCloseEnabled: Boolean,
) {
    companion object {
        /**
         * Defaults: mode off, listening, pause presence on, empty templates, both buttons
         * on, every presence section shown.
         */
        val DEFAULTS = DiscordAdvancedSettings(
            advancedMode = false,
            activityType = 2,
            pausePresenceEnabled = true,
            activityName = "",
            stateTemplate = "",
            detailsTemplate = "",
            pauseTemplate = "",
            button1Enabled = true,
            button1Label = "",
            button1Url = "",
            button2Enabled = true,
            button2Label = "",
            button2Url = "",
            showState = true,
            showDetails = true,
            showArtwork = true,
            showSmallImage = true,
            showTimestamps = true,
            largeImageTextTemplate = "",
            smallImageTextTemplate = "",
            pauseClearEnabled = true,
            idleCloseEnabled = true,
        )

        /** Reads all advanced keys at once (single prefs access per presence update). */
        fun read(prefs: SharedPreferences): DiscordAdvancedSettings = DiscordAdvancedSettings(
            advancedMode = prefs.getBoolean(isDiscordAdvancedModeKey, false),
            activityType = prefs.getInt(discordAdvancedActivityTypeKey, 2),
            pausePresenceEnabled = prefs.getBoolean(discordAdvancedPausePresenceEnabledKey, true),
            activityName = prefs.getString(discordAdvancedNameKey, "").orEmpty(),
            stateTemplate = prefs.getString(discordAdvancedStateTemplateKey, "").orEmpty(),
            detailsTemplate = prefs.getString(discordAdvancedDetailsTemplateKey, "").orEmpty(),
            pauseTemplate = prefs.getString(discordAdvancedPauseTemplateKey, "").orEmpty(),
            button1Enabled = prefs.getBoolean(discordAdvancedButton1EnabledKey, true),
            button1Label = prefs.getString(discordAdvancedButton1LabelKey, "").orEmpty(),
            button1Url = prefs.getString(discordAdvancedButton1UrlKey, "").orEmpty(),
            button2Enabled = prefs.getBoolean(discordAdvancedButton2EnabledKey, true),
            button2Label = prefs.getString(discordAdvancedButton2LabelKey, "").orEmpty(),
            button2Url = prefs.getString(discordAdvancedButton2UrlKey, "").orEmpty(),
            showState = prefs.getBoolean(discordAdvancedShowStateKey, true),
            showDetails = prefs.getBoolean(discordAdvancedShowDetailsKey, true),
            showArtwork = prefs.getBoolean(discordAdvancedShowArtworkKey, true),
            showSmallImage = prefs.getBoolean(discordAdvancedShowSmallImageKey, true),
            showTimestamps = prefs.getBoolean(discordAdvancedShowTimestampsKey, true),
            largeImageTextTemplate = prefs.getString(discordAdvancedLargeImageTextKey, "").orEmpty(),
            smallImageTextTemplate = prefs.getString(discordAdvancedSmallImageTextKey, "").orEmpty(),
            pauseClearEnabled = prefs.getBoolean(discordAdvancedPauseClearEnabledKey, true),
            idleCloseEnabled = prefs.getBoolean(discordAdvancedIdleCloseEnabledKey, true),
        )

        fun read(context: Context): DiscordAdvancedSettings = read(context.encryptedPreferences)
    }
}
