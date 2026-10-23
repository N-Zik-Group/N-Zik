package app.n_zik.android.components.musicbrainz

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.screens.album.Translate
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.align
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.musicbrainz.models.ExternalLink
import app.n_zik.android.musicbrainz.utils.cleanWikipediaText
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/** Collapsible "Info and Community" section with MusicBrainz metadata (rating, year/country, keywords, external links, bio) and the Insights entry. */
@Composable
fun InfoAndCommunity(
    modifier: Modifier = Modifier,
    rating: Float?,
    ratingVotes: Int?,
    year: String?,
    countryCode: String?,
    keywords: List<String>?,
    links: List<ExternalLink>?,
    bio: String?,
    translate: Translate,
    translator: Translator,
    languageDestination: Language,
    lastSyncAt: Long? = null,
    lastSyncFailed: Boolean = false,
    pausedForSeconds: Long = 0,
    isSyncing: Boolean = false,
    onResyncClick: () -> Unit = {},
    onInsightsClick: () -> Unit
) {
    var readMore by remember { mutableStateOf(true) }

    val cleanedBio = bio?.cleanWikipediaText()

    var translatedBio by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cleanedBio, translate.isActive) {
        if (translate.isActive && !cleanedBio.isNullOrBlank()) {
            val result = withContext(NzikDispatchers.DATA) {
                runCatching {
                    translator.translate(cleanedBio, languageDestination, Language.AUTO).translatedText
                }.getOrNull()
            }
            translatedBio = result
        } else {
            translatedBio = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(uiRoundnessShape())
                .clickable {
                    readMore = !readMore
                }
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.title_info_and_community),
                    style = typography().m.semiBold
                )
                Text(
                    text = when {
                        pausedForSeconds > 0 -> stringResource(
                            R.string.mb_last_sync_paused,
                            (pausedForSeconds / 60).coerceAtLeast(1)
                        )
                        lastSyncAt == null -> stringResource(R.string.mb_last_sync_never)
                        lastSyncFailed -> stringResource(
                            R.string.mb_last_sync_failed,
                            DateUtils.getRelativeTimeSpanString(lastSyncAt).toString()
                        )
                        else -> stringResource(
                            R.string.mb_last_sync,
                            DateUtils.getRelativeTimeSpanString(lastSyncAt).toString()
                        )
                    },
                    style = if (pausedForSeconds > 0 || lastSyncFailed)
                        typography().xxs.semiBold.color(colorPalette().red)
                    else
                        typography().xxs.secondary
                )
            }
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(18.dp),
                    color = colorPalette().textSecondary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = stringResource(R.string.mb_resync),
                    tint = colorPalette().textSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onResyncClick() }
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
            Icon(
                painter = painterResource(if (readMore) R.drawable.chevron_up else R.drawable.chevron_down),
                contentDescription = null,
                tint = colorPalette().textSecondary
            )
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(uiRoundnessShape())
                    .clickable {
                        readMore = !readMore
                    }
                    .padding(bottom = 8.dp)
            ) {
                RatingBar(rating, ratingVotes)
                if (rating != null)
                    Spacer(modifier = Modifier.padding(horizontal = 20.dp))

                InfoBar(year, countryCode)
            }

            AnimatedVisibility(readMore && !keywords.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.title_keywords),
                        color = colorPalette().text,
                        style = typography().xs.semiBold
                    )

                    KeywordChips(keywords)
                }
            }

            AnimatedVisibility(readMore && !links.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.title_external_links),
                        color = colorPalette().text,
                        style = typography().xs.semiBold
                    )
                    ExternalLinksSection(links)
                }
            }

            AnimatedVisibility(readMore && !cleanedBio.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.title_bio),
                        color = colorPalette().text,
                        style = typography().xs.semiBold
                    )
                    cleanedBio?.let { description ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            translate.ToolBarButton()

                            BasicText(
                                text = "“",
                                style = typography().xxs.semiBold,
                                modifier = Modifier
                                    .offset(y = (-8).dp)
                                    .align(Alignment.Top)
                            )

                            BasicText(
                                text = translatedBio ?: description,
                                style = typography().xxs.secondary.align(TextAlign.Justify),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .weight(1f)
                            )

                            BasicText(
                                text = "”",
                                style = typography().xxs.semiBold,
                                modifier = Modifier
                                    .offset(y = 4.dp)
                                    .align(Alignment.Bottom)
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(uiRoundnessShape())
                .clickable {
                    onInsightsClick()
                }
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.mb_insights),
                style = typography().m.semiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.chevron_forward),
                contentDescription = null,
                tint = colorPalette().textSecondary
            )
        }
    }
}
