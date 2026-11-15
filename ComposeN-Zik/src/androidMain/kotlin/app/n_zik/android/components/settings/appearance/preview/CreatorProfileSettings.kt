package app.n_zik.android.components.settings.appearance.preview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.AtomicFile
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.n_zik.android.components.glass.GlassTextField as TextField
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.dialog.common.DialogActionList
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.settings.accounts.GitHubAccount
import app.n_zik.android.components.settings.accounts.rememberGitHubPublicationAuthorization
import app.n_zik.android.components.settings.accounts.githubContributionBlock
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.navigation.currentPageNavigationEntry
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import org.json.JSONObject

@Composable
internal fun signedInCreatorId(): Long? {
    val context = LocalContext.current
    val login by GitHubAccount.login.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(context) { GitHubAccount.read(context) }
    return remember(context, login) { GitHubAccount.userId(context) }
}

internal fun hasCreatorPublicationChanges(
    published: CreatorProfile?,
    login: String,
    displayName: String,
    description: String,
    avatar: String?,
    banner: String?,
): Boolean = published == null || published.login != login ||
    displayName.trim() != published.displayName || description != published.bio ||
    (avatar != null && (avatar.isNotEmpty() || published.avatar.source != "github")) ||
    (banner != null && (banner.isNotEmpty() || published.banner.source != "generated"))

@Composable
internal fun CreatorProfileSettingsEntry(
    onClick: () -> Unit,
    themeEntries: @Composable () -> Unit,
) {
    val login by GitHubAccount.login.collectAsStateWithLifecycle()
    SettingsSectionCard(stringResource(R.string.theme_store_creator_profile), R.drawable.person, horizontalPadding = 0.dp, content = {
        OtherSettingsEntry(
            title = stringResource(R.string.theme_store_edit_creator),
            text = stringResource(if (login == null) R.string.theme_store_creator_sign_in else R.string.theme_store_creator_draft),
            icon = R.drawable.person,
            isEnabled = login != null,
            onClick = onClick,
        )
        if (login != null) {
            app.n_zik.android.components.settings.common.SettingsCardSectionDivider()
            themeEntries()
        }
    })
}

@Composable
internal fun CreatorProfileSettings(navController: NavController, miniPlayer: @Composable () -> Unit) {
    val context = LocalContext.current
    val login by GitHubAccount.login.collectAsStateWithLifecycle()
    val id = remember(login) { GitHubAccount.userId(context) }
    val title = stringResource(R.string.theme_store_edit_creator)
    val offline by OfflineMode.rememberEnabled()
    val catalog = remember(context, offline) { themeStoreCatalog(context, offline) }
    val lastSync by produceState<Long?>(null, catalog) { value = catalog.cachedState()?.syncedAt }
    Skeleton(
        navController = navController,
        topInsetHandledByContent = true,
        miniPlayer = miniPlayer,
        navBarContent = { item -> item(0, title, R.drawable.person) },
    ) {
        val padding = rememberPageChromeContentPadding(PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                HeaderWithIcon(title = title, iconId = R.drawable.color_palette, enabled = false, modifier = Modifier, onClick = {})
                lastSync?.let { StoreLastSync(it) }
            }
            val handle = login
            if (id == null || handle == null) {
                SettingsDescription(stringResource(R.string.theme_store_creator_sign_in))
            } else {
                key(id) { CreatorProfileEditor(id, handle) { navController.popBackStack() } }
            }
        }
    }
}

