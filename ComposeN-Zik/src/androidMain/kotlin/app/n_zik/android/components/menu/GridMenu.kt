package app.n_zik.android.components.menu


import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.menu.MenuConstants.CONTENT_HORIZONTAL_PADDING
import app.n_zik.android.components.menu.MenuConstants.CONTENT_TOP_PADDING
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.gridMenuShape
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuScrollState
import app.n_zik.android.uiRoundnessShape

object GridMenu {

    @Composable
    fun Menu(
        showDragHandle: Boolean = true,
        title: String? = null,
        headerTrailing: (@Composable RowScope.() -> Unit)? = null,
        drawSurface: Boolean = true,
        headerContent: (@Composable ColumnScope.() -> Unit)? = null,
        bodyHeader: (@Composable ColumnScope.() -> Unit)? = null,
        emptyBody: (@Composable () -> Unit)? = null,
        headerLeading: (@Composable RowScope.() -> Unit)? = null,
        content: LazyGridScope.() -> Unit,
    ) {
        MenuFrame(
            showDragHandle = showDragHandle,
            title = title,
            headerLeading = headerLeading,
            headerTrailing = headerTrailing,
            drawSurface = drawSurface,
            headerContent = headerContent,
            bodyHeader = bodyHeader,
            emptyBody = emptyBody,
            bodyAlignment = Alignment.Start,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                state = LocalMenuScrollState.current?.rememberGridState() ?: rememberLazyGridState(),
                contentPadding = PaddingValues(
                    start = CONTENT_HORIZONTAL_PADDING.dp,
                    end = CONTENT_HORIZONTAL_PADDING.dp,
                    top = CONTENT_TOP_PADDING.dp,
                    bottom = 100.dp,
                ),
                modifier = Modifier.weight(1f),
                content = content,
            )
        }
    }

    @Composable
    fun Entry(
        text: String,
        icon: @Composable BoxScope.() -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        subtitle: String? = null,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        iconHasOwnBackground: Boolean = false,
        selected: Boolean = false,
        onLongClickLabel: String? = null,
        titleMaxLines: Int = 1,
        trailingContent: @Composable () -> Unit = {}
    ) {
        val alpha = if (enabled) 1f else 0.5f
        val isScrollingTextDisabled by rememberPreference( disableScrollingTextKey, false )
        val glass = rememberGlassSettings()
        val shape = gridMenuShape()

        Column(
            modifier = modifier
                .clip(shape)
                .background(if (selected) colorPalette().accent.copy(alpha = 0.08f) else Color.Transparent)
                .menuEntryClick(shape, enabled, onClick, onLongClick, onLongClickLabel = onLongClickLabel)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon + trailing content side by side
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Icon with accent background
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .then(if (iconHasOwnBackground) Modifier else Modifier.iconBadgeBackground(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape(),
                            settings = glass,
                        )),
                    contentAlignment = Alignment.Center,
                    content = icon
                )

                // Trailing content next to icon (arrow/toggle)
                trailingContent()
            }

            // Text centered
            Text(
                text = text,
                overflow = TextOverflow.Ellipsis,
                color = colorPalette().text.copy(alpha = alpha),
                style = typography().xs.semiBold,
                textAlign = TextAlign.Center,
                maxLines = titleMaxLines,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .conditional(titleMaxLines == 1 && !isScrollingTextDisabled) {
                        basicMarquee( iterations = Int.MAX_VALUE )
                    }
            )

            // Subtitle
            subtitle?.let {
                Text(
                    text = it,
                    overflow = TextOverflow.Ellipsis,
                    color = colorPalette().textSecondary.copy(alpha = alpha),
                    style = typography().xxs,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .conditional( !isScrollingTextDisabled ) {
                            basicMarquee( iterations = Int.MAX_VALUE )
                        }
                )
            }
        }
    }
}
