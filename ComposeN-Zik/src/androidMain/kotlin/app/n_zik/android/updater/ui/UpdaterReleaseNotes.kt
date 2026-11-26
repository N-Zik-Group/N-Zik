@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.n_zik.android.updater.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.appTextResources
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.NativeIconActionSize
import app.n_zik.android.components.ui.LeadingIconBadge
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.Languages
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.models.UpdaterConstants
import app.n_zik.android.updater.services.Updater
import app.n_zik.android.utils.bold
import app.n_zik.android.utils.contentTranslationTarget
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.semiBold

@Composable
internal fun ColumnScope.UpdaterReleaseNotes(
    isReinstalling: Boolean,
    hasUpdate: Boolean,
    currentVersion: String,
    newVersion: String,
    colorPaletteMode: ColorPaletteMode,
    isTranslationActive: Boolean,
    changelogTranslationTarget: Language?,
    onToggleTranslation: () -> Unit,
    onChooseTranslationLanguage: () -> Unit,
    headerModifier: Modifier = Modifier,
) {
    LaunchedEffect(isReinstalling, hasUpdate) {
        if (isReinstalling || !hasUpdate) {
            Updater.loadCachedChangelog()
            Updater.fetchCurrentChangelog()
        }
    }
    var currentChangelog by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        currentChangelog = withContext(NzikDispatchers.DATA) { readLocalReleaseNotes() }
    }
    val changelogTextToDisplay = if (isReinstalling || !hasUpdate) {
        Updater.currentChangelog?.takeIf { it.isNotBlank() }
            ?: currentChangelog
    } else {
        Updater.latestChangelog?.takeIf { it.isNotBlank() } ?: if (Updater.isFetchingChangelog) "" else
            Updater.githubRelease?.body?.takeIf { it.isNotBlank() } ?: currentChangelog
    }

    val translator = remember { Translator(NetworkClientFactory.getTranslatorClient()) }
    var translatedText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(changelogTextToDisplay, isTranslationActive, changelogTranslationTarget) {
        translatedText = null
        val destination = changelogTranslationTarget
        if (isTranslationActive && changelogTextToDisplay.isNotBlank() &&
            destination != null && destination != Language.ENGLISH
        ) {
            try {
                translatedText = withContext(NzikDispatchers.DATA) {
                    translator.translate(changelogTextToDisplay, destination, Language.ENGLISH).translatedText
                }
            } catch (e: Exception) {
                Timber.tag("UpdateScreen").e(e, "Translation failed")
            }
        }
    }

    if (changelogTextToDisplay.isNotBlank() || Updater.isFetchingChangelog) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(800)) + scaleIn(
                animationSpec = tween(800),
                initialScale = 0.9f
            )
        ) {
            UpdaterCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = surfaceColor(colorPalette().background1)
                ),
                shape = uiRoundnessShape(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = headerModifier.padding(bottom = 16.dp)
                    ) {
                        LeadingIconBadge(
                            iconRes = R.drawable.sparkles,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = stringResource(R.string.updates_changelog_title, if (hasUpdate && !isReinstalling) newVersion else stringResource(R.string.updates_current_version_format, currentVersion)),
                            modifier = Modifier.weight(1f).padding(end = 8.dp).basicMarquee(iterations = Int.MAX_VALUE),
                            maxLines = 1,
                            style = typography().m.bold.copy(color = colorPalette().text)
                        )
                        UpdaterIconAction(
                            modifier = Modifier.size(NativeIconActionSize),
                            onClick = {
                                if (changelogTranslationTarget == null) onChooseTranslationLanguage()
                                else onToggleTranslation()
                            },
                            onLongClick = onChooseTranslationLanguage,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.translate),
                                contentDescription = stringResource(R.string.common_action_translate),
                                tint = if (isTranslationActive && changelogTranslationTarget != null) {
                                    colorPalette().accent
                                } else colorPalette().textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (changelogTextToDisplay.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = colorPalette().accent,
                            )
                        }
                    } else {
                        ChangelogCard(
                            rawText = changelogTextToDisplay,
                            translatedText = if (isTranslationActive && changelogTranslationTarget != null) translatedText else null,
                            colorPaletteMode = colorPaletteMode
                        )
                    }
                }
            }
        }
    }
}