@Composable
private fun CreatorProfileEditor(id: Long, login: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    val offline by OfflineMode.rememberEnabled()
    val catalog = remember(context, offline) { themeStoreCatalog(context, offline) }
    val prefs = remember(context) { context.getSharedPreferences("theme_creator_drafts", android.content.Context.MODE_PRIVATE) }
    val storedDraft = remember(id) { prefs.getString(id.toString(), null)?.let { runCatching { JSONObject(it) }.getOrNull() } }
    val navigationEntry = currentPageNavigationEntry()
    val imageCleanup = if (navigationEntry != null) {
        viewModel<CreatorDraftImageCleanup>(viewModelStoreOwner = navigationEntry, key = "creator-images-$id")
    } else null
    SideEffect {
        val app = context.applicationContext
        imageCleanup?.onExit = { candidates ->
            val retained = creatorDraftImages(prefs.getString(id.toString(), null))
            pruneCreatorDraftImages(app.filesDir, id, candidates - retained)
        }
        imageCleanup?.candidates?.addAll(creatorDraftImages(storedDraft?.toString()))
    }
    var useDraft by rememberSaveable(id) { mutableStateOf<Boolean?>(null) }
    if (storedDraft != null && useDraft == null) {
        ThemeStoreDraftDialog(onSaved, onDiscard = {
            prefs.edit().remove(id.toString()).apply()
            useDraft = false
        }, onContinue = { useDraft = true })
        return
    }
    val saved = storedDraft.takeIf { useDraft == true }
    var catalogLoaded by remember(catalog, id) { mutableStateOf(false) }
    var loading by remember(catalog, id) { mutableStateOf(!offline) }
    val publicProfile by produceState<CreatorProfile?>(null, catalog, id) {
        try {
            value = catalog.creators().firstOrNull { it.githubId == id }
            catalogLoaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The editor remains available with local defaults.
        } finally {
            loading = false
        }
    }
    if (loading) {
        app.n_zik.android.components.ui.FullPageLoader()
        return
    }
    var name by rememberSaveable { mutableStateOf(saved?.optString("name")) }
    var bio by rememberSaveable { mutableStateOf(saved?.optString("bio")) }
    var avatar by rememberSaveable { mutableStateOf(saved?.optString("avatar")) }
    var banner by rememberSaveable { mutableStateOf(saved?.optString("banner")) }
    SideEffect { imageCleanup?.candidates?.addAll(listOfNotNull(avatar, banner)) }
    var imageChoice by rememberSaveable { mutableStateOf<CreatorImageChoice?>(null) }
    val displayName = name ?: publicProfile?.displayName ?: login
    val description = bio ?: publicProfile?.bio.orEmpty()
    val published = publicProfile
    val hasPublicationChanges = catalogLoaded && hasCreatorPublicationChanges(
        published, login, displayName, description, avatar, banner,
    )
    val avatarImage by produceState<Any?>(null, catalog, publicProfile, avatar) {
        val profile = publicProfile
        value = if (avatar != null) avatar?.takeIf(String::isNotEmpty)?.let(Uri::parse)
        else profile?.let { creatorEditorImage(catalog, it, it.avatar) } ?: "https://avatars.githubusercontent.com/u/$id?s=256"
    }
    val bannerImage by produceState<Any?>(null, catalog, publicProfile, banner) {
        val profile = publicProfile
        value = if (banner != null) banner?.takeIf(String::isNotEmpty)?.let(Uri::parse)
        else profile?.let { creatorEditorImage(catalog, it, it.banner) }
    }
    val scope = rememberCoroutineScope()
    fun cropImage(uri: Uri?, size: Size, onSelected: (String) -> Unit) {
        if (uri == null) return
        scope.launch {
            var staged: String? = null
            try {
                val edited = PickedImageEditor.edit(context, uri, aspectRatio = size) ?: return@launch
                val selected = edited.use {
                    withContext(NzikDispatchers.DATA) {
                        persistCreatorImage(context, id, it.file, size).also { staged = it }
                    }
                }
                imageCleanup?.candidates?.add(selected)
                onSelected(selected)
                staged = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.log.Timber.tag("CreatorProfile").w(error, "Could not save cropped image")
                Toaster.e(R.string.theme_store_creator_image_error)
            } finally {
                staged?.let { discarded ->
                    withContext(NonCancellable + NzikDispatchers.DATA) {
                        pruneCreatorDraftImages(context.filesDir, id, setOf(discarded))
                    }
                }
            }
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        cropImage(uri, Size(256, 256)) { avatar = it }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        cropImage(uri, Size(1100, 500)) { banner = it }
    }
    var packaging by remember { mutableStateOf(false) }
    var pullUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmPublish by rememberSaveable { mutableStateOf(false) }
    SettingsSectionCard(
        title = stringResource(R.string.theme_store_creator_profile),
        icon = R.drawable.person,
        horizontalPadding = 0.dp,
        bottomSpacing = 0.dp,
        content = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(2.2f).clip(uiRoundnessShape())) {
                    val custom = bannerImage
                    if (custom == null) {
                        CreatorDemoBanner(id)
                    } else {
                        ImageCacheFactory.LocalAsyncImage(custom, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, fallback = null, shape = null)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(56.dp).clip(artistThumbnailShape())) {
                        val image = avatarImage ?: "https://avatars.githubusercontent.com/u/$id?s=256"
                        ImageCacheFactory.LocalAsyncImage(image, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop, shape = null)
                    }
                    Column(Modifier.weight(1f)) {
                        StoreText(displayName, style = typography().m)
                        StoreText("@$login", secondary = true)
                    }
                }
                if (description.isNotBlank()) StoreText(description, secondary = true)
            }
        },
    )
    SettingsSectionCard(
        title = stringResource(R.string.theme_store_creator_identity),
        icon = R.drawable.person,
        horizontalPadding = 0.dp,
        bottomSpacing = 0.dp,
        content = {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    displayName, { if (it.themeTextLength <= 60) name = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.theme_store_creator_display_name)) },
                    singleLine = true, shape = uiRoundnessShape(), colors = creatorFieldColors(),
                )
                TextField(
                    description, { if (it.themeTextLength <= 280) bio = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.theme_store_creator_bio)) },
                    minLines = 2, maxLines = 5, shape = uiRoundnessShape(), colors = creatorFieldColors(),
                )
            }
        },
    )
    SettingsSectionCard(
        title = stringResource(R.string.theme_store_creator_images),
        icon = R.drawable.folder,
        horizontalPadding = 0.dp,
        bottomSpacing = 0.dp,
        content = {
            OtherSettingsEntry(stringResource(R.string.theme_store_creator_pick_avatar), "256 × 256", R.drawable.person,
                onClick = { imageChoice = CreatorImageChoice.Avatar })
            OtherSettingsEntry(stringResource(R.string.theme_store_creator_pick_banner), "1100 × 500", R.drawable.folder,
                onClick = { imageChoice = CreatorImageChoice.Banner })
        },
    )
    SettingsSectionCard(
        title = stringResource(R.string.theme_store_creator_save_draft),
        icon = R.drawable.information,
        horizontalPadding = 0.dp,
        bottomSpacing = 0.dp,
        content = {
            SettingsDescription(stringResource(R.string.theme_store_creator_draft))
            PageButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                enabled = displayName.isNotBlank(),
                onClick = {
                    val draft = JSONObject().put("name", displayName.trim()).put("bio", description)
                    avatar?.let { draft.put("avatar", it) }
                    banner?.let { draft.put("banner", it) }
                    prefs.edit().putString(id.toString(), draft.toString()).apply()
                    Toaster.s(R.string.theme_store_creator_draft_saved)
                    onSaved()
                },
            ) { Text(stringResource(R.string.theme_store_creator_save_draft)) }
        },
    )
    val authPending by GitHubAccount.awaiting.collectAsStateWithLifecycle()
    PageButton(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        enabled = !offline && !packaging && !authPending && displayName.isNotBlank() && hasPublicationChanges,
        onClick = { confirmPublish = true },
    ) { Text(stringResource(R.string.theme_store_creator_publish)) }
    val submit: () -> Unit = {
        packaging = true
        Toaster.i(R.string.theme_store_submission_sending)
        Toaster.i(R.string.theme_store_creator_submitting)
        scope.launch {
            try {
                val message = githubContributionBlock(id)
                if (message == null) {
                    val files = prepareCreatorSubmission(context, catalog, publicProfile, id, login, displayName.trim(), description, avatar, banner)
                    val token = checkNotNull(GitHubAccount.tokenFor(context, id))
                    pullUrl = submitCreatorProfile(token, id, files)
                    val number = pullUrl?.substringAfterLast('/')?.toIntOrNull()
                    if (number != null) {
                        context.getSharedPreferences("theme_creator_submissions", android.content.Context.MODE_PRIVATE)
                            .edit().putString("$id:$number", "open").apply()
                        notifyCreatorSubmissionSent(context, id, number)
                    }
                    Toaster.s(R.string.theme_store_creator_pr_submitted)
                } else {
                    GitHubAccount.disconnect(context)
                    Toaster.e(message, android.widget.Toast.LENGTH_LONG)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (blocked: CreatorPublicationBlocked) {
                GitHubAccount.disconnect(context)
                Toaster.e(blocked.publicMessage, android.widget.Toast.LENGTH_LONG)
            } catch (_: CreatorPublicationAccountMismatch) {
                Toaster.e(R.string.theme_store_creator_publisher_account)
            } catch (_: CreatorPublicationUnchanged) {
                Toaster.i(R.string.theme_store_creator_unchanged)
            } catch (_: CreatorPublicationWorkflowPermission) {
                GitHubAccount.requireWorkflowAuthorization(context)
                Toaster.i(R.string.theme_store_creator_workflow_authorization)
            } catch (_: CreatorPublicationSessionExpired) {
                GitHubAccount.clearPublicationToken(context)
                Toaster.i(R.string.theme_store_creator_session_expired)
            } catch (error: Exception) {
                timber.log.Timber.tag("CreatorProfile").w(error, "Profile submission failed")
                Toaster.e(R.string.theme_store_creator_publish_error)
            } finally {
                packaging = false
            }
        }
    }
    val authorizePublication = rememberGitHubPublicationAuthorization(submit)
    DialogPresence(visible = confirmPublish) {
        DefaultDialog(
            title = stringResource(R.string.theme_store_creator_publish),
            onDismiss = { confirmPublish = false },
            footer = {
                ButtonRow(listOf(
                    DialogButton(stringResource(R.string.common_action_cancel), { confirmPublish = false }, danger = true),
                    DialogButton(stringResource(R.string.theme_store_creator_continue_publish), {
                        confirmPublish = false
                        if (GitHubAccount.tokenFor(context, id) != null) submit() else {
                            Toaster.i(R.string.theme_store_creator_authorizing)
                            authorizePublication(id)
                        }
                    }),
                ))
            },
        ) { StoreText(stringResource(R.string.theme_store_creator_publish_warning)) }
    }
    DialogPresence(visible = pullUrl != null) {
        DialogActionList(
            title = stringResource(R.string.theme_store_creator_pr_submitted),
            onDismiss = { pullUrl = null },
            actions = listOf(DialogButton(stringResource(R.string.theme_store_creator_open_github), onClick = {
                val url = pullUrl
                pullUrl = null
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkNotNull(url))))
                }.onFailure { Toaster.e(R.string.theme_store_creator_publish_error) }
            })),
        )
    }
    imageChoice?.let { choice ->
        val isAvatar = choice == CreatorImageChoice.Avatar
        DialogPresence(visible = true) {
            DialogActionList(
                title = stringResource(if (isAvatar) R.string.theme_store_creator_pick_avatar else R.string.theme_store_creator_pick_banner),
                onDismiss = { imageChoice = null },
                actions = listOf(
                    DialogButton(stringResource(R.string.theme_store_creator_select_image), onClick = {
                        imageChoice = null
                        val picker = if (isAvatar) avatarPicker else bannerPicker
                        picker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                    }),
                    DialogButton(
                        stringResource(if (isAvatar) R.string.theme_store_creator_github_avatar else R.string.theme_store_creator_generated_banner),
                        onClick = {
                            imageChoice = null
                            if (isAvatar) avatar = "" else banner = ""
                        },
                    ),
                ),
            )
        }
    }
}

