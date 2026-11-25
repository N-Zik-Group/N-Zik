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
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.uiRoundnessShape

/** Card titles are navigation results; matching child options never expand the card. */
internal fun searchSettingsCardResults(context: Context, query: String): List<SettingsCardRegistryEntry> {
    val needle = normalizedSettingsText(query)
    if (needle.isEmpty()) return emptyList()
    return OrganizedSettingsCards.filter { it.matchesSearch(context, needle) }
}

internal fun SettingsCardRegistryEntry.matchesSearch(context: Context, normalizedQuery: String): Boolean =
    normalizedSettingsText(displayTitle(context)).contains(normalizedQuery) ||
        groupTitleRes?.let { normalizedSettingsText(context.getString(it)).contains(normalizedQuery) } == true ||
        keywordRes.any { normalizedSettingsText(context.getString(it)).contains(normalizedQuery) }

@Composable
internal fun SettingsEntryResultRow(
    result: SettingsEntryTarget,
    surfaceTag: String,
    rowTag: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    SettingsResultCard(testTag = surfaceTag) {
        OtherSettingsEntry(
            title = result.searchResultTitle(context),
            text = result.locationDescription(context),
            icon = result.parentCard().iconRes,
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
    onClick: () -> Unit,
) {
    val section = OrganizedSettingsCategories.first { it.id == card.categoryId() }
    SettingsResultCard(testTag = surfaceTag) {
        OtherSettingsEntry(
            title = card.displayTitle(LocalContext.current),
            text = stringResource(section.labelRes),
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
    content: @Composable () -> Unit,
) {
    val containerColor = surfaceColor(colorPalette().background1)
    Column(modifier = Modifier.fillMaxWidth()) {
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

