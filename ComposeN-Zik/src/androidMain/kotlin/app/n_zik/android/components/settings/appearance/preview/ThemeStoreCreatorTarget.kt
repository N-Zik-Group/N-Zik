package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import app.n_zik.android.R
import app.n_zik.android.enums.NavRoutes

internal enum class ThemeStoreCreatorTarget(val routeKey: String, vararg val labels: Int) {
    PublishingAccess("access", R.string.theme_store_group_publishing_access),
    GitHubInstallation("github-installation", R.string.github_app_installation),
    Profile("profile", R.string.theme_store_edit_creator, R.string.theme_store_creator_profile),
    PublicInformation("public-information", R.string.theme_store_creator_identity),
    PublishProfile("publish-profile", R.string.theme_store_creator_publish),
    Publish("publish", R.string.theme_store_creator_publish_theme, R.string.theme_store_group_theme_publication),
    EditPublished("edit-published", R.string.theme_store_creator_edit_theme),
    MyThemes("my-themes", R.string.theme_store_creator_my_themes),
    Submissions("submissions", R.string.theme_store_creator_submissions, R.string.theme_store_group_review_status),
    CreateLocal("create-local", R.string.theme_store_create_local, R.string.theme_store_local_themes, R.string.theme_store_group_local_editing),
    EditLocal("edit-local", R.string.theme_store_edit_local);

    val opensProfileEditor: Boolean
        get() = this == PublicInformation || this == PublishProfile
}

internal const val CREATOR_PROFILE_TARGET_ARGUMENT = "target"

internal fun creatorProfileSettingsRoute(target: ThemeStoreCreatorTarget): String =
    "${NavRoutes.themeStoreCreatorSettings.name}?$CREATOR_PROFILE_TARGET_ARGUMENT=${target.routeKey}"

internal fun ThemeStoreCreatorTarget?.visibleTarget(hasCreatorAccount: Boolean): ThemeStoreCreatorTarget? = when (this) {
    ThemeStoreCreatorTarget.GitHubInstallation,
    ThemeStoreCreatorTarget.Profile,
    ThemeStoreCreatorTarget.PublicInformation,
    ThemeStoreCreatorTarget.PublishProfile,
    ThemeStoreCreatorTarget.Publish,
    ThemeStoreCreatorTarget.EditPublished,
    ThemeStoreCreatorTarget.MyThemes,
    ThemeStoreCreatorTarget.Submissions -> if (hasCreatorAccount) this else ThemeStoreCreatorTarget.PublishingAccess

    null,
    ThemeStoreCreatorTarget.PublishingAccess,
    ThemeStoreCreatorTarget.CreateLocal,
    ThemeStoreCreatorTarget.EditLocal -> this
}

@Composable
internal fun creatorTargetModifier(target: ThemeStoreCreatorTarget?, item: ThemeStoreCreatorTarget): Modifier {
    val modifier = Modifier.testTag("themeStore.creator.${item.routeKey}")
    if (target != item) return modifier
    val requester = remember { BringIntoViewRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(placed) {
        if (placed) requester.bringIntoView()
    }
    return modifier.bringIntoViewRequester(requester).onGloballyPositioned { placed = true }
}
