package app.n_zik.android.components.menu

import app.n_zik.android.gridMenuShape

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.menu.MenuConstants.CONTENT_HEIGHT_FRACTION
import app.n_zik.android.components.menu.MenuConstants.CONTENT_HORIZONTAL_PADDING
import app.n_zik.android.components.menu.MenuConstants.CONTENT_TOP_PADDING
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_BOTTOM_PADDING
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_CORNER_RADIUS
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_HEIGHT
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_TOP_PADDING
import app.n_zik.android.components.menu.MenuConstants.DRAG_HANDLE_WIDTH
import app.n_zik.android.topUiRoundnessShape

object GridMenu {

    @Composable
    fun Menu( showDragHandle: Boolean = true, title: String? = null, headerTrailing: (@Composable RowScope.() -> Unit)? = null, content: LazyGridScope.() -> Unit ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp
        val hasHeader = showDragHandle || title != null || headerTrailing != null

        Column(
            Modifier.heightIn( max = (screenHeight * CONTENT_HEIGHT_FRACTION).dp )
                       .fillMaxWidth()
                       .clip(topUiRoundnessShape())
                       .background(colorPalette().background0),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasHeader) {
                // Header with handle bar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(topUiRoundnessShape())
                        .background(colorPalette().background1)
                ) {
                    if (showDragHandle) {
                        Box(
                            modifier = Modifier
                                .padding(top = DRAG_HANDLE_TOP_PADDING.dp, bottom = DRAG_HANDLE_BOTTOM_PADDING.dp)
                                .size(width = DRAG_HANDLE_WIDTH.dp, height = DRAG_HANDLE_HEIGHT.dp)
                                .clip(RoundedCornerShape(DRAG_HANDLE_CORNER_RADIUS.dp))
                                .background(colorPalette().text)
                        )
                    }

                    if (headerTrailing != null) {
                        // Trailing action next to the title (e.g. refresh): an invisible
                        // mirror of the action on the left balances its exact width, so the
                        // title is truly centered on the screen (equal spacers alone would
                        // center it in the space left of the action). The mirror is alpha(0f)
                        // and semantics-cleared: invisible to the eye and to TalkBack. The
                        // trailing composable is composed twice — it is side-effect free in
                        // its current use (an IconButton), so this is safe. Without a
                        // trailing, the else branch renders the original block —
                        // pixel-identical for every existing menu.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .alpha(0f)
                                    .clearAndSetSemantics {}
                            ) {
                                headerTrailing()
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            title?.let {
                                Text(
                                    text = it,
                                    style = typography().m.copy(color = colorPalette().text),
                                    modifier = Modifier.padding(top = 5.dp, bottom = 10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            headerTrailing()
                        }
                    } else {
                        title?.let {
                            Text(
                                text = it,
                                style = typography().m.copy(color = colorPalette().text),
                                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp)
                            )
                        }
                    }

                    HorizontalDivider(Modifier.height(1.dp))
                }
            }

            // Grid content
            LazyVerticalGrid(
                columns = GridCells.Adaptive( minSize = 120.dp ),
                contentPadding = PaddingValues(
                    start = CONTENT_HORIZONTAL_PADDING.dp,
                    end = CONTENT_HORIZONTAL_PADDING.dp,
                    top = CONTENT_TOP_PADDING.dp,
                    bottom = 100.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(if (!hasHeader) topUiRoundnessShape() else RoundedCornerShape(0.dp))
                    .background(colorPalette().background0),
                content = content
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
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {},
        trailingContent: @Composable () -> Unit = {}
    ) {
        val alpha = if (enabled) 1f else 0.5f
        val isScrollingTextDisabled by rememberPreference( disableScrollingTextKey, false )

        Column(
            modifier = modifier
                .clip(gridMenuShape())
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(12.dp),
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
                        .background(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        ),
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
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .conditional( !isScrollingTextDisabled ) {
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
