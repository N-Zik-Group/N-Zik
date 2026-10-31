package app.it.fast4x.rimusic.ui.components.themed

import app.n_zik.android.components.glass.NativeSectionIconAction

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.glass.GlassTitleActionRow

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.typography

@Composable
fun Title(
    title: String,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 12.dp,
    @DrawableRes icon: Int? = R.drawable.arrow_forward,
    enableClick: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    GlassTitleActionRow(
        modifier = modifier.fillMaxWidth(),
        verticalPadding = verticalPadding,
        hasTrailingActions = trailingIcon != null,
        onClick = onClick?.let { action -> { if (enableClick) action() } },
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = typography().l.semiBold.fontSize,
                fontWeight = typography().l.semiBold.fontWeight,
                color = colorPalette().text,
                textAlign = TextAlign.Start
            ),
            modifier = Modifier.weight(1f)

        )

        if (onClick != null && enableClick) {
            Icon(
                painter = painterResource(icon ?: R.drawable.arrow_forward),
                contentDescription = null,
                tint = colorPalette().text
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            trailingIcon()
        }
    }
}

@Composable
fun Title2Actions(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon1: Int? = R.drawable.arrow_forward,
    @DrawableRes icon2: Int? = R.drawable.arrow_forward,
    enableClick: Boolean = true,
    onClick1: (() -> Unit)? = null,
    onClick2: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(uiRoundnessShape()).clickable(enabled = onClick1 != null) {
                if (enableClick)
                    onClick1?.invoke()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = typography().l.semiBold.fontSize,
                fontWeight = typography().l.semiBold.fontWeight,
                color = colorPalette().text,
                textAlign = TextAlign.Start
            ),
            modifier = Modifier.weight(1f)

        )
        if (onClick2 != null && enableClick) {
            NativeSectionIconAction(
                icon = icon2 ?: R.drawable.arrow_forward,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
                color = colorPalette().text,
                onClick = onClick2,
            )
        }

        if (onClick1 != null && enableClick) {
            NativeSectionIconAction(
                icon = icon1 ?: R.drawable.arrow_forward,
                contentDescription = null,
                color = colorPalette().text,
                onClick = onClick1,
            )
        }

    }
}

@Composable
fun Title3Actions(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon1: Int? = R.drawable.arrow_forward,
    @DrawableRes icon2: Int? = R.drawable.arrow_forward,
    @DrawableRes icon3: Int? = R.drawable.arrow_forward,
    enableClick: Boolean = true,
    onClick1: (() -> Unit)? = null,
    onClick2: (() -> Unit)? = null,
    onClick3: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(uiRoundnessShape()).clickable(enabled = onClick1 != null) {
                if (enableClick)
                    onClick1?.invoke()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = typography().l.semiBold.fontSize,
                fontWeight = typography().l.semiBold.fontWeight,
                color = colorPalette().text,
                textAlign = TextAlign.Start
            ),
            modifier = Modifier.weight(1f)
        )
        if (onClick3 != null && enableClick) {
            NativeSectionIconAction(
                icon = icon3 ?: R.drawable.arrow_forward,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
                color = colorPalette().text,
                onClick = onClick3,
            )
        }

        if (onClick2 != null && enableClick) {
            NativeSectionIconAction(
                icon = icon2 ?: R.drawable.arrow_forward,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
                color = colorPalette().text,
                onClick = onClick2,
            )
        }

        if (onClick1 != null && enableClick) {
            NativeSectionIconAction(
                icon = icon1 ?: R.drawable.arrow_forward,
                contentDescription = null,
                color = colorPalette().text,
                onClick = onClick1,
            )
        }
    }
}

@Composable
fun TitleSection(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = TextStyle(
            fontSize = typography().xl.bold.fontSize,
            fontWeight = typography().xl.bold.fontWeight,
            color = colorPalette().text,
            textAlign = TextAlign.Start
        ),
        modifier = modifier.padding(end = 12.dp)

    )


}

@Composable
fun TitleMiniSection(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = TextStyle(
            fontSize = typography().xs.bold.fontSize,
            fontWeight = typography().xs.bold.fontWeight,
            color = colorPalette().text,
            textAlign = TextAlign.Start
        ),
        modifier = modifier.padding(top = 5.dp)
    )
}





