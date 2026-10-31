package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.settings.common.LocalSettingsTargets

/** Centers fitting category tabs while retaining Material scrolling for wider labels. */
@Composable
internal fun SettingsDialogTabRow(
    labels: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    val target = LocalSettingsTargets.current?.nestedTarget
    LaunchedEffect(target?.id) {
        target?.nestedDestination?.tabIndex?.takeIf { it in labels.indices }?.let { index ->
            if (selectedTabIndex != index) onTabSelected(index)
        }
    }
    val textStyle = MaterialTheme.typography.titleSmall.copy(textAlign = TextAlign.Center)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tabWidths = labels.map { label ->
        val textWidth = textMeasurer.measure(label, style = textStyle, softWrap = false).size.width
        with(density) {
            (textWidth + 2 * 16.dp.roundToPx())
                .coerceAtLeast(TabRowDefaults.ScrollableTabRowMinTabWidth.roundToPx()).toDp()
        }
    }
    val edgeWidth = with(density) { (2 * 8.dp.roundToPx()).toDp() }
    val contentWidth = tabWidths.fold(edgeWidth) { total, width -> total + width }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().testTag("settingsDialogTabContainer"),
        contentAlignment = Alignment.Center,
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.width(contentWidth.coerceAtMost(maxWidth))
                .testTag("settingsDialogTabRow"),
            containerColor = Color.Transparent,
            divider = {},
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                tabPositions.getOrNull(selectedTabIndex)?.let { position ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(position)
                            .testTag("settingsDialogTabIndicator"),
                        color = colorPalette().accent,
                    )
                }
            },
        ) {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.width(tabWidths[index]).testTag("settingsDialogTab$index"),
                    text = { Text(label, style = textStyle, softWrap = false) },
                    selectedContentColor = colorPalette().accent,
                    unselectedContentColor = colorPalette().textSecondary,
                )
            }
        }
    }
}
