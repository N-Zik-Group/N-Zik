package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.utils.colorPaletteModeKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ThemeStoreLocalEditTest {
    @Test
    fun editSurvivesReconciliationWithoutChangingThePublishedRevision() {
        val (editor, worker) = libraries("migrated-local-edit")
        val published = publishedTheme()
        val original = restoredTheme(published)
        editor.restoreImported(listOf(original))
        assertEquals(1, worker.bindTrustedStoreOrigins(listOf(checkNotNull(published.theme))))

        val edited = editedTheme(original)
        val saved = editor.saveLocalEdit(original, edited)

        assertEquals(edited.copy(storeOrigin = null), saved)
        assertEquals(published, editor.download(published.id))
        assertEquals(saved, editor.download(original.id))
        assertEquals(2, editor.downloads().size)
        assertEquals(emptyList<ThemeStoreOrigin>(), editor.restoredStoreOrigins())
        assertEquals(saved, editor.saveLocalEdit(original, edited))
        assertEquals(2, editor.downloads().size)
    }

    @Test
    fun unchangedSaveAfterReconciliationDoesNotRecreateALocalDuplicate() {
        val (editor, worker) = libraries("unchanged-migrated-local-edit")
        val published = publishedTheme()
        val original = restoredTheme(published)
        editor.restoreImported(listOf(original))
        worker.bindTrustedStoreOrigins(listOf(checkNotNull(published.theme)))
        val originalTheme = checkNotNull(original.theme)
        // Packaging normalizes JSON and supplies the current local author even without an edit.
        val document = Json { prettyPrint = true }.encodeToString(
            JsonElement.serializer(), Json.parseToJsonElement(original.document),
        )
        val packaged = original.copy(document = document, theme = originalTheme.copy(
            author = StoreAuthor(0, "local"), localCreatorName = "My profile",
            appearance = originalTheme.appearance.copy(sha256 = document.encodeToByteArray().sha256()),
        ))

        assertEquals(published, editor.saveLocalEdit(original, packaged))
        assertEquals(listOf(published), editor.downloads())
        assertNull(editor.download(original.id))
    }

    @Test
    fun editingBeforeReconciliationDetachesTheChangedLocalCopy() {
        val (editor, worker) = libraries("edited-before-reconciliation")
        val published = publishedTheme()
        val original = restoredTheme(published)
        editor.restoreImported(listOf(original))

        val saved = editor.saveLocalEdit(original, editedTheme(original))

        assertEquals(0, worker.bindTrustedStoreOrigins(listOf(checkNotNull(published.theme))))
        assertEquals(listOf(saved), editor.downloads())
        assertNull(saved.storeOrigin)
    }

    @Test
    fun unchangedLocalSavePreservesItsEligibilityForReconciliation() {
        val (editor, worker) = libraries("unchanged-local-edit")
        val published = publishedTheme()
        val original = restoredTheme(published)
        editor.restoreImported(listOf(original))

        assertEquals(original, editor.saveLocalEdit(original, original))
        assertEquals(1, worker.bindTrustedStoreOrigins(listOf(checkNotNull(published.theme))))
        assertEquals(listOf(published), editor.downloads())
    }

    @Test
    fun savingDoesNotResurrectADeletedThemeOrOverwriteAnotherLocalEdit() {
        val (editor, worker) = libraries("removed-local-edit")
        val original = restoredTheme(publishedTheme())
        editor.restoreImported(listOf(original))
        worker.removeDownload(original.id)

        assertThrows(IllegalStateException::class.java) { editor.saveLocalEdit(original, editedTheme(original)) }
        assertEquals(emptyList<SavedTheme>(), editor.downloads())

        val edited = editedTheme(original)
        val otherEdit = edited.copy(name = "Another edit", theme = checkNotNull(edited.theme).copy(title = "Another edit"))
        worker.save(otherEdit)
        assertThrows(IllegalStateException::class.java) { editor.saveLocalEdit(original, editedTheme(original)) }
        assertEquals(listOf(otherEdit), editor.downloads())
    }

    private fun libraries(name: String): Pair<ThemeStoreLibrary, ThemeStoreLibrary> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return ThemeStoreLibrary(preferences) to ThemeStoreLibrary(preferences)
    }

    private fun publishedTheme(): SavedTheme {
        val document = defaultThemeAppearanceDocument()
        val theme = StoreTheme(
            1, "12345678-1111-2222-3333-123456789014", 1, "Restored theme", "A portable theme",
            StoreAuthor(12, "alice"), emptyList(), StoreCompatibility(1),
            StoreFile("appearance.json", document.encodeToByteArray().sha256()), emptyList(),
            publishedOn = "2026-11-15",
        )
        return SavedTheme(theme.title, document, theme)
    }

    private fun restoredTheme(published: SavedTheme): SavedTheme {
        val theme = checkNotNull(published.theme)
        return published.copy(
            theme = theme.copy(themeId = "12345678-1111-2222-3333-123456789015", publishedOn = null),
            imported = true,
            storeOrigin = ThemeStoreOrigin(theme.themeId, theme.revision),
        )
    }

    private fun editedTheme(original: SavedTheme): SavedTheme {
        val theme = checkNotNull(original.theme)
        val document = encodeThemeV1(mapOf(colorPaletteModeKey to ColorPaletteMode.Light.name), emptyMap<String, Any>())
        return original.copy(name = "My edited theme", document = document, theme = theme.copy(
            title = "My edited theme", appearance = theme.appearance.copy(sha256 = document.encodeToByteArray().sha256()),
        ))
    }
}
