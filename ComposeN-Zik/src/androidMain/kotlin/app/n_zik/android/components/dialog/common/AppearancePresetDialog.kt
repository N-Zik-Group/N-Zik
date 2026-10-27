package app.n_zik.android.components.dialog.common

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.uiRoundnessShape

/** Appearance preview pager with the shared title and action bands. */
@Composable
fun AppearancePresetDialog(
    onDismiss: () -> Unit,
    onClick0: () -> Unit,
    onClick1: () -> Unit,
    onClick2: () -> Unit,
    onClick3: () -> Unit,
    onClick4: () -> Unit,
    onClick5: () -> Unit
) {
    val images = listOf(R.drawable.preset0,R.drawable.preset1,R.drawable.preset2,R.drawable.preset3,R.drawable.preset4,R.drawable.preset5)
    val pagerStateAppearance = rememberPagerState(pageCount = { images.size })

    DialogFrame(
        onDismiss = onDismiss,
        title = stringResource(R.string.appearancepresets),
        footer = {
            ButtonRow(listOf(
                DialogButton(stringResource(R.string.cancel), onClick = onDismiss),
                DialogButton(stringResource(R.string.dialog_apply), primary = true, onClick = {
                    when (pagerStateAppearance.currentPage) {
                        0 -> onClick0()
                        1 -> onClick1()
                        2 -> onClick2()
                        3 -> onClick3()
                        4 -> onClick4()
                        else -> onClick5()
                    }
                })
            ))
        }
    ) {
        Box {
            HorizontalPager(
                state = pagerStateAppearance,
                pageSize = PageSize.Fill,
                modifier = Modifier.fillMaxSize()
            ) { index ->
                Image(
                    painter = painterResource(images[index]),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Row(
                Modifier
                    .height(20.dp)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(images.size) { iteration ->
                    val lineWidth = animateDpAsState(
                        targetValue = when {
                            pagerStateAppearance.currentPage == iteration -> 15.dp
                            iteration < pagerStateAppearance.currentPage -> 5.dp
                            else -> 10.dp
                        },
                        label = "width",
                        animationSpec = tween(300, easing = EaseInOut)
                    )
                    val color = if (pagerStateAppearance.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(width = lineWidth.value, height = 5.dp)
                            .clip(uiRoundnessShape())
                            .background(color)
                    )
                }
            }
        }
    }
}
