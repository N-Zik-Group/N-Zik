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

internal val GlassSearchParent = SettingsCardRegistryEntry(1, R.string.liquid_glass, R.drawable.color_palette, "glass")

internal val GlassSearchOptions: List<GlassSearchOption> = buildList {
    fun option(name: String, label: Int, section: String, sectionTitle: Int,
        description: Int? = null, fallback: String? = null, choices: List<Int> = emptyList()) {
        add(GlassSearchOption(SettingsEntryTarget("glass.$name", 1, R.string.liquid_glass, label,
            listOfNotNull(R.string.liquid_glass, sectionTitle, description) + choices), section, sectionTitle, fallback))
    }
    option("master", R.string.liquid_glass_enable, "master", R.string.liquid_glass, R.string.liquid_glass_enable_desc)
    option("ripple.enabled", R.string.glass_ripple_enabled, "interactions", R.string.glass_group_interactions, R.string.glass_ripple_description)
    option("ripple.intensity", R.string.glass_ripple_intensity, "interactions", R.string.glass_group_interactions, R.string.glass_ripple_intensity_description)
    option("ripple.size", R.string.glass_ripple_size, "interactions", R.string.glass_group_interactions, R.string.glass_ripple_size_description)
    option("jelly.enabled", R.string.glass_jelly_enabled, "interactions", R.string.glass_group_interactions, R.string.glass_jelly_description)
    option("jelly.intensity", R.string.glass_jelly_intensity, "interactions", R.string.glass_group_interactions, R.string.glass_jelly_intensity_description)
    option("quality", R.string.quality, "material", R.string.glass_group_material,
        choices = listOf(R.string.glass_performance, R.string.glass_balanced, R.string.glass_quality))
    option("vibrancy", R.string.glass_vibrancy, "material", R.string.glass_group_material, fallback = "glass.quality")
    option("blur", R.string.glass_blur, "material", R.string.glass_group_material, fallback = "glass.quality")
    option("tintSource", R.string.liquid_glass_tint_source, "colour", R.string.glass_group_colour,
        choices = listOf(R.string.glass_tint_none, R.string.liquid_glass_theme_accent, R.string.liquid_glass_artwork,
            R.string.liquid_glass_system_accent, R.string.liquid_glass_custom))
    option("tint", R.string.liquid_glass_color, "colour", R.string.glass_group_colour, fallback = "glass.tintSource")
    option("hue", R.string.glass_tint, "colour", R.string.glass_group_colour, fallback = "glass.tintSource")
    option("tintStrength", R.string.glass_tint_strength, "colour", R.string.glass_group_colour, fallback = "glass.tintSource")
    option("film", R.string.glass_film, "colour", R.string.glass_group_colour)
    option("indicator", R.string.glass_indicator, "colour", R.string.glass_group_colour)
    option("dialogDimming", R.string.glass_dialog_dimming, "readability", R.string.glass_group_readability)
    option("menuDimming", R.string.glass_menu_dimming, "readability", R.string.glass_group_readability)
    option("rim", R.string.glass_rim, "edges", R.string.glass_group_edges)
    option("refractionDepth", R.string.glass_refraction_depth, "edges", R.string.glass_group_edges, fallback = "glass.quality")
    option("refractionAmount", R.string.glass_refraction_amount, "edges", R.string.glass_group_edges, fallback = "glass.quality")
    option("dispersion", R.string.glass_dispersion, "edges", R.string.glass_group_edges, fallback = "glass.quality")
    option("depth", R.string.glass_depth, "edges", R.string.glass_group_edges, fallback = "glass.quality")
    GlassDestinationGroup.entries.forEach { group ->
        glassDestinationRows(group).forEach { row ->
            add(GlassSearchOption(SettingsEntryTarget(row.searchId, 1, R.string.liquid_glass,
                row.title, listOf(R.string.liquid_glass, group.title, group.summary, row.description,
                    R.string.glass_mode_disabled, R.string.glass_mode_contour_only) +
                    if (row.area == GlassArea.Images) emptyList() else
                        listOf(R.string.glass_mode_glass_only, R.string.glass_mode_glass_and_contour)),
                "destination.${group.name}", group.title))
        }
    }
}

internal fun glassSearchOption(id: String): GlassSearchOption? = GlassSearchOptions.firstOrNull { it.target.id == id }
