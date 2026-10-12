package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.semiBold

/**
 * Indeterminate / determinate progress dialog on the shared
 * [DefaultDialog]; when [onDismiss] is provided a cancel [ButtonRow]
 * pill is shown.
 */
@Composable
fun InProgressDialog(
    total: Int,
    done: Int,
    text: String,
    onDismiss: (() -> Unit)? = null,
) {
    val palette = colorPalette()
    DefaultDialog(
        onDismiss = { if (onDismiss != null) { onDismiss() } },
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.3f else 0.8f)
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                textAlign = TextAlign.Center,
                fontSize = typography().l.bold.fontSize,
                fontWeight = typography().l.bold.fontWeight,
                color = palette.text
            ),
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        val progress = if (total > 0) done.toFloat() / total.toFloat() else 0f
        if (total > 0) {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                color = palette.accent,
                trackColor = palette.background2
            )
        } else {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally),
                color = palette.accent,
                trackColor = palette.background2
            )
        }
        if (total > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = "$done / $total",
                style = TextStyle(
                    textAlign = TextAlign.Center,
                    fontStyle = typography().xs.semiBold.fontStyle,
                    color = palette.text
                ),
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDismiss != null) {
            Spacer(modifier = Modifier.height(20.dp))
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        danger = true
                    )
                )
            )
        }
    }
}
