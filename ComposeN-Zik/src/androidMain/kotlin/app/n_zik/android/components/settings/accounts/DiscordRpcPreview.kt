package app.n_zik.android.components.settings.accounts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassCustomChip
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.TextContrast
import app.n_zik.android.components.glass.glassPaneForeground
import app.n_zik.android.components.glass.glassSurfaceDarkness
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.surfaceBackdropColor
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.extensions.discord.DiscordActivityBuilder
import app.n_zik.android.extensions.discord.DiscordAdvancedSettings
import app.n_zik.android.extensions.discord.DiscordMediaInfo
import app.n_zik.android.extensions.discord.DiscordStrings
import app.n_zik.android.extensions.discord.DiscordTemplateRenderer
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.albumTitleWithFallback
import app.n_zik.android.utils.artistTextWithFallback
import app.n_zik.android.utils.getVersionName
import app.n_zik.android.utils.isExplicit

/**
 * Discord section only (item 6): live preview of the Discord RPC card, rendered with
 * the same [DiscordActivityBuilder] the presence manager uses, so what you see here is
 * what gets sent. When nothing is playing, the fallback card shows the template
 * variables as-is (the current customization) with the app's standard missing-image
 * icon (the ImageCacheFactory fallback drawable). [tick] drives recomposition so the
 * card stays reactive to settings changes even while paused/idle. [mode] pins the
 * preview to a playback state (0 = playing, 1 = paused, 2 = idle) from the state
 * chips below the card; a pinned playing/paused state falls back to idle when no
 * media is loaded.
 */