private enum class CreatorImageChoice { Avatar, Banner }

@Composable
private fun creatorFieldColors() = InputDialog.defaultTextFieldColors().copy(
    unfocusedTextColor = app.n_zik.android.colorPalette().text,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
)

private suspend fun prepareCreatorSubmission(
    context: android.content.Context,
    catalog: ThemeStoreCatalog,
    profile: CreatorProfile?,
    id: Long,
    login: String,
    name: String,
    bio: String,
    avatar: String?,
    banner: String?,
) = withContext(NzikDispatchers.DATA) {
    val files = mutableMapOf<String, ByteArray>()
    suspend fun media(selection: String?, existing: CreatorMedia?, stem: String, fallback: String): CreatorMedia {
        if (selection.isNullOrEmpty() && (selection != null || existing?.source != "custom")) return CreatorMedia(fallback)
        val assetName = "$stem.webp"
        val original: ByteArray
        val limit = 4 * 1024 * 1024
        if (selection == null) {
            val current = requireNotNull(profile)
            val media = requireNotNull(existing)
            val file = catalog.creatorImage(current, media) as? File ?: error("Missing original image")
            original = file.inputStream().use { it.readThemeBytes(limit) }
        } else {
            val source = Uri.parse(selection)
            original = checkNotNull(context.contentResolver.openInputStream(source)).use { it.readThemeBytes(limit) }
        }
        val bytes = if (stem == "profile") creatorWebp(original, 256, 256) else creatorWebp(original, 1100, 500)
        files[assetName] = bytes
        return CreatorMedia("custom", StoreFile(assetName, bytes.sha256()))
    }
    val document = CreatorProfile(1, id, login, name, bio,
        media(avatar, profile?.avatar, "profile", "github"),
        media(banner, profile?.banner, "banner", "generated"),
    )
    val json = Json { prettyPrint = true }
    files["profile.json"] = json.encodeToString(CreatorProfile.serializer(), document).encodeToByteArray()
    files.mapKeys { (name, _) -> "catalog/creators/$id/$name" }
}

