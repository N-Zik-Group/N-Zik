package app.n_zik.android.components.settings.ui

import android.content.Context
import android.os.Build
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.settings.common.SettingsCardRegistryEntry
import app.n_zik.android.components.settings.common.SettingsEntryTarget

/** One inventory supplies global results, subgroup breadcrumbs and lazy-list destinations. */
internal data class GlassSearchOption(
    val target: SettingsEntryTarget,
    val section: String,
    val sectionTitle: Int,
    val fallback: String? = null,
    val choiceRes: List<Int> = emptyList(),
    val requiredTintSource: GlassTintSource? = null,
)

private val hardwareGlassSearchSettings = GlassSettings()

internal fun isGlassSearchLabelSupported(context: Context, label: Int): Boolean = when (label) {
    R.string.glass_effect_color_vibrancy,
    R.string.glass_effect_frost,
    R.string.glass_effect_frost_strength -> glassRenderer(context, hardwareGlassSearchSettings) != GlassRenderer.Tint
    R.string.glass_effect_refraction_depth,
    R.string.glass_effect_refraction_amount,
    R.string.glass_effect_dispersion,
    R.string.glass_effect_depth_enabled -> glassRenderer(context, hardwareGlassSearchSettings) == GlassRenderer.Refraction
    else -> true
}

internal val GlassSearchParent = SettingsCardRegistryEntry(1, R.string.glass_effect_title, R.drawable.droplet, "glass")

internal fun availableGlassTintSources(): List<GlassTintSource> = GlassTintSource.entries.filter {
    it != GlassTintSource.SystemAccent || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

internal fun GlassTintSource.labelRes(): Int = when (this) {
    GlassTintSource.None -> R.string.glass_effect_tint_none
    GlassTintSource.ThemeAccent -> R.string.common_color_source_theme
    GlassTintSource.Artwork -> R.string.common_color_source_artwork
    GlassTintSource.SystemAccent -> R.string.theme_palette_material_you
    GlassTintSource.Custom -> R.string.glass_effect_tint_source_custom
}

internal val GlassSectionGroupTitles = mapOf(
    R.string.glass_effect_title to R.string.settings_group_glass_activation,
    R.string.glass_effect_group_material to R.string.settings_group_glass_rendering,
    R.string.glass_effect_group_color to R.string.settings_group_glass_tint,
    R.string.glass_effect_group_edges to R.string.settings_group_glass_refraction,
    R.string.glass_effect_group_readability to R.string.settings_group_glass_dimming,
    R.string.glass_effect_group_interactions to R.string.settings_group_glass_feedback,
)

internal val GlassSearchOptions: List<GlassSearchOption> = buildList {
    fun option(name: String, label: Int, section: String, sectionTitle: Int,
        description: Int? = null, fallback: String? = null, choices: List<Int> = emptyList(),
        requiredTintSource: GlassTintSource? = null) {
        add(GlassSearchOption(SettingsEntryTarget("glass.$name", 1, R.string.glass_effect_title, label,
            listOfNotNull(R.string.glass_effect_title, sectionTitle, GlassSectionGroupTitles[sectionTitle], description)),
            section, sectionTitle, fallback, choices, requiredTintSource))
    }
    option("master", R.string.glass_effect_enabled_title, "master", R.string.glass_effect_title, R.string.glass_effect_enabled_description)
    option("optimization", R.string.glass_effect_optimization_title, "material", R.string.glass_effect_group_material, R.string.glass_effect_optimization_description)
    option("quality", R.string.glass_effect_rendering_quality, "material", R.string.glass_effect_group_material,
        choices = listOf(R.string.glass_effect_quality_fastest, R.string.glass_effect_quality_balanced, R.string.glass_effect_quality_high))
    option("vibrancy", R.string.glass_effect_color_vibrancy, "material", R.string.glass_effect_group_material, fallback = "glass.quality")
    option("blur", R.string.glass_effect_frost, "material", R.string.glass_effect_group_material, fallback = "glass.quality")
    option("tintSource", R.string.glass_effect_tint_source, "colour", R.string.glass_effect_group_color,
        choices = availableGlassTintSources().map(GlassTintSource::labelRes))
    option("tint", R.string.glass_effect_custom_color, "colour", R.string.glass_effect_group_color,
        fallback = "glass.tintSource", requiredTintSource = GlassTintSource.Custom)
    option("hue", R.string.glass_effect_tint, "colour", R.string.glass_effect_group_color,
        fallback = "glass.tintSource", requiredTintSource = GlassTintSource.Custom)
    option("tintStrength", R.string.glass_effect_tint_strength, "colour", R.string.glass_effect_group_color, fallback = "glass.tintSource")
    option("film", R.string.glass_effect_film, "colour", R.string.glass_effect_group_color)
    option("indicator", R.string.glass_effect_active_tab_indicator, "colour", R.string.glass_effect_group_color)
    option("rim", R.string.glass_effect_rim, "edges", R.string.glass_effect_group_edges)
    option("refractionDepth", R.string.glass_effect_refraction_depth, "edges", R.string.glass_effect_group_edges, fallback = "glass.quality")
    option("refractionAmount", R.string.glass_effect_refraction_amount, "edges", R.string.glass_effect_group_edges, fallback = "glass.quality")
    option("dispersion", R.string.glass_effect_dispersion, "edges", R.string.glass_effect_group_edges, fallback = "glass.quality")
    option("depth", R.string.glass_effect_depth_enabled, "edges", R.string.glass_effect_group_edges, fallback = "glass.quality")
    option("dialogDimming", R.string.glass_effect_dialog_dimming, "readability", R.string.glass_effect_group_readability)
    option("menuDimming", R.string.glass_effect_menu_dimming, "readability", R.string.glass_effect_group_readability)
    option("ripple.enabled", R.string.glass_effect_ripple_enabled, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_ripple_description)
    option("ripple.intensity", R.string.glass_effect_ripple_intensity, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_ripple_intensity_description)
    option("ripple.size", R.string.glass_effect_ripple_size, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_ripple_size_description)
    option("jelly.enabled", R.string.glass_effect_jelly_enabled, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_jelly_description)
    option("jelly.intensity", R.string.glass_effect_jelly_intensity, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_jelly_intensity_description)
    GlassDestinationGroup.entries.forEach { group ->
        glassDestinationRows(group).forEach { row ->
            val modes = listOf(R.string.glass_effect_mode_disabled, R.string.glass_effect_mode_contour_only) +
                if (row.area == GlassArea.Images) emptyList() else
                    listOf(R.string.glass_effect_mode_glass_only, R.string.glass_effect_mode_glass_and_contour)
            add(GlassSearchOption(SettingsEntryTarget(row.searchId, 1, R.string.glass_effect_title,
                row.title, listOf(R.string.glass_effect_title, group.title, group.summary, row.description,
                    R.string.settings_group_glass_surface_modes)),
                "destination.${group.name}", group.title, choiceRes = modes))
        }
    }
}

internal fun glassSearchOption(id: String): GlassSearchOption? = GlassSearchOptions.firstOrNull { it.target.id == id }
