package app.n_zik.android.components.settings.common

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Animated settings card with an icon header and a content block.
 * Hidden cards collapse to zero height so sibling cards slide instead of jumping.
 */
@Suppress("UNUSED_PARAMETER") // Kept for source compatibility; cards display titles only.
@Composable
fun SettingsSectionCard(
    title: String,
    icon: Int,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    visible: Boolean = true
) {
    val context = LocalContext.current
    val highlighted = LocalSettingsTargets.current?.highlighted
    val highlightedCard = highlighted?.takeIf { it.startsWith("card.") }?.let { id ->
        SettingsCards.firstOrNull { settingsCardTargetId(context, it.tab, it.titleRes) == id }
    }
    val isHighlighted = highlightedCard != null && title == stringResource(highlightedCard.titleRes)
    val cardColor = surfaceColor(colorPalette().background1)
    val containerColor = if (isHighlighted) lerp(cardColor, colorPalette().accent, 0.22f) else cardColor
    val resetScope = LocalSettingsResetScope.current
    val displayTitle = if (resetScope != null && title == stringResource(R.string.settings_reset))
        stringResource(R.string.settings_reset_scope, stringResource(resetScope)) else title
    if (resetScope != null && title == stringResource(R.string.settings_reset)) {
        if (visible) Column(modifier = modifier.fillMaxWidth()) { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            SettingsCardSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = uiRoundnessShape(),
                containerColor = containerColor,
                backgroundColor = colorPalette().background0,
                shadowColor = colorPalette().accent.copy(alpha = 0.2f),
                shadowElevation = 4.dp
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
                                .iconBadgeBackground(
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
                            text = displayTitle,
                            style = typography().xs.semiBold.copy(
                                color = colorPalette().accent
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