@Composable
internal fun DiscordRpcPreviewCard(
    // Player (not ExoPlayer): the guarded Listen Together facade is a plain Player — this
    // preview only reads currentMediaItem (spec-listen-together-guest-lock-hardening).
    player: Player?,
    positionMs: Long,
    tick: Int,
    settings: DiscordAdvancedSettings,
    // Pinned state from the state chips: 0 = playing, 1 = paused, 2 = nothing playing
    // (-1 = follow the real player state, resolved by the caller).
    mode: Int
) {
    val palette = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Inset to match the section content (13.dp = the "Account info"
            // indent and the "Rich Presence preview" title inset).
            .padding(horizontal = 13.dp)
            .padding(bottom = 12.dp)
            // background2 (not background1): a visibly distinct card surface.
            .background(palette.background2, uiRoundnessShape())
            .clip(uiRoundnessShape())
            .padding(12.dp)
    ) {
        val item = player?.currentMediaItem
        val info = item?.let { item ->
            val cleanTitle = cleanPrefix(item.mediaMetadata.title?.toString() ?: "").takeIf { it.isNotBlank() }
                ?: stringResource(R.string.metadata_unknown_title)
            DiscordMediaInfo(
                // The explicit marker, exactly like the real presence / notification:
                // prepended when the app's MediaItem.isExplicit flags the track.
                title = if (item.isExplicit) "\uD83C\uDD74 $cleanTitle" else cleanTitle,
                // Artist resolved metadata → DB, the same sources the real presence uses
                // (artistTextOrDb) — the preview stays "what you see is what gets sent".
                artist = item.artistTextWithFallback(),
                albumName = item.albumTitleWithFallback(),
                songId = item.mediaId
            )
        } ?: // Idle state: never used (the fallback card renders the templates as-is).
            DiscordMediaInfo("", "", null, "")
        // Pinned preview state (state chips): 0 = playing, 1 = paused, 2 = idle.
        // A pinned playing/paused preview without a loaded media renders the raw templates.

        // The paused variant shows the pause template, so the three variants stay
        // distinguishable even with nothing playing.
        val effectiveMode = mode.coerceIn(0, 2)
        val isIdle = effectiveMode == 2
        val isPlaying = effectiveMode == 0
        // Localized strings for the presence content (no hardcoded user-facing text).
        val strings = DiscordStrings(
            nameFallback = stringResource(R.string.discord_presence_name),
            buttonGetNZik = stringResource(R.string.discord_presence_button_get_nzik),
            buttonListenYtmusic = stringResource(R.string.discord_presence_button_listen_ytmusic),
            pausedLineDefault = stringResource(R.string.discord_presence_pause_default),
            unknownAlbum = stringResource(R.string.discord_template_unknown_album),
            appVersion = getVersionName(),
        )
        // PW-3: the pause presence is optional — when disabled, no paused update is
        // ever sent.

        // The real Discord keeps the last playing presence, so the preview shows the
        // playing card too (the paused variant does not exist).
        val content = when {
            isIdle ->
                // Fallback card: the template variables as-is (current customization).
                DiscordActivityBuilder.buildIdlePreview(settings, strings)
            isPlaying && item != null ->
                DiscordActivityBuilder.buildForPlaying(info, settings, strings)
            isPlaying ->
                // Pinned playing preview without media: the template variables as-is.
                DiscordActivityBuilder.buildIdlePreview(settings, strings)
            item != null && !settings.pausePresenceEnabled ->
                // Paused with the pause presence disabled: keep the playing card
                // (what the real presence shows).
                DiscordActivityBuilder.buildForPlaying(info, settings, strings)
            item != null ->
                DiscordActivityBuilder.buildForPlaying(info, settings, strings)
                    .copy(details = DiscordActivityBuilder.buildPausedLine(info, settings, strings))
            !settings.pausePresenceEnabled ->
                // Pinned paused preview without media, pause presence disabled: no
                // paused variant exists — the playing-style fallback.
                DiscordActivityBuilder.buildIdlePreview(settings, strings)
            else ->
                // Pinned paused preview without media: the pause template as-is.
                DiscordActivityBuilder.buildIdlePreview(settings, strings)
                    .copy(details = settings.pauseTemplate.ifBlank { strings.pausedLineDefault })
        }
        // Header line ("Listening N-Zik" style, Discord card layout): type + activity name.
        val typeLabel = when (settings.activityType) {
            0 -> stringResource(R.string.discord_activity_playing)
            3 -> stringResource(R.string.discord_activity_watching)
            5 -> stringResource(R.string.discord_activity_competing)
            else -> stringResource(R.string.discord_activity_listening)
        }
        // PW-4: media artwork on the left (Discord card layout); the standard
        // missing-image icon when the media carries no artwork URI (or nothing is playing).
        val artworkUrl = item?.mediaMetadata?.artworkUri?.toString()?.takeIf { it.isNotBlank() }
        // The section toggles hide the matching card parts (advanced mode only — normal
        // mode keeps the frozen identity, exactly like the real presence).
        val showArtworkSection = !settings.advancedMode || settings.showArtwork
        val showSmallImageSection = !settings.advancedMode || settings.showSmallImage
        val showTimestampsSection = !settings.advancedMode || settings.showTimestamps
        // The large image text (the artwork's Discord hover tooltip): rendered as its own
        // card line so the customization is visible in the preview (on Discord itself it

        // only shows as a tooltip over the artwork). A custom template renders with the
        // media (as-is on the fallback card).

        // The built-in default is the live album value when the app knows it,
        // otherwise the current configuration's "details - state" expression —

        // the same the real presence computes (manager sendActivity). The paused
        // line renders into details, so the paused state shows in the image text too.
        val imageText =
            if (settings.advancedMode && settings.largeImageTextTemplate.isNotBlank()) {
                if (item != null) {
                    DiscordTemplateRenderer.render(
                        settings.largeImageTextTemplate,
                        info.title,
                        info.artist,
                        info.albumName,
                        info.songId,
                        strings.unknownAlbum,
                        strings.appVersion,
                    )
                } else {
                    settings.largeImageTextTemplate
                }
            } else {
                // Built-in default, exactly like the real presence: the album value when
                // the app knows it (live — metadata then DB).

                // Unknown album while media is loaded → the current configuration's
                // "details - state" expression (never a rendered "Unknown Album").

                // No media (fallback card) → the current image template,
                // i.e. the built-in album template as-is.
                if (item != null) {
                    info.albumName?.takeIf { it.isNotBlank() }
                        ?: if (content.state.isNotBlank()) "${content.details} - ${content.state}" else content.details
                } else {
                    DiscordActivityBuilder.DEFAULT_LARGE_IMAGE_TEXT_TEMPLATE
                }
            }
        // The app logo tooltip content: rendered while media is loaded (the same value
        // the real presence sends).

        // The template as-is otherwise (the current template IS the fallback).
        // The artwork tooltip reuses the card line above (`imageText`): rendered content

        // with media, template as-is without.
        val smallImageTooltipText =
            if (item != null) {
                DiscordTemplateRenderer.render(
                    settings.smallImageTextTemplate.ifBlank { DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE },
                    info.title,
                    info.artist,
                    info.albumName,
                    info.songId,
                    strings.unknownAlbum,
                    strings.appVersion,
                )
            } else {
                settings.smallImageTextTemplate.ifBlank { DiscordActivityBuilder.DEFAULT_LOGO_TEXT_TEMPLATE }
            }
        // Tooltip popups: tapping the artwork / the app logo shows the matching image
        // text, mirroring Discord's hover tooltips.
        val cardContext = LocalContext.current
        var showLargeImageTooltip by remember { mutableStateOf(false) }
        var showSmallImageTooltip by remember { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header line, full width ABOVE the artwork row (Discord card layout):
            // type + activity name.
            Text(
                text = "$typeLabel ${content.name}",
                // The card mimics the (dark) Discord rich presence: all text white.
                style = typography().s.copy(color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (showArtworkSection) {
                    // Tap = the large image text tooltip popup (the Discord hover equivalent).
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { showLargeImageTooltip = true },
                    ) {
                        if (artworkUrl != null) {
                            ImageCacheFactory.AsyncImage(
                                shape = RoundedCornerShape(12.dp),
                                thumbnailUrl = artworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            // Fallback artwork: the app's standard missing-image icon.

                            // The same drawable ImageCacheFactory uses for errors/absent
                            // thumbnails.
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_box),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        // Small image overlay at the artwork's bottom-right corner
                        // (Discord's small-image position — the tooltip is the app version).

                        // ic_launcher = the circular app icon (the one the import-CSV menu
                        // uses); ic_launcher_box is a square and reads badly at this size.
                        if (showSmallImageSection) {
                            // Tap = the small image text tooltip popup (the app version).
                            Image(
                                painter = painterResource(R.drawable.ic_launcher),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickable { showSmallImageTooltip = true }
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    // The main (bold) line: the rendered details. The idle fallback card
                    // (no media) keeps the N-Zik identity name when the details template

                    // is empty; a DISABLED details section (playing/paused) hides the line
                    // entirely — the preview mirrors the real presence, where a disabled

                    // section is omitted, not replaced.
                    val boldLine = when {
                        content.details.isNotEmpty() -> content.details
                        isIdle -> content.name
                        else -> null
                    }
                    if (boldLine != null) {
                        Text(
                            text = boldLine,
                            style = typography().m.copy(color = Color.White),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Second body line: the state (the artist by default).
                    if (content.state.isNotEmpty()) {
                        Text(
                            text = content.state,
                            style = typography().s.copy(color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Third body line: the large image text (hidden with the artwork
                    // section — no artwork = no tooltip).
                    if (showArtworkSection && imageText.isNotEmpty()) {
                        Text(
                            text = imageText,
                            style = typography().xs.copy(color = palette.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val duration = player?.duration ?: 0L
                    // The progress bar shows in EVERY preview state (playing / paused /
                    // fallback): real media -> live progress.

                    // No media -> an empty bar with "0:00 / 0:00" (the card stays complete).
                    // Material's LinearProgressIndicator, the app's standard bar.
                    if (showTimestampsSection) {
                        val progress =
                            if (duration > 0) (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            else 0f
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = palette.accent,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            strokeCap = StrokeCap.Round,
                            progress = { progress },
                        )
                        // Time labels under the bar (Discord shows "elapsed / total").
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatPreviewTime(if (duration > 0) positionMs else 0L),
                                style = typography().xs.copy(color = palette.textSecondary),
                                maxLines = 1
                            )
                            Text(
                                text = formatPreviewTime(duration),
                                style = typography().xs.copy(color = palette.textSecondary),
                                maxLines = 1
                            )
                        }
                    }
                    if (content.buttons.isNotEmpty()) {
                        // FlowRow: custom button labels can be long — wrapping keeps the
                        // buttons from overflowing/overlapping each other in the card.
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            content.buttons.forEach { button ->
                                // Tap = open the button link in the external browser (the
                                // real Discord buttons open the same URL).
                                DiscordPreviewChip(
                                    label = button.label,
                                    color = palette.accent.copy(alpha = 0.15f),
                                    foreground = Color.White,
                                    onClick = { cardContext.openDiscordButtonUrl(button.url) },
                                )
                            }
                        }
                    }
                }
            }
        }
        // The image text tooltip popups (tap targets: the artwork and the app logo).
        if (showLargeImageTooltip) {
            DiscordImageTooltipDialog(
                title = stringResource(R.string.discord_advanced_image_text),
                text = imageText,
                onDismiss = { showLargeImageTooltip = false },
            )
        }
        if (showSmallImageTooltip) {
            DiscordImageTooltipDialog(
                title = stringResource(R.string.discord_advanced_logo_text),
                text = smallImageTooltipText,
                onDismiss = { showSmallImageTooltip = false },
            )
        }
    }
}

/** Discord section only: "m:ss" time label for the preview card's progress bar (pure time formatting — not user-facing text, nothing to localize). */
private fun formatPreviewTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** Preview labels resolve after their own chip film, independently of the surrounding card. */
@Composable
internal fun DiscordPreviewChip(
    label: String,
    color: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
) {
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    GlassCustomChip(color, onClick, modifier, settings = settings) { chipModifier ->
        val ink = if (material) glassPaneForeground(foreground, surfaceBackdropColor(),
            TextContrast, glassSurfaceDarkness(settings)) else foreground
        Text(
            text = label,
            style = typography().s.copy(color = ink),
            modifier = chipModifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Discord section only: opens a preview card button URL in the external browser — the real Discord buttons open the same link, and the preview must let the user verify where each button points. */
private fun Context.openDiscordButtonUrl(url: String) {
    if (url.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