@Suppress("DEPRECATION")
private fun creatorWebp(bytes: ByteArray, width: Int, height: Int): ByteArray {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth > 0 && options.outHeight > 0)
    var sample = 1
    while (maxOf(options.outWidth, options.outHeight) / sample > 2200) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    val image = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
    try {
        val cropWidth = minOf(image.width, (image.height.toLong() * width / height).toInt()).coerceAtLeast(1)
        val cropHeight = minOf(image.height, (image.width.toLong() * height / width).toInt()).coerceAtLeast(1)
        val x = (image.width - cropWidth) / 2
        val y = (image.height - cropHeight) / 2
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(output).drawBitmap(image, Rect(x, y, x + cropWidth, y + cropHeight),
                Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            return java.io.ByteArrayOutputStream().use { buffer ->
                check(output.compress(Bitmap.CompressFormat.WEBP, 78, buffer))
                buffer.toByteArray()
            }
        } finally {
            output.recycle()
        }
    } finally {
        image.recycle()
    }
}

private suspend fun creatorEditorImage(catalog: ThemeStoreCatalog, profile: CreatorProfile, media: CreatorMedia): Any? =
    try {
        catalog.creatorImage(profile, media)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

private fun persistCreatorImage(context: android.content.Context, id: Long, image: File, size: Size): String {
    val source = image.inputStream().use { it.readThemeBytes(32 * 1024 * 1024) }
    val bytes = creatorWebp(source, size.width, size.height)
    val folder = context.filesDir.resolve("theme-creator-drafts/$id")
    check(folder.isDirectory || folder.mkdirs() || folder.isDirectory)
    val file = AtomicFile(folder.resolve("${java.util.UUID.randomUUID()}.webp"))
    writeThemeFile(file, bytes)
    return Uri.fromFile(file.baseFile).toString()
}


internal class CreatorDraftImageCleanup : ViewModel() {
    val candidates = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    var onExit: ((Set<String>) -> Unit)? = null

    override fun onCleared() {
        val cleanup = onExit ?: return
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch { cleanup(candidates) }
    }
}

private fun creatorDraftImages(raw: String?): Set<String> {
    val draft = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptySet()
    return listOf("avatar", "banner").map { draft.optString(it) }.filter(String::isNotEmpty).toSet()
}

private fun pruneCreatorDraftImages(filesDir: File, id: Long, candidates: Set<String>) {
    val folder = filesDir.canonicalFile.resolve("theme-creator-drafts/$id")
    for (candidate in candidates) {
        val uri = Uri.parse(candidate)
        if (uri.scheme != "file" || !uri.authority.isNullOrEmpty()) continue
        val file = uri.path?.let(::File)?.canonicalFile ?: continue
        if (file.parentFile == folder && file.extension == "webp") file.delete()
    }
}