internal val Languages.changelogTranslationTarget: Language?
    get() = contentTranslationTarget

/** Reads the bundled fallback when the caller has no cached or remote notes. */
internal fun readLocalReleaseNotes(context: Context = appContext()): String =
    try {
        context.resources
            .openRawResource(R.raw.release_notes)
            .bufferedReader(Charsets.UTF_8)
            .readText()
    } catch (e: Exception) { "" }

/** Parses changelog headings and bulleted entries in their source order. */
fun parseChangelogText(text: String): List<Pair<String, List<String>>> {
    val sections = mutableListOf<Pair<String, List<String>>>()
    var currentTitle: String? = null
    val currentChanges = mutableListOf<String>()

    fun packSection() {
        val title = currentTitle
        if (title != null) {
            sections.add(title to currentChanges.toList())
            currentChanges.clear()
        }
    }

    text.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.endsWith(":") || trimmed.endsWith(" :") -> {
                packSection()
                currentTitle = trimmed.substringBeforeLast(":").trim()
            }
            trimmed.startsWith("-") -> {
                val change = trimmed.removePrefix("-").trim()
                if (change.isNotBlank()) {
                    currentChanges.add(change)
                }
            }
        }
    }
    packSection()

    if (sections.isEmpty() && currentChanges.isNotEmpty()) {
        val otherTitle = appTextResources().getString(R.string.common_other_section_title)
        sections.add(otherTitle to currentChanges.toList())
    }

    return sections
}

/** Renders release notes with section colors and icons derived from their headings. */
@Composable
fun ChangelogCard(rawText: String, translatedText: String?, colorPaletteMode: ColorPaletteMode) {
    val rawSections = remember(rawText) { parseChangelogText(rawText) }

    val displaySections = remember(rawText, translatedText) {
        val parsedTranslated = translatedText?.let { parseChangelogText(it) }

        if (parsedTranslated != null && parsedTranslated.size == rawSections.size) {
            rawSections.mapIndexed { index, rawSection ->
                Triple(rawSection.first, parsedTranslated[index].first, parsedTranslated[index].second)
            }
        } else {
            val fallbackSections = parsedTranslated ?: rawSections
            fallbackSections.map { Triple(it.first, it.first, it.second) }
        }
    }

    if (displaySections.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        displaySections.forEach { section ->
            var expanded by remember { mutableStateOf(true) }

            UpdaterCard(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                shape = uiRoundnessShape(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                glassContainerColor = surfaceColor(colorPalette().background1),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LeadingIconBadge(
                            iconRes = when (section.first.lowercase()) {
                                UpdaterConstants.CHANGELOG_ADDED -> R.drawable.add
                                UpdaterConstants.CHANGELOG_CHANGED -> R.drawable.pencil
                                UpdaterConstants.CHANGELOG_IMPROVED -> R.drawable.refresh_circle
                                UpdaterConstants.CHANGELOG_FIXED -> R.drawable.alert
                                UpdaterConstants.CHANGELOG_DEV -> R.drawable.sparkles
                                else -> R.drawable.information
                            },
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicText(
                            text = section.second,
                            style = typography().s.semiBold.copy(color = colorPalette().text)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(if (expanded) R.drawable.chevron_up else R.drawable.chevron_down),
                            contentDescription = null,
                            tint = colorPalette().textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            section.third.forEach { change ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(6.dp)
                                            .clip(uiRoundnessShape())
                                            .background(
                                                when (section.first.lowercase()) {
                                                    UpdaterConstants.CHANGELOG_ADDED -> Color(0xFF4CAF50)
                                                    UpdaterConstants.CHANGELOG_CHANGED -> Color(0xFFFF9800)
                                                    UpdaterConstants.CHANGELOG_IMPROVED -> Color(0xFF2196F3)
                                                    UpdaterConstants.CHANGELOG_FIXED -> Color(0xFFF44336)
                                                    UpdaterConstants.CHANGELOG_DEV -> Color(0xFFFFEB3B)
                                                    else -> colorPalette().accent
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    BasicText(
                                        text = change,
                                        style = typography().xs.copy(color = colorPalette().text),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
