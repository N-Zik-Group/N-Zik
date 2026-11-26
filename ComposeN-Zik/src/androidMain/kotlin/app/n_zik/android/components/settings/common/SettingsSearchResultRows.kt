package app.n_zik.android.components.settings.common

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.settings.accounts.isYouTubeSyncBuild
import app.n_zik.android.components.settings.isLastFmSettingsAvailable
import app.n_zik.android.extensions.pip.supportsPictureInPicture
import app.n_zik.android.surfaceColor
import app.n_zik.android.uiRoundnessShape

/** Card titles are navigation results; matching child options never expand the card. */
internal fun searchSettingsCardResults(
    context: Context,
    query: String,
    lastFmSupported: Boolean = isLastFmSettingsAvailable(),
): List<SettingsCardRegistryEntry> {
    val needle = normalizedSettingsText(query)
    if (needle.isEmpty()) return emptyList()
    return OrganizedSettingsCards.filter { it.matchesSearch(context, needle, lastFmSupported = lastFmSupported) }
}

internal fun SettingsCardRegistryEntry.duplicatesCategoryResult(context: Context, categories: List<SettingsCategory>): Boolean {
    val category = categories.firstOrNull { it.id == categoryId() } ?: return false
    return normalizedSettingsText(displayTitle(context)) == normalizedSettingsText(context.getString(category.labelRes))
}

internal fun SettingsCardRegistryEntry.matchesSearch(
    context: Context,
    normalizedQuery: String,
    youtubeSyncSupported: Boolean = isYouTubeSyncBuild(),
    lastFmSupported: Boolean = isLastFmSettingsAvailable(),
): Boolean {
    if (!youtubeSyncSupported && requiresYouTubeSyncBuild()) return false
    if (!lastFmSupported && titleRes == R.string.settings_lastfm_title) return false
    val orderedQueryTerms = normalizedQuery.split(' ').sortedByDescending(String::length)
    fun matches(label: String): Boolean {
        val text = normalizedSettingsText(label)
        return text.contains(normalizedQuery) || matchesSettingsTerms(text.split(' ').toSet(), orderedQueryTerms)
    }
    val keywords = when {
        !youtubeSyncSupported && titleRes == R.string.sync_data_with_ytm_account -> YouTubeSyncMasterKeywords
        titleRes == R.string.settings_picture_in_picture_title && !supportsPictureInPicture(context) ->
            keywordRes.filter { it == titleRes || it == R.string.settings_enable_pip }
        else -> keywordRes
    }
    return matches(displayTitle(context)) ||
        groupTitleRes?.let { matches(context.getString(it)) } == true ||
        keywords.any { matches(context.getString(it)) }
}

@Composable
internal fun SettingsEntryResultRow(
    result: SettingsEntryTarget,
    surfaceTag: String,
    rowTag: String,
    modifier: Modifier = Modifier,
    searchCategoryId: String? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    SettingsResultCard(testTag = surfaceTag, modifier = modifier) {
        OtherSettingsEntry(
            title = result.searchResultTitle(context),
            text = result.locationDescription(context, searchCategoryId),
            icon = result.parentCard(searchCategoryId).iconRes,
            modifier = Modifier.clip(uiRoundnessShape()).testTag(rowTag),
            onClick = onClick,
        )
    }
}

@Composable
internal fun SettingsCardResultRow(
    card: SettingsCardRegistryEntry,
    surfaceTag: String,
    rowTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val section = OrganizedSettingsCategories.first { it.id == card.categoryId() }
    SettingsResultCard(testTag = surfaceTag, modifier = modifier) {
        OtherSettingsEntry(
            title = card.displayTitle(LocalContext.current),
            text = listOfNotNull(
                stringResource(R.string.settings_title),
                stringResource(section.labelRes),
                card.groupTitleRes?.let { stringResource(it) },
            ).distinct().joinToString(" › "),
            icon = card.iconRes,
            modifier = Modifier.clip(uiRoundnessShape()).testTag(rowTag),
            onClick = onClick,
        )
    }
}

/** Shared compact surface for global and per-category search results. */
@Composable
internal fun SettingsResultCard(
    testTag: String,
    contentPadding: Dp = 16.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val containerColor = surfaceColor(colorPalette().background1)
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsCardSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag(testTag),
            shape = uiRoundnessShape(),
            containerColor = containerColor,
            backgroundColor = colorPalette().background0,
            shadowColor = colorPalette().accent.copy(alpha = 0.2f),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

