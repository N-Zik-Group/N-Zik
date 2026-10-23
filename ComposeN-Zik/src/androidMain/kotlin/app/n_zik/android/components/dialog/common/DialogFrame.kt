package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.uiRoundnessShape

/**
 * Shared dialog window: the canonical n_zik frame — a card (max width 95% portrait / 60%
 * landscape, max height 80%), [uiRoundnessShape], [colorPalette().background1] and an
 * optional `l` semibold title. Every dialog — native or migrated from the legacy tree —
 * renders through this frame so button rows and content can never overflow (issue #896).
 *
 * The card is the window's root and its body column must NOT be scrollable: `Dialog.Render()`
 * lays out its body with `Modifier.weight(1f, fill = false)`, which collapses in a scrollable
 * column (the body renders 0×0).
 */
@Composable
fun DialogFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val maxWidth =
        if (isLandscape) Dialog.MAX_WIDTH_LANDSCAPE else Dialog.MAX_WIDTH_PORTRAIT
    val maxHeight =
        if (isLandscape) Dialog.MAX_HEIGHT_LANDSCAPE else Dialog.MAX_HEIGHT_PORTRAIT

    Dialog(onDismiss) {
        Card(
            modifier = modifier
                .testTag("dialogFrameCard")
                .wrapContentSize()
                .sizeIn(
                    maxWidth = screenWidthDp * maxWidth,
                    maxHeight = screenHeightDp * maxHeight
                )
                .padding(16.dp),
            shape = uiRoundnessShape(),
            colors = CardDefaults.cardColors(
                containerColor = colorPalette().background1
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = horizontalAlignment
            ) {
                title?.let {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .fillMaxWidth(0.9f),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = it,
                            style = typography().l.semiBold.copy(
                                color = colorPalette().text
                            )
                        )
                    }
                }
                content()
            }
        }
    }
}
