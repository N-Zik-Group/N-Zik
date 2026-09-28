package app.n_zik.android.core.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.putEnum
import timber.log.Timber

/**
 * Seeds the theme-mode default (spec-achromatic-ramp-luminance-cap, scope extension
 * 2026-09-28): the user-facing default is "Theme mode" ([ColorPaletteMode.System]), but
 * every pre-existing read site falls back to [ColorPaletteMode.Dark] when the key is
 * absent from a profile's prefs (fresh install, data wipe, or a newly switched profile —
 * prefs are per-profile). Writing the default once at process start makes every read
 * site (legacy or new namespace) observe [ColorPaletteMode.System] from the first
 * composition.
 *
 * Idempotent and never overrides an explicit user choice: the write only happens when
 * the key is absent.
 */
internal fun ensureDefaultColorPaletteMode(prefs: SharedPreferences) {
    if (prefs.contains(colorPaletteModeKey)) return
    prefs.edit { putEnum(colorPaletteModeKey, ColorPaletteMode.System) }
    Timber.tag("DefaultColorPaletteMode").d("seeded colorPaletteMode default = System (theme mode)")
}
