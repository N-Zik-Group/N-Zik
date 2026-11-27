package app.n_zik.android.components.ui.app


import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

import app.n_zik.android.MainActivity
import app.n_zik.android.R
import app.n_zik.android.components.theme.configuredApplicationPalette
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.components.theme.forPaletteMode
import app.n_zik.android.components.theme.withColor
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.client.applyProfileProxy
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.FontType
import app.n_zik.android.enums.Languages
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.BoundedCornerSize
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.applyFontPaddingKey
import app.n_zik.android.utils.artistThumbnailRoundnessDpKey
import app.n_zik.android.utils.backgroundProgressKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.customColorKey
import app.n_zik.android.utils.customThemeDark_Background0Key
import app.n_zik.android.utils.customThemeDark_Background1Key
import app.n_zik.android.utils.customThemeDark_Background2Key
import app.n_zik.android.utils.customThemeDark_Background3Key
import app.n_zik.android.utils.customThemeDark_Background4Key
import app.n_zik.android.utils.customThemeDark_TextKey
import app.n_zik.android.utils.customThemeDark_accentKey
import app.n_zik.android.utils.customThemeDark_iconButtonPlayerKey
import app.n_zik.android.utils.customThemeDark_textDisabledKey
import app.n_zik.android.utils.customThemeDark_textSecondaryKey
import app.n_zik.android.utils.customThemeLight_Background0Key
import app.n_zik.android.utils.customThemeLight_Background1Key
import app.n_zik.android.utils.customThemeLight_Background2Key
import app.n_zik.android.utils.customThemeLight_Background3Key
import app.n_zik.android.utils.customThemeLight_Background4Key
import app.n_zik.android.utils.customThemeLight_TextKey
import app.n_zik.android.utils.customThemeLight_accentKey
import app.n_zik.android.utils.customThemeLight_iconButtonPlayerKey
import app.n_zik.android.utils.customThemeLight_textDisabledKey
import app.n_zik.android.utils.customThemeLight_textSecondaryKey
import app.n_zik.android.utils.disableClosingPlayerSwipingDownKey
import app.n_zik.android.utils.disablePlayerHorizontalSwipeKey
import app.n_zik.android.utils.effectRotationKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.isProxyEnabledKey
import app.n_zik.android.utils.languageAppKey
import app.n_zik.android.utils.localeListForAppLanguage
import app.n_zik.android.utils.miniPlayerTypeKey
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.navigationBarTypeKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerThumbnailSizeKey
import app.n_zik.android.utils.playerVisualizerTypeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.proxyHostnameKey
import app.n_zik.android.utils.proxyModeKey
import app.n_zik.android.utils.proxyPortKey
import app.n_zik.android.utils.proxyUsernameKey
import app.n_zik.android.utils.restartActivityKey
import app.n_zik.android.utils.shouldRecreateActivity
import app.n_zik.android.utils.showSearchTabKey
import app.n_zik.android.utils.showTotalTimeQueueKey
import app.n_zik.android.utils.thumbnailRoundnessDpKey
import app.n_zik.android.utils.transitionEffectKey
import app.n_zik.android.utils.useSystemFontKey
import app.n_zik.android.widget.NZikWidgetManager
import app.n_zik.innertube.Innertube

