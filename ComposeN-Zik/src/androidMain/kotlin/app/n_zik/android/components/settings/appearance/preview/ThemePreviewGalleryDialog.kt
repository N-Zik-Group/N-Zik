package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.LocalIndependentGlassBackdrop
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

/** The shared fullscreen viewer for published themes and submission previews. */
@Composable
internal fun <T> ThemePreviewGalleryDialog(
    previews: List<T>,
    initialPage: Int,
    onDismiss: () -> Unit,
    itemKey: (T) -> String,
    caption: @Composable (T) -> String,
    image: @Composable (T) -> Unit,
) {
    if (initialPage !in previews.indices) return
    val pager = rememberPagerState(initialPage = initialPage) { previews.size }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { itemKey(previews[it]) },
            ) { page ->
                Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    image(previews[page])
                }
            }
            val palette = colorPalette()
            Text(
                caption(previews[pager.currentPage]),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = Color.White,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            if (previews.size > 1) {
                Row(Modifier.fillMaxWidth(.75f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(previews.size) { index ->
                        val color = if (index == pager.currentPage) palette.accent else Color.White.copy(alpha = .22f)
                        Box(Modifier.weight(1f).height(3.dp).background(color, RoundedCornerShape(percent = 50)))
                    }
                }
            }
            val galleryBackdrop = rememberCanvasBackdrop { drawRect(Color.Black) }
            CompositionLocalProvider(LocalIndependentGlassBackdrop provides galleryBackdrop) {
                PageButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                ) {
                    Text(stringResource(R.string.common_action_close))
                }
            }
        }
    }
}
