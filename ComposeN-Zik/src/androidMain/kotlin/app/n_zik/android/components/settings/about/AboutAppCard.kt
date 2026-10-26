package app.n_zik.android.components.settings.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.bold
import app.it.fast4x.rimusic.utils.secondary
import app.kreate.android.drawable.APP_ICON_IMAGE_BITMAP
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.models.UpdateEndpoints

/** Preserves the original About App content. */
@Composable
internal fun AboutAppCard() {
    val uriHandler = LocalUriHandler.current
    AnimatedVisibility(visible = true, enter = settingsCardEnter, exit = settingsCardExit) {
                         Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
             ) {
                 Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .height(IntrinsicSize.Max),
                     horizontalArrangement = Arrangement.spacedBy(12.dp)
                 ) {

                     Card(
                         modifier = Modifier
                             .weight(1f)
                             .fillMaxHeight()
                    .shadow(
                        elevation = 8.dp,
                        shape = uiRoundnessShape(),
                        spotColor = colorPalette().accent.copy(alpha = 0.3f)
                    ),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = surfaceColor(colorPalette().background1)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                         modifier = Modifier
                             .fillMaxHeight()
                             .padding(20.dp),
                         horizontalAlignment = Alignment.CenterHorizontally,
                         verticalArrangement = Arrangement.Top
                     ) {

                         Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                     Box(
                         modifier = Modifier
                                     .size(60.dp)
                             .background(
                                 brush = Brush.radialGradient(
                                     colors = listOf(
                                         colorPalette().accent.copy(alpha = 0.1f),
                                         colorPalette().accent.copy(alpha = 0.05f)
                                     )
                                 ),
                        shape = CircleShape
                             ),
                         contentAlignment = Alignment.Center
                     ) {
                         Image(
                             bitmap = APP_ICON_IMAGE_BITMAP,
                    contentDescription = null,
                                     modifier = Modifier.size(28.dp)
                )
            }

                             Spacer(modifier = Modifier.height(12.dp))

            val pkgManager = appContext().packageManager
                     val appInfo = pkgManager.getApplicationInfo(appContext().packageName, 0)
            BasicText(
                         text = pkgManager.getApplicationLabel(appInfo).toString(),
                style = TextStyle(
                                     fontSize = typography().l.bold.fontSize,
                                     fontWeight = typography().l.bold.fontWeight,
                    color = colorPalette().text,
                             textAlign = TextAlign.Center
                         ),
                         modifier = Modifier.fillMaxWidth()
                     )



                        }

                        Spacer(modifier = Modifier.weight(1f))                                                 // Bottom content - Author & Changelog
                         Column(
                             horizontalAlignment = Alignment.CenterHorizontally,
                             verticalArrangement = Arrangement.spacedBy(8.dp)
                         ) {

                         Row(
                             horizontalArrangement = Arrangement.Center,
                             verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .clip(uiRoundnessShape()).clickable {
                                     val url = "${UpdateEndpoints.GITHUB}/${UpdateEndpoints.REAL_OWNER}"
                                     uriHandler.openUri(url)
                                 }
                         ) {
                             BasicText(
                                 text = stringResource(R.string.by_string),
                                     style = typography().xs.secondary.copy(
                                     textAlign = TextAlign.Center
                                 ),
                             )
                             Spacer(modifier = Modifier.width(5.dp))
                             Icon(
                                 painter = painterResource(R.drawable.github_icon),
                                 tint = colorPalette().accent,
                                 contentDescription = null,
                                     modifier = Modifier.size(14.dp)
                             )
                                 Spacer(modifier = Modifier.width(3.dp))
                             BasicText(
                                 text = UpdateEndpoints.REAL_OWNER,
                                     style = typography().xs.secondary.copy(
                                     textDecoration = TextDecoration.Underline,
                                     color = colorPalette().accent,
                                     textAlign = TextAlign.Center
                                 )
                             )
                          }
                     }
                }
            }

             }
        }
    }
}
