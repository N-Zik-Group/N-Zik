package app.n_zik.android.components.musicbrainz


import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassControlOverflowColumn
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.components.glass.GlassTitleActionRow
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.header.headerActionEndPadding
import app.n_zik.android.components.ui.screens.album.Translate
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.utils.shimmerEffect
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.n_zik.android.utils.align
import app.n_zik.android.utils.color
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
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
    translate: Translate? = null,
    translator: Translator? = null,
    languageDestination: Language? = null,
    lastSyncAt: Long? = null,
    lastSyncFailed: Boolean = false,
    pausedForSeconds: Long = 0,
    isSyncing: Boolean = false,
    isLoading: Boolean = false,
    onResyncClick: () -> Unit = {},
    onInsightsClick: () -> Unit = {},
) {
    var readMore by remember { mutableStateOf(true) }
    val forceShimmer = forceLoadingShimmerPreview() || isLoading
    val glass = rememberGlassSettings()
    val materialActions = glass.usesGlassMaterial(GlassArea.ActionButtons)

    val cleanedBio = bio?.cleanWikipediaText()

    var translatedBio by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cleanedBio, translate?.isActive == true, languageDestination) {
        translatedBio = null
        if (translator != null && translate?.isActive == true && languageDestination != null && !cleanedBio.isNullOrBlank()) {
            val result = withContext(NzikDispatchers.DATA) {
                runCatching {
                    translator.translate(cleanedBio, languageDestination, Language.AUTO).translatedText
                }.getOrNull()
            }
            translatedBio = result
        }
    }

    GlassControlOverflowColumn(
        modifier = modifier
            .fillMaxWidth()
    ) {
        GlassTitleActionRow(
            modifier = Modifier.fillMaxWidth(),
            verticalPadding = 0.dp,
            contentPadding = PaddingValues(start = 16.dp, end = headerActionEndPadding(materialActions), top = 24.dp, bottom = 8.dp),
            hasTrailingActions = true,
            onClick = if (materialActions) null else ({ readMore = !readMore }),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.metadata_info_and_community_title),
                    style = typography().m.semiBold
                )
                val syncText = when {
                    pausedForSeconds > 0 -> stringResource(
                        R.string.metadata_musicbrainz_last_sync_paused,
                        (pausedForSeconds / 60).coerceAtLeast(1)
                    )
                    lastSyncAt == null -> stringResource(R.string.metadata_musicbrainz_last_sync_never)
                    lastSyncFailed -> stringResource(
                        R.string.metadata_musicbrainz_last_sync_failed,
                        DateUtils.getRelativeTimeSpanString(lastSyncAt).toString()
                    )
                    else -> stringResource(
                        R.string.metadata_musicbrainz_last_sync,
                        DateUtils.getRelativeTimeSpanString(lastSyncAt).toString()
                    )
                }
                val syncStyle = if (pausedForSeconds > 0 || lastSyncFailed)
                    typography().xxs.semiBold.color(colorPalette().red)
                else typography().xxs.secondary
                if (forceShimmer) MeasuredMetadataPlaceholder(syncText, syncStyle)
                else Text(text = syncText, style = syncStyle)
            }
            val resyncDescription = stringResource(R.string.metadata_musicbrainz_resync)
            val expandDescription = stringResource(R.string.metadata_info_and_community_title)
            GlassSectionActions(
                actions = buildList {
                    add(GlassSectionAction(R.drawable.refresh, resyncDescription,
                        loading = isSyncing, onClick = onResyncClick))
                    add(GlassSectionAction(
                        if (readMore) R.drawable.chevron_up else R.drawable.chevron_down,
                        expandDescription,
                        separateBefore = true,
                        onClick = { readMore = !readMore },
                    ))
                },
                settings = glass,
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
                if (forceShimmer) {
                    if (rating != null && rating != 0f) {
                        CommunityPlaceholder(Modifier.padding(vertical = 8.dp).width(if ((ratingVotes ?: 0) > 0) 80.dp else 44.dp).height(16.dp))
                    }
                    if (rating != null) Spacer(Modifier.padding(horizontal = 20.dp))
                    if (year != null) {
                        CommunityPlaceholder(Modifier.padding(vertical = 8.dp).width(if (countryCode == null) 48.dp else 65.dp).height(16.dp))
                    }
                } else {
                    RatingBar(rating, ratingVotes)
                    if (rating != null) Spacer(Modifier.padding(horizontal = 20.dp))
                    InfoBar(year, countryCode)
                }
            }

            AnimatedVisibility(readMore && !keywords.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.metadata_keywords_title),
                        color = colorPalette().text,
                        style = typography().xs.semiBold
                    )

                    KeywordChips(keywords, isLoading = isLoading)
                }
            }

            AnimatedVisibility(readMore && !links.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.metadata_external_links_title),
                        color = colorPalette().text,
                        style = typography().xs.semiBold
                    )
                    if (forceShimmer) {
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            links.orEmpty().filter { it.platform.isNotBlank() }.forEach { _ ->
                                CommunityPlaceholder(Modifier.size(20.dp))
                            }
                        }
                    } else ExternalLinksSection(links)
                }
            }

            AnimatedVisibility(readMore && !cleanedBio.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.metadata_biography_title),
                        color = colorPalette().text,
                        style = typography().xs.semiBold
                    )
                    cleanedBio?.let { description ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            translate?.ToolBarButton()

                            BasicText(
                                text = "“",
                                style = typography().xxs.semiBold,
                                modifier = Modifier
                                    .offset(y = (-8).dp)
                                    .align(Alignment.Top)
                            )

                            if (forceShimmer) {
                                MeasuredMetadataPlaceholder(
                                    translatedBio ?: description,
                                    typography().xxs.secondary.align(TextAlign.Justify),
                                    Modifier.padding(horizontal = 8.dp).weight(1f),
                                )
                            } else BasicText(
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

        GlassTitleActionRow(
            modifier = Modifier.fillMaxWidth(),
            verticalPadding = 0.dp,
            contentPadding = PaddingValues(start = 16.dp, end = headerActionEndPadding(materialActions), top = 24.dp, bottom = 8.dp),
            hasTrailingActions = true,
            onClick = if (materialActions) null else onInsightsClick,
        ) {
            Text(
                text = stringResource(R.string.metadata_musicbrainz_insights),
                style = typography().m.semiBold,
                modifier = Modifier.weight(1f)
            )
            GlassSectionActions(
                actions = listOf(GlassSectionAction(
                    R.drawable.chevron_forward, stringResource(R.string.metadata_musicbrainz_insights), onClick = onInsightsClick,
                )),
                settings = glass,
            )
        }
    }
}

@Composable
private fun CommunityPlaceholder(modifier: Modifier) {
    Spacer(modifier.clip(uiRoundnessShape()).shimmerEffect())
}
