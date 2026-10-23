package app.n_zik.android.components.dialog.common

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.n_zik.android.uiRoundnessShape

/** Full-screen pager of appearance preset screenshots (moved verbatim from the legacy tree — it is a dedicated full-screen selection dialog, not a framed one). */
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(color = colorPalette().background1)
        ){
            Box(
                modifier = Modifier
            ){
                HorizontalPager(
                    state = pagerStateAppearance,
                    pageSize = PageSize.Fill,
                    modifier = Modifier
                        .fillMaxSize()
                ) { index ->
                    Image(
                        painter = painterResource(images[index]),
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        colorFilter = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(bottom = 30.dp)
                        .padding(end = 15.dp)
                        .background(colorPalette().accent, CircleShape)
                        .align(Alignment.BottomEnd),
                ) {
                    IconButton(
                        icon = R.drawable.checkmark,
                        color = colorPalette().background0,
                        indication = ripple(false),
                        onClick = {
                            when (pagerStateAppearance.currentPage) {
                                0 -> onClick0()
                                1 -> onClick1()
                                2 -> onClick2()
                                3 -> onClick3()
                                4 -> onClick4()
                                else -> onClick5()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(30.dp)
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
                            targetValue = if (pagerStateAppearance.currentPage == iteration) {15.dp} else {
                                if (iteration < pagerStateAppearance.currentPage) {5.dp} else {10.dp}
                            }, label = "width", animationSpec = tween(300, easing = EaseInOut)
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
}
