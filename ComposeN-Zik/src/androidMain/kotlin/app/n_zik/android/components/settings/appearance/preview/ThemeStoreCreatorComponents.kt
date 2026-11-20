package app.n_zik.android.components.settings.appearance.preview

import java.io.File

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import timber.log.Timber

import app.n_zik.android.R
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.artist.ArtistFollowButton
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.ui.screens.profiles.ProfileFaceAvatar
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.typography
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.shimmerEffect

internal data class CreatorImageResolution(val model: Any? = null, val isResolving: Boolean = true)

@Composable
private fun CreatorImage(catalog: ThemeStoreCatalog, profile: CreatorProfile?, media: CreatorMedia?, authorId: Long, modifier: Modifier, contentScale: ContentScale = ContentScale.FillBounds, fallback: @Composable () -> Unit) {
    val image by key(catalog, profile, media) {
        produceState(CreatorImageResolution()) {
            val model = if (profile != null && media != null) {
                try {
                    catalog.creatorImage(profile, media)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("ThemeStore").w(error, "Could not load creator image")
                    null
                }
            } else null
            value = CreatorImageResolution(model, false)
        }
    }
    var loaded by remember(image.model) { mutableStateOf(false) }
    if (!image.isResolving && !loaded) fallback()
    // Older CDN responses can still reference the original banner with transparent margins.
    val imageModifier = if (contentScale == ContentScale.FillBounds && media?.asset?.sha256 == "21fc3f12b97e7c9b2a16aa87fac5becbe313542db5bcddfb0d887f8470970c84") {
        modifier.graphicsLayer {
            scaleX = 1200f / 1100f
            scaleY = 600f / 500f
        }
    } else if (media?.asset?.sha256 == "faff0a7d85ebff353f68b2a5dd139477522d6d338eed8c6a5fd66dabd2590412") {
        // The published artwork includes transparent rounded corners. Crop them before applying the UI shape.
        modifier.graphicsLayer {
            scaleX = 1.1f
            scaleY = 1.1f
        }
    } else modifier
    if (image.isResolving || image.model != null) {
        ImageCacheFactory.LocalAsyncImage(
            model = image.model,
            contentDescription = null,
            modifier = imageModifier,
            contentScale = contentScale,
            fallback = null,
            shape = null,
            isResolvingModel = image.isResolving,
            loadIdentity = listOf("creator-banner", authorId),
            onSuccess = { loaded = true },
        )
    }
}