internal fun MainActivity.mainActivityPreferenceChanges(
    coroutineScope: CoroutineScope,
    systemIsDark: () -> Boolean,
    appearance: () -> Appearance,
    setAppearance: (Appearance) -> Unit,
    updateAppearance: (Appearance, Boolean) -> Unit,
    refreshDynamicPalette: () -> Unit,
    cancelDynamicAppearance: () -> Unit,
): SharedPreferences.OnSharedPreferenceChangeListener =
    SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            languageAppKey -> {
                // AppCompat recreates the activity to apply the new locale (locale is not in the
                // activity-level configChanges), so the language switches live without a restart.
                val lang = sharedPreferences.getEnum(languageAppKey, Languages.System)
                if (shouldRecreateActivity(Database.isClosed)) {
                    AppCompatDelegate.setApplicationLocales(localeListForAppLanguage(lang))
                    Timber.tag("MainActivity").d("Language changed: $lang")
                } else {
                    Timber.tag("MainActivity").w("Language changed: $lang - locale apply skipped, database closed")
                }
            }

            effectRotationKey, playerThumbnailSizeKey,
            playerVisualizerTypeKey,
            disablePlayerHorizontalSwipeKey,
            disableClosingPlayerSwipingDownKey,
            showSearchTabKey,
            navigationBarPositionKey,
            navigationBarTypeKey,
            showTotalTimeQueueKey,
            backgroundProgressKey,
            transitionEffectKey,
            playerBackgroundColorsKey,
            miniPlayerTypeKey,
            hideStatusBarKey,
            restartActivityKey
                -> {
                if (shouldRecreateActivity(Database.isClosed)) {
                    this@mainActivityPreferenceChanges.recreate()
                    Timber.tag("MainActivity").d("recreate()")
                } else {
                    Timber.tag("MainActivity").w("recreate() skipped for $key: database closed")
                }
            }

            isProxyEnabledKey, proxyHostnameKey, proxyPortKey, proxyModeKey, proxyUsernameKey -> {
                if (!applyProfileProxy(this@mainActivityPreferenceChanges)) {
                    Toaster.e(R.string.network_proxy_invalid_hostname)
                }
            }

            dynamicThemeRespectBrightnessKey -> {
                if (binder == null && preferences.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic) != ColorPaletteName.Dynamic) {
                    coroutineScope.launch(NzikDispatchers.DATA) {
                        NZikWidgetManager.updateIdleWidgets(this@mainActivityPreferenceChanges)
                    }
                }
            }

            colorPaletteNameKey, colorPaletteModeKey, customColorKey,
            customThemeLight_Background0Key,
            customThemeLight_Background1Key,
            customThemeLight_Background2Key,
            customThemeLight_Background3Key,
            customThemeLight_Background4Key,
            customThemeLight_TextKey,
            customThemeLight_textSecondaryKey,
            customThemeLight_textDisabledKey,
            customThemeLight_iconButtonPlayerKey,
            customThemeLight_accentKey,
            customThemeDark_Background0Key,
            customThemeDark_Background1Key,
            customThemeDark_Background2Key,
            customThemeDark_Background3Key,
            customThemeDark_Background4Key,
            customThemeDark_TextKey,
            customThemeDark_textSecondaryKey,
            customThemeDark_textDisabledKey,
            customThemeDark_iconButtonPlayerKey,
            customThemeDark_accentKey,
                -> {
                val colorPaletteName =
                    sharedPreferences.getEnum(
                        colorPaletteNameKey,
                        ColorPaletteName.Dynamic
                    )

                val colorPaletteMode =
                    sharedPreferences.getEnum(
                        colorPaletteModeKey,
                        ColorPaletteMode.System
                    )

                if (colorPaletteName == ColorPaletteName.Dynamic) {
                    refreshDynamicPalette()
                } else {
                    cancelDynamicAppearance()

                    val colorPalette = configuredApplicationPalette(
                        this@mainActivityPreferenceChanges,
                        sharedPreferences,
                        colorPaletteName,
                        colorPaletteMode,
                        systemIsDark(),
                    ) {
                        Color(monet.getAccentColor(this@mainActivityPreferenceChanges))
                    }
                    val normalized = appearance().copy(
                        colorPalette = colorPalette,
                        typography = appearance().typography.withColor(colorPalette.text),
                    ).forPaletteMode(colorPaletteMode)

                    updateAppearance(normalized, true)
                }
            }

            thumbnailRoundnessDpKey -> {
                val thumbnailRoundnessDp =
                    sharedPreferences.getFloat(thumbnailRoundnessDpKey, 12f)

                setAppearance(appearance().copy(
                    thumbnailShape = if (thumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(thumbnailRoundnessDp.dp, 0.25f))
                ))
            }

            artistThumbnailRoundnessDpKey -> {
                val artistThumbnailRoundnessDp =
                    sharedPreferences.getFloat(artistThumbnailRoundnessDpKey, 48f)

                setAppearance(appearance().copy(
                    artistThumbnailShape = if (artistThumbnailRoundnessDp >= 48f) CircleShape else RoundedCornerShape(BoundedCornerSize(artistThumbnailRoundnessDp.dp, 0.25f))
                ))
            }

            "uiRoundnessDpKey" -> {
                val uiRoundnessDp = sharedPreferences.getFloat(key, 25f)
                setAppearance(appearance().copy(
                    uiRoundnessShape = RoundedCornerShape(BoundedCornerSize(uiRoundnessDp.dp, 0.4f))
                ))
            }

            useSystemFontKey, applyFontPaddingKey, fontTypeKey -> {
                val useSystemFont =
                    sharedPreferences.getBoolean(useSystemFontKey, false)
                val applyFontPadding =
                    sharedPreferences.getBoolean(applyFontPaddingKey, false)
                val fontType =
                    sharedPreferences.getEnum(fontTypeKey, FontType.Rubik)

                setAppearance(appearance().copy(
                    typography = typographyOf(
                        appearance().colorPalette.text,
                        useSystemFont,
                        applyFontPadding,
                        fontType
                    ),
                ))
            }
        }
    }
