package app.n_zik.android.components.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

/**
 * Maximum width of an onboarding card action label in dp.
 *
 * The labels are short ("Accorder", "Settings", "Continuer"…) on a normal screen, so the
 * cap is invisible there; on a small screen or an enlarged system font the label scrolls
 * in a marquee instead of pushing the card off-screen (see [OnboardingActionCard]).
 */
internal const val ONBOARDING_ACTION_LABEL_MAX_WIDTH_DP = 104

/**
 * Label of an onboarding card action button.
 *
 * Single line, capped at [ONBOARDING_ACTION_LABEL_MAX_WIDTH_DP]: a longer label (small
 * screen, enlarged font, long translation) scrolls in a marquee, honoring the global
 * "disable scrolling text" setting, which falls back to ellipsis — the same convention
 * as the header badges ([app.n_zik.android.components.ui.header.HeaderBadgeBox]).
 */
@Composable
internal fun OnboardingActionLabel(text: String) {
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = ONBOARDING_ACTION_LABEL_MAX_WIDTH_DP.dp)
            .conditional(!isScrollingTextDisabled) {
                basicMarquee(iterations = Int.MAX_VALUE)
            }
    )
}

/**
 * Shared onboarding card: icon, title, description and an optional trailing action — the
 * same pattern behind every onboarding step (permissions, restore, name, accounts).
 *
 * Overflow-safe by design (small screens / enlarged system font):
 * - the title is a single line capped to the text column and scrolls in a marquee when
 *   it is wider (honoring the global "disable scrolling text" setting)
 * - the description wraps, never capped
 * - the action label is bounded, so it can never push the card past the screen edge
 *
 * @param icon drawable for the leading icon slot
 * @param title card title, one line
 * @param description card description, wrapping
 * @param action optional trailing action (button or status icon); null for header-only cards
 * @param extraContent optional content below the header row, inside the same card
 *   (restore options + button, guest name field + button)
 */
@Composable
fun OnboardingActionCard(
    icon: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(containerColor = colorPalette().background1)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingCardIcon(icon)

                Spacer(modifier = Modifier.width(16.dp))

                OnboardingCardText(title = title, description = description)

                if (action != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    action()
                }
            }

            if (extraContent != null) {
                extraContent()
            }
        }
    }
}

/**
 * Titled variant for the one-line section headers ("Permissions" card): same overflow
 * treatment as the card title.
 */
@Composable
fun OnboardingSectionCard(title: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(containerColor = colorPalette().background1)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // Section headers keep their accent color (card titles use the text color)
            OnboardingCardTitle(title, color = colorPalette().accent)
        }
    }
}

@Composable
private fun OnboardingCardIcon(icon: Int) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(colorPalette().accent.copy(alpha = 0.1f), shape = uiRoundnessShape()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colorPalette().accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun RowScope.OnboardingCardText(title: String, description: String) {
    Column(modifier = Modifier.weight(1f)) {
        OnboardingCardTitle(title, color = colorPalette().text)
        Text(
            text = description,
            style = typography().xxs,
            color = colorPalette().textSecondary
        )
    }
}

@Composable
internal fun OnboardingCardTitle(text: String, color: Color) {
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    Text(
        text = text,
        style = typography().s,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .conditional(!isScrollingTextDisabled) {
                basicMarquee(iterations = Int.MAX_VALUE)
            }
    )
}
