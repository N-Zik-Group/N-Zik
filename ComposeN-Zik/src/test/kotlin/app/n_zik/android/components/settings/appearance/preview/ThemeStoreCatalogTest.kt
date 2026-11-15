package app.n_zik.android.components.settings.appearance.preview

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStoreCatalogTest {
    private val blur = theme("Blurred Cover", "N-Zik-Group", "artwork")
    private val waves = theme("Audio Waves", "N-Zik-Group", "dark")
    private val night = theme("Night Art", "Indigo", "dark")
    private val catalog = listOf(blur, waves, night)

    @Test
    fun metadataLimitsCountUnicodeCodePointsLikeTheCatalogSchema() {
        val symbol = "\uD83C\uDFB5"
        val published = blur.copy(
            title = symbol.repeat(100),
            description = symbol.repeat(1000),
            tags = listOf(symbol.repeat(30)),
            previews = listOf(StorePreview("previews/player-normal.webp", "player", "a".repeat(64), symbol.repeat(160))),
        )
        assertEquals(published, parseStoreTheme(Json.encodeToString(published)))
        val invalid = listOf(
            published.copy(title = published.title + symbol),
            published.copy(description = published.description + symbol),
            published.copy(tags = listOf(published.tags.single() + symbol)),
            published.copy(previews = listOf(published.previews.single().copy(caption = symbol.repeat(161)))),
        )
        invalid.forEach { theme ->
            assertThrows(IllegalArgumentException::class.java) { parseStoreTheme(Json.encodeToString(theme)) }
        }
        val local = published.copy(author = StoreAuthor(0, "local"), localCreatorName = symbol.repeat(100))
        assertEquals(local, parseStoreTheme(Json.encodeToString(local), allowLocalAuthor = true))
        assertEquals(symbol.repeat(100), (symbol.repeat(101) + "name").takeThemeText(100))
    }

    @Test
    fun reviewLimitAcceptsSupplementaryCharactersWithoutDoublingTheirLength() {
        val symbol = "\uD83C\uDFB5"
        fun document(comment: String) = Json.encodeToString(StoreReviewDocument(1, listOf(
            StoreReview(StoreAuthor(1, "Reviewer"), "community", 5, comment),
        )))
        val comment = symbol.repeat(2000)
        assertEquals(comment, parseStoreReviews(document(comment)).comments.single().comment)
        assertThrows(IllegalArgumentException::class.java) { parseStoreReviews(document(comment + symbol)) }
    }

    @Test
    fun exploreOrdersLatestRevisionsFirstAndUsesIndexOrderWithinADay() {
        val first = blur.copy(publishedOn = "2026-11-13")
        val updated = night.copy(revision = 2, publishedOn = "2026-11-15")
        val new = waves.copy(publishedOn = "2026-11-15")
        val unpublished = blur.copy(title = "Undated")

        assertEquals(listOf(new, updated, first, unpublished),
            listOf(first, updated, new, unpublished).mostRecentlyPublishedFirst())
    }

    @Test
    fun partialTitleOrAuthorAndTagMustBothMatch() {
        assertEquals(listOf(waves), catalog.filterStoreThemes(" zik ", "dark"))
        assertEquals(listOf(blur), catalog.filterStoreThemes("COVER", "artwork"))
        assertEquals(emptyList<StoreTheme>(), catalog.filterStoreThemes("cover", "dark"))
        assertEquals(listOf(waves, night), catalog.filterStoreThemes("", "dark"))
        assertEquals(listOf(night), catalog.filterStoreThemes("digo", null))
    }

    @Test
    fun defaultAppearanceExportsAsPortableDocument() {
        val appearance = parseThemeV1(encodeThemeV1(emptyMap<String, String>(), emptyMap<String, String>()))
        assertEquals(ThemeCategory.entries.toSet(), appearance.keys)
        assertTrue(appearance.values.all { it.plain.isNotEmpty() })
    }

    @Test
    fun submissionOnlyExportsSelectedCategories() {
        val source = encodeThemeV1(emptyMap<String, String>(), emptyMap<String, String>())
        val selected = setOf(ThemeCategory.COLORS, ThemeCategory.NAVIGATION)
        assertEquals(parseThemeV1(source).filterKeys { it in selected },
            parseThemeV1(selectThemeV1Categories(source, selected)))
    }

    @Test
    fun galleryKeepsPublishedOrderAndSharesUntaggedScreenshots() {
        val normal = StorePreview("previews/player-normal.webp", "player", "a".repeat(64))
        val glass = normal.copy(path = "previews/player-glass.webp")
        val shared = normal.copy(path = "previews/queue.webp", role = "queue")
        val manifest = blur.copy(previews = listOf(normal, glass, shared))
        assertEquals(listOf(normal, shared), manifest.galleryPreviews(false))
        assertEquals(listOf(glass, shared), manifest.galleryPreviews(true))
    }

    @Test
    fun reviewSummaryRetainsThePublishedCommentsAndAuthors() {
        val reviews = parseStoreReviews(
            """{"schemaVersion":1,"reviews":[
                {"author":{"githubId":1,"login":"Reviewer"},"kind":"maintainer","rating":5,"comment":"Detailed feedback."},
                {"author":{"githubId":2,"login":"Another"},"kind":"community","rating":4,"comment":"A second opinion."}
            ]}""",
        )
        assertEquals(4.5, reviews.average, 0.0)
        assertEquals(2, reviews.count)
        assertEquals("Detailed feedback.", reviews.comments.first().comment)
        assertEquals("Reviewer", reviews.comments.first().author.login)
        assertEquals("community", reviews.comments.last().kind)
    }

    @Test
    fun tagVariantsShareOneFilterAndReservedIdentity() {
        val first = theme("First", "Creator", "B\u00fbilt in")
        val second = theme("Second", "Creator", "built_in")
        val catalog = listOf(first, second)

        assertEquals(catalog, catalog.filterStoreThemes("", "BUILT-IN"))
        assertEquals(listOf("built-in", "stock", "dark"), listOf(
            "B\u00fbilt in", "built_in", "S.t.o.c.k", "DARK MODE", "dark",
        ).canonicalStoreTags())
        assertEquals(setOf("stock", "built-in"), reservedStoreTags)
    }

    private fun theme(title: String, author: String, tag: String) = StoreTheme(
        schemaVersion = 1,
        themeId = "99edc843-6a9b-5753-8e76-cc11fe9b3295",
        revision = 1,
        title = title,
        description = title,
        author = StoreAuthor(1, author),
        tags = listOf(tag),
        compatibility = StoreCompatibility(null),
        appearance = StoreFile("appearance.json", "a".repeat(64)),
        previews = emptyList(),
    )
}
