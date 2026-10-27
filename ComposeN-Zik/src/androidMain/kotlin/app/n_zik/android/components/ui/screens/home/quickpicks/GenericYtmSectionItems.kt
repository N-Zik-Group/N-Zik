package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import it.fast4x.innertube.requests.HomePage

/** Gives every generic carousel its own lazy slot so offscreen rows stay uncomposed. */
internal fun LazyListScope.genericYtmSectionItems(
    homePage: HomePage,
    content: @Composable (HomePage) -> Unit
) {
    itemsIndexed(
        items = homePage.sections,
        key = { index, section -> "generic_ytm_section_${section.title}_$index" },
        contentType = { _, _ -> "generic_ytm_section" }
    ) { _, section ->
        content(homePage.copy(sections = listOf(section)))
    }
}
