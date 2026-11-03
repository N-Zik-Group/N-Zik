package app.n_zik.android.components.settings.ui

import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.settings.common.SettingsCardRegistryEntry
import app.n_zik.android.components.settings.common.SettingsEntryTarget

/** One inventory supplies global results, subgroup breadcrumbs and lazy-list destinations. */
internal data class GlassSearchOption(
    val target: SettingsEntryTarget,
    val section: String,
    val sectionTitle: Int,
    val fallback: String? = null,
)

internal val GlassSearchParent = SettingsCardRegistryEntry(1, R.string.glass_effect_title, R.drawable.color_palette, "glass")

internal val GlassSearchOptions: List<GlassSearchOption> = buildList {
    fun option(name: String, label: Int, section: String, sectionTitle: Int,
        description: Int? = null, fallback: String? = null, choices: List<Int> = emptyList()) {
        add(GlassSearchOption(SettingsEntryTarget("glass.$name", 1, R.string.glass_effect_title, label,
            listOfNotNull(R.string.glass_effect_title, sectionTitle, description) + choices), section, sectionTitle, fallback))
    }
    option("master", R.string.glass_effect_enabled_title, "master", R.string.glass_effect_title, R.string.glass_effect_enabled_description)
    option("optimization", R.string.glass_effect_optimization_title, "material", R.string.glass_effect_group_material, R.string.glass_effect_optimization_description)
    option("ripple.enabled", R.string.glass_effect_ripple_enabled, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_ripple_description)
    option("ripple.intensity", R.string.glass_effect_ripple_intensity, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_ripple_intensity_description)
    option("ripple.size", R.string.glass_effect_ripple_size, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_ripple_size_description)
    option("jelly.enabled", R.string.glass_effect_jelly_enabled, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_jelly_description)
    option("jelly.intensity", R.string.glass_effect_jelly_intensity, "interactions", R.string.glass_effect_group_interactions, R.string.glass_effect_jelly_intensity_description)
    option("quality", R.string.settings_network_quality_title, "material", R.string.glass_effect_group_material,
        choices = listOf(R.string.glass_effect_quality_fastest, R.string.glass_effect_quality_balanced, R.string.glass_effect_quality_high))
    option("vibrancy", R.string.glass_effect_color_vibrancy, "material", R.string.glass_effect_group_material, fallback = "glass.quality")
    option("blur", R.string.glass_effect_frost, "material", R.string.glass_effect_group_material, fallback = "glass.quality")
    option("tintSource", R.string.glass_effect_tint_source, "colour", R.string.glass_effect_group_color,
        choices = listOf(R.string.glass_effect_tint_none, R.string.common_color_source_theme, R.string.common_color_source_artwork,
            R.string.glass_effect_tint_source_system_accent, R.string.glass_effect_tint_source_custom))
    option("tint", R.string.glass_effect_custom_color, "colour", R.string.glass_effect_group_color, fallback = "glass.tintSource")
    option("hue", R.string.glass_effect_tint, "colour", R.string.glass_effect_group_color, fallback = "glass.tintSource")
    option("tintStrength", R.string.glass_effect_tint_strength, "colour", R.string.glass_effect_group_color, fallback = "glass.tintSource")
    option("film", R.string.glass_effect_film, "colour", R.string.glass_effect_group_color)
    option("indicator", R.string.glass_effect_active_tab_indicator, "colour", R.string.glass_effect_group_color)
    option("dialogDimming", R.string.glass_effect_dialog_dimming, "readability", R.string.glass_effect_group_readability)
    option("menuDimming", R.string.glass_effect_menu_dimming, "readability", R.string.glass_effect_group_readability)
    option("rim", R.string.glass_effect_rim, "edges", R.string.glass_effect_group_edges)
    option("refractionDepth", R.string.glass_effect_refraction_depth, "edges", R.string.glass_effect_group_edges, fallback = "glass.quality")
    option("refractionAmount", R.string.glass_effect_refraction_amount, "edges", R.string.glass_effect_group_edges, fallback = "glass.quality")
    option("dispersion", R.string.glass_effect_dispersion, "edges", R.string.glass_effect_group_edges, fallback = "glass.quality")
    option("depth", R.string.glass_effect_depth_enabled, "edges", R.string.glass_effect_group_edges, fallback = "glass.quality")
    GlassDestinationGroup.entries.forEach { group ->
        glassDestinationRows(group).forEach { row ->
            add(GlassSearchOption(SettingsEntryTarget(row.searchId, 1, R.string.glass_effect_title,
                row.title, listOf(R.string.glass_effect_title, group.title, group.summary, row.description,
                    R.string.glass_effect_mode_disabled, R.string.glass_effect_mode_contour_only) +
                    if (row.area == GlassArea.Images) emptyList() else
                        listOf(R.string.glass_effect_mode_glass_only, R.string.glass_effect_mode_glass_and_contour)),
                "destination.${group.name}", group.title))
        }
    }
}

internal fun glassSearchOption(id: String): GlassSearchOption? = GlassSearchOptions.firstOrNull { it.target.id == id }
