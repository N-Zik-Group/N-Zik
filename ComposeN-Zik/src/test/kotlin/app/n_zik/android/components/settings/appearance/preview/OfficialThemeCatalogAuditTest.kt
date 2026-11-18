package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.utils.colorPaletteModeKey
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OfficialThemeCatalogAuditTest {
    @Test fun `official v2 themes apply and undo over every palette mode and Glass toggle`() {
        val catalogPath = System.getenv("NZIK_OFFICIAL_THEME_CATALOG")
        assumeTrue("Set NZIK_OFFICIAL_THEME_CATALOG to the Theme Store checkout's catalog directory", catalogPath != null)
        val catalog = File(requireNotNull(catalogPath))
        val officialIds = Json.parseToJsonElement(catalog.resolve("creators/verified.json").readText())
            .jsonObject.getValue("githubIds").jsonArray.map { it.jsonPrimitive.int }.toSet()
        val entries = Json.parseToJsonElement(catalog.resolve("themes/index-v2.json").readText())
            .jsonObject.getValue("themes").jsonArray
        val documents = entries.mapNotNull { entry ->
            val fields = entry.jsonObject
            val id = fields.getValue("themeId").jsonPrimitive.content
            val revision = fields.getValue("revision").jsonPrimitive.int
            val directory = catalog.resolve("themes/$id/$revision")
            val manifest = Json.parseToJsonElement(directory.resolve("manifest.json").readText()).jsonObject
            val author = manifest.getValue("author").jsonObject.getValue("githubId").jsonPrimitive.int
            if (author !in officialIds) return@mapNotNull null
            val minimum = manifest.getValue("compatibility").jsonObject.getValue("minVersionCode").jsonPrimitive.int
            val title = manifest.getValue("title").jsonPrimitive.content
            Triple(title, minimum, directory.resolve("appearance.json").readText())
        } + Triple("N-Zik stock", ThemeAppearanceV2MinimumVersionCode, defaultThemeAppearanceDocument())
        assertTrue("Official themes must exist in the local catalog", documents.size > 1)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = context.getSharedPreferences("official-audit-profile", Context.MODE_PRIVATE)
        val glass = context.getSharedPreferences("official-audit-glass", Context.MODE_PRIVATE)
        val app = context.getSharedPreferences("official-audit-app", Context.MODE_PRIVATE)
        for ((title, minimum, source) in documents) {
            val groups = parseThemeAppearance(source, allowEmptyCategories = false, advertisedMinVersionCode = minimum)
                .toStoreGroups()
            val appearance = StoreAppearance(source, source.length, groups)
            val selected = requireNotNull(appearance.selectedSettings(StoreCompatibility(minimum), 100, groups.keys))
            for (mode in ColorPaletteMode.entries) for (enabled in listOf(false, true)) {
                profile.edit().clear().putString(colorPaletteModeKey, mode.name).commit()
                glass.edit().clear().putBoolean(glassEnabledKey, enabled).commit()
                app.edit().clear().putString("outside_theme", "preserved").commit()
                val before = listOf(profile, glass, app).map { it.all }
                val undo = selected.applyTo(profile, glass, app)
                assertEquals(title, "preserved", app.getString("outside_theme", null))
                assertTrue("$title must apply at least one published preference", before != listOf(profile, glass, app).map { it.all })
                undo()
                assertEquals("$title/$mode/Glass=$enabled", before, listOf(profile, glass, app).map { it.all })
            }
        }
        println("Audited ${documents.size} official theme documents across ${ColorPaletteMode.entries.size * 2} starting palettes and Glass variants each.")
    }
}