@Composable
internal fun CreatorCard(
    profile: CreatorProfile?,
    themes: List<StoreTheme>,
    catalog: ThemeStoreCatalog,
    followed: Boolean,
    official: Boolean,
    onFollow: (StoreAuthor) -> Unit,
    publicationAlerts: Boolean,
    canSetPublicationAlerts: Boolean,
    onTogglePublicationAlerts: () -> Unit,
    fallbackAuthor: StoreAuthor? = null,
    onEditProfile: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    val author = profile?.let { StoreAuthor(it.githubId, it.login) } ?: themes.firstOrNull()?.author ?: fallbackAuthor ?: return
    val name = profile?.displayName ?: author.login
    val ownProfile = signedInCreatorId() == author.githubId
    val shimmer = forceLoadingShimmerPreview()
    ThemeCardSurface {
        Box {
            Box(Modifier.matchParentSize().clip(uiRoundnessShape())) {
                if (shimmer) Spacer(Modifier.matchParentSize().shimmerEffect())
                else {
                    CreatorImage(catalog, profile, profile?.banner, author.githubId, Modifier.matchParentSize(), ContentScale.Crop) {
                        CreatorDemoBanner(author.githubId, Modifier.matchParentSize())
                    }
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.70f)))
                }
            }
            Box(Modifier.matchParentSize().clip(uiRoundnessShape()).clickable(onClick = onOpen))
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (shimmer) Spacer(Modifier.size(36.dp).clip(artistThumbnailShape()).shimmerEffect())
                else CreatorAvatar(profile, author, catalog, 36.dp)
                Column(Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f, fill = false)) {
                            val style = typography().s.copy(color = Color.White)
                            if (shimmer) StoreLoadingLine(name, style)
                            else BasicText(name, Modifier.basicMarquee(iterations = Int.MAX_VALUE), style = style, maxLines = 1)
                        }
                        OfficialCreatorBadge(official)
                    }
                    val count = stringResource(R.string.theme_store_author_theme_count, themes.size)
                    val countStyle = typography().xxs.copy(color = Color.White.copy(alpha = 0.7f))
                    if (shimmer) StoreLoadingLine(count, countStyle)
                    else BasicText(count, style = countStyle)
                }
                if (onEditProfile != null) {
                    app.n_zik.android.components.glass.PageButton(onClick = onEditProfile) {
                        StoreText(stringResource(R.string.theme_store_edit_creator))
                    }
                } else if (!ownProfile) {
                    ArtistFollowButton(likeState = true.takeIf { followed }, onClick = { onFollow(author) })
                    CreatorPublicationBell(publicationAlerts, canSetPublicationAlerts, onTogglePublicationAlerts)
                }
                Icon(painterResource(R.drawable.chevron_forward), null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
internal fun localThemeCreatorName(theme: StoreTheme): String = theme.localCreatorName
    ?: if (theme.author.githubId > 0) theme.author.login
    else app.n_zik.android.components.navigation.header.rememberHeaderProfileFace().name

@Composable
internal fun CreatorAvatar(
    profile: CreatorProfile?,
    author: StoreAuthor,
    catalog: ThemeStoreCatalog,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val name = profile?.displayName ?: author.login
    val image by key(catalog, profile) {
        produceState(CreatorImageResolution()) {
            val model = if (profile != null) {
                try {
                    catalog.creatorImage(profile, profile.avatar)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("ThemeStore").w(error, "Could not load creator avatar")
                    null
                }
            } else null
            value = CreatorImageResolution(model, false)
        }
    }
    val offline by OfflineMode.rememberEnabled()
    val source = when (val model = image.model) {
        is File -> model.path
        is String -> model.takeUnless { offline }
        else -> if (profile == null && !offline) "https://avatars.githubusercontent.com/u/${author.githubId}?s=256" else null
    }
    ProfileFaceAvatar(
        avatar = source?.let { FaceAvatar.Photo(it) } ?: FaceAvatar.Initials(name),
        faceName = name,
        size = size,
        modifier = modifier.glassImageRim(artistThumbnailShape()).clip(artistThumbnailShape()),
        isLoadingPhoto = image.isResolving,
        photoLoadIdentity = listOf("creator-avatar", author.githubId),
    )
}

@Composable
internal fun CreatorDemoBanner(githubId: Long, modifier: Modifier = Modifier.fillMaxSize()) {
    val context = LocalContext.current.applicationContext
    val offline by OfflineMode.rememberEnabled()
    val banner by key(context, githubId, offline) {
        produceState(CreatorImageResolution()) {
            val model = try {
                ThemePreviewAssets.banner(context, githubId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemeStore").d(error, "Demo creator banner unavailable")
                null
            }
            value = CreatorImageResolution(model, false)
        }
    }
    Box(modifier.background(colorPalette().background1)) {
        if (banner.isResolving || banner.model != null) {
            ImageCacheFactory.LocalAsyncImage(
                model = banner.model, contentDescription = null, modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, shape = null,
                isResolvingModel = banner.isResolving,
                loadIdentity = listOf("creator-demo-banner", githubId),
            )
        }
    }
}

@Composable
internal fun CreatorProfileHeader(
    onEditProfile: (() -> Unit)?,
    profile: CreatorProfile?,
    author: StoreAuthor,
    themeCount: Int?,
    catalog: ThemeStoreCatalog,
    themeCountPending: Boolean,
    followed: Boolean,
    query: TextFieldValue,
    onQueryChanged: (TextFieldValue) -> Unit,
    official: Boolean,
    publicationAlerts: Boolean,
    canSetPublicationAlerts: Boolean,
    onTogglePublicationAlerts: () -> Unit,
    onFollow: () -> Unit,
) {
    val shimmer = forceLoadingShimmerPreview()
    val name = profile?.displayName ?: author.login
    val handle = "@${author.login}"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bannerHeight = maxWidth / 2.2f
            val bannerShape = uiRoundnessShape()
            Box(Modifier.fillMaxWidth().height(bannerHeight).glassImageRim(bannerShape).clip(bannerShape)) {
                if (shimmer) Spacer(Modifier.matchParentSize().shimmerEffect())
                else CreatorImage(catalog, profile, profile?.banner, author.githubId, Modifier.fillMaxSize()) {
                    CreatorDemoBanner(author.githubId)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = bannerHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Box(Modifier.size(70.dp).offset(y = (-28).dp).background(colorPalette().background0, artistThumbnailShape()),
                contentAlignment = Alignment.Center) {
                if (shimmer) Spacer(Modifier.size(66.dp).clip(artistThumbnailShape()).shimmerEffect())
                else CreatorAvatar(profile, author, catalog, 66.dp)
            }
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f, fill = false)) {
                        if (shimmer) StoreLoadingLine(name, typography().m)
                        else StoreText(name, style = typography().m, marquee = true)
                    }
                    OfficialCreatorBadge(official)
                }
                if (shimmer) StoreLoadingLine(handle, typography().xs)
                else StoreText(handle, secondary = true, style = typography().xs)
            }
            if (onEditProfile != null) {
                app.n_zik.android.components.glass.PageButton(onClick = onEditProfile) {
                    StoreText(stringResource(R.string.theme_store_edit_creator))
                }
            } else {
                ArtistFollowButton(likeState = true.takeIf { followed }, onClick = onFollow)
                CreatorPublicationBell(publicationAlerts, canSetPublicationAlerts, onTogglePublicationAlerts)
            }
            }
        }
        profile?.bio?.takeIf { it.isNotBlank() }?.let {
            if (shimmer) MeasuredMetadataPlaceholder(it, typography().xxs.secondary, Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
            else SettingsDescription(it)
        }
        UnifiedSearchBar(
            input = query,
            onInputChanged = onQueryChanged,
            placeholderRes = R.string.theme_store_search_creator_themes,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            onClear = { onQueryChanged(TextFieldValue()) },
        )
        Box(Modifier.padding(top = 8.dp)) {
            if (themeCountPending) StoreLoadingLine(null, typography().xs.copy(fontWeight = FontWeight.SemiBold))
            else themeCount?.let { StoreSectionHeader(R.string.theme_store_published_theme_count, themeCount = it) }
        }
    }
}


@Composable
internal fun CreatorPublicationBell(enabled: Boolean, available: Boolean, onToggle: () -> Unit) {
    StoreIconAction(available, onToggle) {
        Icon(
            painterResource(if (enabled) R.drawable.notification1 else R.drawable.notification2),
            stringResource(if (enabled) R.string.theme_store_disable_publication_alerts else R.string.theme_store_enable_publication_alerts),
            Modifier.size(20.dp),
            tint = colorPalette().text,
        )
    }
}
