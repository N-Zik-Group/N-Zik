package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

/** Shared dialog surface with bounded width and height. */
@Composable
fun DialogFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    bodyPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val maxWidth =
        if (isLandscape) Dialog.MAX_WIDTH_LANDSCAPE else Dialog.MAX_WIDTH_PORTRAIT
    val maxHeight =
        if (isLandscape) Dialog.MAX_HEIGHT_LANDSCAPE else Dialog.MAX_HEIGHT_PORTRAIT

    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = modifier
                .testTag("dialogFrameCard")
                .wrapContentSize()
                .sizeIn(
                    maxWidth = screenWidthDp * maxWidth,
                    maxHeight = screenHeightDp * maxHeight
                )
                // Tight width prevents marquee children from repeatedly changing the window size.
                .width(screenWidthDp * maxWidth)
                .padding(16.dp),
            shape = uiRoundnessShape(),
            colors = CardDefaults.cardColors(
                // Elevated surface: dark gray in pitch black instead of pure black
                containerColor = surfaceColor(colorPalette().background1)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                title?.let {
                    Column(
                        modifier = Modifier
                            .testTag("dialogFrameHeader")
                            .fillMaxWidth()
                            .background(surfaceColor(colorPalette().background1))
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BasicText(
                            text = it,
                            modifier = Modifier.fillMaxWidth().testTag("dialogFrameTitle"),
                            style = typography().l.semiBold.copy(
                                color = colorPalette().text,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.height(1.dp).testTag("dialogFrameHeaderDivider")
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("dialogFrameBody")
                        .fillMaxWidth()
                        .background(recessedSurfaceColor(colorPalette().background0))
                        .padding(bodyPadding),
                    horizontalAlignment = horizontalAlignment,
                    content = content
                )
                footer?.let {
                    HorizontalDivider(
                        modifier = Modifier.height(1.dp).testTag("dialogFrameFooterDivider")
                    )
                    Column(
                        modifier = Modifier
                            .testTag("dialogFrameFooter")
                            .fillMaxWidth()
                            .background(surfaceColor(colorPalette().background1))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = horizontalAlignment,
                        content = it
                    )
                }
            }
        }
    }
}
