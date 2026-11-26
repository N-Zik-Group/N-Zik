package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.GlassTextField as TextField
import app.n_zik.android.components.glass.LocalIndependentGlassBackdrop
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.updater.models.UpdateEndpoints
import app.n_zik.android.components.settings.common.SettingsBottomPanel

@Composable
internal fun ThemeSubmissionLoading(padding: PaddingValues, dismiss: () -> Unit) {
    val footerGlass = rememberGlassSettings()
    val footerBackdrop = rememberLayerBackdrop()
    val captureFooter = footerGlass.usesGlassMaterial(GlassArea.SettingsCards) &&
        glassRenderer(footerGlass) != GlassRenderer.Tint
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorPalette().background0,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            SubmissionFooter(
                backdrop = footerBackdrop.takeIf { captureFooter },
                onBack = dismiss,
                onContinue = {},
                backLabel = R.string.common_action_cancel,
                continueLabel = R.string.theme_submission_continue,
                backEnabled = true,
                continueEnabled = false,
            )
        },
    ) { footerPadding ->
        Column(
            Modifier.fillMaxSize()
                .background(colorPalette().background0)
                .then(if (captureFooter) Modifier.layerBackdrop(footerBackdrop) else Modifier)
                .padding(top = padding.calculateTopPadding()),
        ) {
            SubmissionStepper(step = 0)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(bottom = footerPadding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SubmissionInformationCard("", {}, "", {}, "", {}, loading = true)
                SettingsSectionCard(
                    stringResource(R.string.theme_submission_author),
                    R.drawable.github_icon,
                    bottomSpacing = 0.dp,
                    content = {
                        Column {
                            StoreLoadingLine(null, typography().s, width = 0.42f)
                            StoreLoadingLine(null, typography().xs, width = 0.34f)
                        }
                    },
                )
                SubmissionMinimumVersionCard(value = "", onValueChanged = {}, loading = true)
            }
        }
    }
}

@Composable
internal fun SubmissionMinimumVersionCard(
    value: String,
    onValueChanged: (String) -> Unit,
    invalid: Boolean = false,
    loading: Boolean = false,
    validation: @Composable () -> Unit = {},
) {
    SettingsSectionCard(
        stringResource(R.string.theme_store_submission_minimum_version),
        R.drawable.information,
        bottomSpacing = 0.dp,
        content = {
            SettingsDescription(stringResource(
                R.string.theme_store_submission_minimum_description,
                BuildConfig.VERSION_NAME.substringBefore('-'),
                BuildConfig.VERSION_CODE,
                FirstThemeStoreVersionCode,
            ))
            TextField(
                value = value,
                onValueChange = onValueChanged,
                modifier = Modifier.fillMaxWidth().storeLoadingContent(loading),
                enabled = !loading,
                label = { Text(stringResource(R.string.theme_store_submission_version_code_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = invalid,
                colors = InputDialog.defaultTextFieldColors(),
            )
            val versionCodeLink = stringResource(R.string.theme_store_submission_find_version_code)
            val linkStyle = SpanStyle(color = colorPalette().accent, textDecoration = TextDecoration.Underline)
            Text(
                text = buildAnnotatedString {
                    withLink(LinkAnnotation.Url(
                        url = UpdateEndpoints.REPO_URL + "/blob/main/Updater/README.md",
                        styles = TextLinkStyles(style = linkStyle),
                    )) {
                        append(versionCodeLink)
                    }
                },
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
            validation()
        },
    )
}

@Composable
internal fun SubmissionInformationCard(
    title: String,
    onTitleChanged: (String) -> Unit,
    description: String,
    onDescriptionChanged: (String) -> Unit,
    tags: String,
    onTagsChanged: (String) -> Unit,
    loading: Boolean = false,
    extraContent: @Composable () -> Unit = {},
) {
    SettingsSectionCard(
        stringResource(R.string.theme_submission_information_title),
        R.drawable.ui,
        bottomSpacing = 0.dp,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubmissionField(title, onTitleChanged, stringResource(R.string.theme_submission_title), loading = loading)
                SubmissionField(description, onDescriptionChanged, stringResource(R.string.theme_submission_description), lines = 3, loading = loading)
                SubmissionField(tags, onTagsChanged, stringResource(R.string.theme_store_tags_entry), loading = loading)
                extraContent()
            }
        },
    )
}

@Composable
internal fun SubmissionFooter(
    backdrop: LayerBackdrop?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    backLabel: Int,
    continueLabel: Int,
    backEnabled: Boolean,
    continueEnabled: Boolean,
) {
    CompositionLocalProvider(
        LocalIndependentGlassBackdrop provides LocalIndependentGlassBackdrop.current.takeIf { backdrop == null },
    ) {
        SettingsBottomPanel(
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val palette = colorPalette()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    enabled = backEnabled,
                    elevation = null,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.red),
                ) {
                    Text(stringResource(backLabel))
                }
                PageButton(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                    enabled = continueEnabled,
                    elevation = null,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                ) {
                    Text(stringResource(continueLabel))
                }
            }
        }
    }
}

@Composable
internal fun SubmissionStepper(step: Int) {
    val sections = listOf(stringResource(R.string.theme_submission_section_information), stringResource(R.string.theme_submission_section_previews), stringResource(R.string.theme_submission_section_review))
    val palette = colorPalette()
    val glass = rememberGlassSettings()
    val area = GlassArea.ActionButtons
    val shape = uiRoundnessShape()
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        sections.forEachIndexed { index, section ->
            val active = index <= step
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                Row(Modifier.fillMaxWidth().padding(top = 13.dp)) {
                    Spacer(Modifier.weight(1f).height(2.dp).background(
                        if (index == 0) Color.Transparent
                        else if (active) palette.accent else palette.background1))
                    Spacer(Modifier.weight(1f).height(2.dp).background(
                        if (index == sections.lastIndex) Color.Transparent
                        else if (index < step) palette.accent else palette.background1))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val tone = if (active) palette.accent else palette.background1
                    val marker = if (glass.usesGlassMaterial(area)) {
                        Modifier.glassMaterial(glass.forArea(area), shape, filmColor = tone, drawShadow = false)
                    } else {
                        Modifier.background(tone, shape).glassAreaContour(glass, area, shape)
                    }
                    Box(Modifier.size(28.dp).then(marker), contentAlignment = Alignment.Center) {
                        if (index < step) {
                            Icon(painterResource(R.drawable.checkmark), contentDescription = null,
                                modifier = Modifier.size(16.dp), tint = palette.onAccent)
                        } else {
                            Text("${index + 1}", style = typography().xs,
                                color = if (active) palette.onAccent else palette.text)
                        }
                    }
                    Text(section, style = typography().xs, color = if (index == step) palette.text else palette.textSecondary)
                }
            }
        }
    }
}

@Composable
internal fun SubmissionField(value: String, onChange: (String) -> Unit, label: String, lines: Int = 1, loading: Boolean = false) {
    TextField(value, onChange, Modifier.fillMaxWidth().storeLoadingContent(loading), enabled = !loading, label = { Text(label) },
        minLines = lines, singleLine = lines == 1, shape = uiRoundnessShape(),
        colors = InputDialog.defaultTextFieldColors())
}

@Composable
internal fun SubmissionAuthor(login: String) {
    Column {
        Text(login, style = typography().s)
        Text("@$login", style = typography().xs, color = colorPalette().textSecondary)
    }
}

@Composable
internal fun SubmissionSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = typography().s)
        Text(value, style = typography().xs, color = colorPalette().textSecondary)
    }
}
