package app.n_zik.android.components.settings.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Animated settings card with an icon header, an optional description and a content block.
 * Hidden cards collapse to zero height so sibling cards slide instead of jumping.
 */
@Composable
fun SettingsSectionCard(
    title: String,
    icon: Int,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    visible: Boolean = true
) {
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)

    AnimatedVisibility(
        visible = visible,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = uiRoundnessShape(),
                        spotColor = colorPalette().accent.copy(alpha = 0.2f)
                    ),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = if (colorPalette() === PureBlackColorPalette || colorPalette() === ModernBlackColorPalette || colorPaletteMode == ColorPaletteMode.PitchBlack) {
                        Color(0xFF1A1A1A) // Gray dark for pitch black themes
                    } else {
                        colorPalette().background1
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Section Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = colorPalette().accent.copy(alpha = 0.1f),
                                    shape = uiRoundnessShape()
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(icon),
                                tint = colorPalette().accent,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        BasicText(
                            text = title,
                            style = typography().xs.semiBold.copy(
                                color = colorPalette().accent
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (description != null) {
                        BasicText(
                            text = description,
                            style = typography().xxs.copy(
                                color = colorPalette().textSecondary
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // Content
                    content()
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Groups settings entries under an optional title and description, with a trailing spacer.
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    description: String? = null,
    important: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) = Column(modifier = modifier) {
    if (title != null) {
        SettingsEntryGroupText(title = title)
    }

    description?.let { description ->
        SettingsDescription(
            text = description,
            important = important
        )
    }

    content()

    SettingsGroupSpacer()
}

/** Group separator. */
@Composable
fun SettingsGroupSpacer(
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
    )
}

/** Uppercased section label used above a group of entries. */
@Composable
fun SettingsEntryGroupText(
    title: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = title.uppercase(),
        style = typography().xs.semiBold.copy(colorPalette().accent),
        modifier = modifier
            .padding(start = 12.dp)
    )
}

/** Top-level description text for a settings screen. */
@Composable
fun SettingsTopDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = typography().xs.secondary,
        modifier = modifier
            .padding(start = 12.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * Description line under an entry; red semi-bold when [important].
 */
@Composable
fun SettingsDescription(
    text: String,
    modifier: Modifier = Modifier,
    important: Boolean = false,
    textAlign: TextAlign? = null,
) {
    if (textAlign != null) {
        Text(
            text = text,
            style = if (important) typography().xxs.semiBold.color(colorPalette().red)
            else typography().xxs.secondary,
            textAlign = textAlign,
            modifier = modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )
    } else {
        BasicText(
            text = text,
            style = if (important) typography().xxs.semiBold.color(colorPalette().red)
            else typography().xxs.secondary,
            modifier = modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )
    }
}

/** Red semi-bold warning line. */
@Composable
fun ImportantSettingsDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = typography().xxs.semiBold.color(colorPalette().red),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
    )
}
