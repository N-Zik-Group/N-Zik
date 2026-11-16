package app.n_zik.android.components.settings.appearance.preview

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import app.n_zik.android.components.glass.GlassTextField as TextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import app.n_zik.android.core.navigation.currentPageNavigationEntry
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.components.ui.NzikCheckbox
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogButtonPlacement
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.ui.screens.home.GlassHomeChips
import app.n_zik.android.components.ui.FullPageLoader
import app.n_zik.android.components.glass.PageButton
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.LocalIndependentGlassBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.updater.models.UpdateEndpoints
import app.n_zik.android.updater.ui.UpdaterBottomPanel
import app.n_zik.android.components.dialog.settings.SettingsDialogTabRow
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.components.navigation.Skeleton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.settings.accounts.GitHubAccount
import app.n_zik.android.components.settings.accounts.rememberGitHubPublicationAuthorization
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SettingsCardSectionDivider
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID
import java.io.File

internal val requiredPreviews = listOf("player", "queue", "lyrics", "home", "navigation", "album",
    "artist", "playlist", "local-playlist", "search", "search-albums", "search-artists")
    .flatMap { listOf("$it-normal", "$it-glass") }
internal val playbackPreviews = listOf("player-playing", "miniplayer-playing")
    .flatMap { listOf("$it-normal", "$it-glass") }
internal val automaticPreviews = requiredPreviews + playbackPreviews
internal const val SubmissionPreviewLimit = 4 * 1024 * 1024
private val SubmissionPreviewMimeTypes = arrayOf("image/png", "image/jpeg", "image/webp", "image/gif")
internal fun submissionMinimumVersionCode(input: String, current: Int, previous: Int?): Int? =
    input.trim().toIntOrNull()?.takeIf { code ->
        code >= FirstThemeStoreVersionCode &&
            (code == previous || code <= maxOf(FirstThemeStoreVersionCode, current))
    }

internal fun submissionDefaultMinimumVersionCode(previous: Int?): Int =
    maxOf(FirstThemeStoreVersionCode, previous ?: FirstThemeStoreVersionCode)

@Composable
internal fun ThemeSubmissionPage(navController: NavController, miniPlayer: @Composable () -> Unit, themeId: String) {
    val context = LocalContext.current
    val profileId = getActiveProfile(context)
    val localKey = themeId.takeIf { it.startsWith("local:") }?.removePrefix("local:")
    val library = remember(context, profileId) { ThemeStoreLibrary(context.preferences) }
    val localThemeSnapshot by rememberSaveable(profileId, localKey) {
        mutableStateOf(localKey?.let(library::download)?.takeIf { it.imported }?.let {
            Json.encodeToString(SavedTheme.serializer(), it)
        })
    }
    val localTheme = remember(localThemeSnapshot) {
        localThemeSnapshot?.let { Json.decodeFromString(SavedTheme.serializer(), it) }
    }
    val creatingLocal = themeId == "local-new"
    val localOnly = creatingLocal || localKey != null
    val catalog = remember(context, localOnly) { themeStoreCatalog(context, localOnly) }
    val published by produceState<List<StoreTheme>?>(null, catalog) {
        value = if (localOnly) emptyList() else try {
            catalog.refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            catalog.cached() ?: emptyList()
        }
    }
    val ownId = if (localOnly) null else signedInCreatorId()
    val existing = localTheme?.theme ?: published.orEmpty().filter {
        it.themeId == themeId && it.author.githubId == ownId
    }.maxByOrNull(StoreTheme::revision)
    var appearanceRetry by remember(themeId) { mutableIntStateOf(0) }
    val publishedAppearance by produceState<Result<String>?>(null, existing, localOnly, appearanceRetry) {
        value = null
        val revision = existing
        if (!localOnly && revision != null) {
            value = try {
                Result.success(catalog.download(revision))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.log.Timber.tag("ThemeStore").w(error, "Could not load published theme for editing")
                Result.failure(error)
            }
        }
    }
    val title = stringResource(when (themeId) {
        "local-new" -> R.string.theme_store_create_local
        "new" -> R.string.theme_store_creator_publish_theme
        else -> if (localKey != null) R.string.theme_store_edit_local else R.string.theme_store_creator_edit_theme
    })
    Skeleton(navController = navController, miniPlayer = miniPlayer, topInsetHandledByContent = true,
        capturePageGlassBackdrop = false,
        navBarContent = { item -> item(0, title, R.drawable.ui) }) {
        val padding = rememberPageChromeContentPadding(PaddingValues(top = 12.dp))
        val loaded = published
        val initial = publishedAppearance
        if (loaded == null || (!localOnly && existing != null && initial == null)) {
            FullPageLoader(modifier = Modifier.fillMaxSize().padding(padding))
        } else if (initial?.isFailure == true) {
            Column(Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                SettingsDescription(stringResource(R.string.theme_store_catalog_unavailable))
                PageButton(onClick = { appearanceRetry++ }) {
                    Text(stringResource(R.string.common_action_retry))
                }
            }
        } else if (creatingLocal || themeId == "new" || existing != null) {
            ThemeSubmissionForm(existing, loaded, padding, localOnly, localTheme,
                initialAppearance = localTheme?.document ?: initial?.getOrNull(), currentAppearance = {
                encodeThemeV1(
                    context.preferences.all + app.n_zik.android.utils.DataStoreUtils.prefs(context).all.filterKeys { it in portableBurgerKeys },
                    GlassProfilePreferences.preferences(context, profileId).all,
                    defaultGlassQuality = app.n_zik.android.components.glass.defaultGlassQuality(
                        android.os.Build.VERSION.SDK_INT,
                        context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true,
                    ),
                )
            }, onLocalSaved = { saved ->
                check(getActiveProfile(context) == profileId)
                if (creatingLocal) library.save(saved)
                else library.saveLocalEdit(checkNotNull(localTheme), saved)
            }, dismiss = { navController.popBackStack() })
        } else {
            Text(stringResource(R.string.theme_store_catalog_unavailable), Modifier.padding(padding))
        }
    }
}

@Composable
private fun ThemeSubmissionForm(
    existing: StoreTheme?,
    published: List<StoreTheme>,
    padding: PaddingValues,
    localOnly: Boolean,
    localTheme: SavedTheme?,
    initialAppearance: String?,
    currentAppearance: () -> String,
    onLocalSaved: (SavedTheme) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember(context, localOnly) { themeStoreCatalog(context, localOnly) }
    val accountId = signedInCreatorId()
    val id = if (localOnly) accountId ?: 0L else accountId ?: return
    val account by GitHubAccount.login.collectAsStateWithLifecycle()
    val login = account?.takeIf { id > 0 } ?: if (localOnly) "local" else return
    val localCreator = if (localOnly) {
        account?.takeIf { id > 0 }
            ?: app.n_zik.android.components.navigation.header.rememberHeaderProfileFace().name.takeThemeText(100)
    } else null
    val scope = rememberCoroutineScope()
    val drafts = remember { context.getSharedPreferences("theme_submission_drafts", 0) }
    val key = when {
        localTheme != null -> "edit-local:${getActiveProfile(context)}:${localTheme.id}"
        localOnly -> "new-local:${getActiveProfile(context)}"
        else -> "$id:${existing?.themeId ?: "new"}"
    }
    val storedDraft = remember(key, published) {
        drafts.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }
    val obsoleteDraft = existing == null && storedDraft != null &&
        published.any { it.themeId == storedDraft.optString("id") }
    fun discardDraft(activePreviews: JSONObject? = null) {
        val savedPreviews = draftPreviewUris(drafts.getString(key, null))
        drafts.edit().remove(key).apply()
        removeUnusedSubmissionPreviews(context, drafts, savedPreviews + submissionPreviewUris(activePreviews))
    }
    LaunchedEffect(key, obsoleteDraft) {
        if (obsoleteDraft) discardDraft()
    }
    var useDraft by rememberSaveable(key) { mutableStateOf<Boolean?>(null) }
    if (storedDraft != null && !obsoleteDraft && useDraft == null) {
        ThemeStoreDraftDialog(dismiss, onDiscard = {
            discardDraft()
            useDraft = false
        }, onContinue = { useDraft = true })
        return
    }
    val draft = storedDraft.takeIf { !obsoleteDraft && useDraft == true }
    val themeId = rememberSaveable(key) { existing?.themeId ?: draft?.optString("id") ?: UUID.randomUUID().toString() }
    var title by rememberSaveable(key) { mutableStateOf(draft?.optString("title") ?: existing?.title.orEmpty()) }
    var description by rememberSaveable(key) { mutableStateOf(draft?.optString("description") ?: existing?.description.orEmpty()) }
    var tags by rememberSaveable(key) { mutableStateOf(draft?.optString("tags") ?: existing?.tags?.joinToString(", ").orEmpty()) }
    val previousMinimum = existing?.compatibility?.minVersionCode
    val currentVersionCode = BuildConfig.VERSION_CODE
    var minimumVersionInput by rememberSaveable(key) {
        mutableStateOf(draft?.takeIf { it.has("minVersionCode") }?.optString("minVersionCode")
            ?.let(::submissionDraftMinimumVersionInput)
            ?: submissionDefaultMinimumVersionCode(previousMinimum).toString())
    }
    val minimumVersionCode = submissionMinimumVersionCode(minimumVersionInput, currentVersionCode, previousMinimum)
    val minimumVersionLabel = when (minimumVersionCode) {
        currentVersionCode -> stringResource(R.string.theme_store_submission_version_with_code,
            BuildConfig.VERSION_NAME.substringBefore('-'), currentVersionCode)
        null -> ""
        else -> stringResource(R.string.theme_store_submission_version_code, minimumVersionCode)
    }
    val canUseReservedTags by produceState(false, catalog, id, localOnly) {
        if (!localOnly) {
            try {
                value = id in catalog.verifiedCreatorIds()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.log.Timber.tag("ThemeStore").w(error, "Could not verify creator tags")
            }
        }
    }
    val catalogTags = remember(published) { published.flatMap { it.tags }.canonicalStoreTags().sorted() }
    val rawTags = tags.split(',').map(String::trim).filter(String::isNotEmpty)
    val submissionTags = rawTags.canonicalStoreTags()
    val hasDuplicateTags = submissionTags.size != rawTags.size
    val hasReservedTags = !localOnly && !canUseReservedTags && submissionTags.any { it in reservedStoreTags }
    val validTags = !hasDuplicateTags && !hasReservedTags && submissionTags.size <= 8 &&
        submissionTags.all { it.themeTextLength <= 30 }
    val tagQuery = canonicalStoreTag(tags.substringAfterLast(','))
    val enteredTags = (if (',' in tags) tags.substringBeforeLast(',').split(',') else emptyList()).canonicalStoreTags()
    val suggestedTags = catalogTags.filter { tag ->
        tag !in enteredTags && (canUseReservedTags || tag !in reservedStoreTags) && tag.contains(tagQuery)
    }

    var storedAppearance by rememberSaveable(key) {
        mutableStateOf(
            draft?.optString("fullAppearance")?.takeIf(String::isNotBlank)
                ?: draft?.optString("appearance") ?: initialAppearance ?: currentAppearance(),
        )
    }
    val compatibleAppearance = remember(storedAppearance) {
        compatibleThemeSubmissionDraftAppearance(storedAppearance)
    }
    val fullAppearance = remember(storedAppearance, compatibleAppearance) {
        compatibleAppearance ?: currentAppearance()
    }
    var usingCurrentAppearance by rememberSaveable(key) {
        mutableStateOf(compatibleAppearance == null ||
            (draft?.optBoolean("usingCurrentAppearance") ?: (initialAppearance == null)))
    }
    val availableSettings = remember(fullAppearance) { parseThemeV1(fullAppearance, allowEmptyCategories = true) }
    val availableCategories = availableSettings.keys
    var expandedCategory by rememberSaveable(key) { mutableStateOf<ThemeCategory?>(null) }
    var expandedReviewCategory by rememberSaveable(key) { mutableStateOf<ThemeCategory?>(null) }
    var selection by rememberSaveable(key) {
        mutableIntStateOf(draft?.optInt("categorySelection", -1) ?: -1)
    }
    val included = availableCategories.filter { selection and (1 shl it.ordinal) != 0 }
    val appearance = remember(fullAppearance, selection) { selectThemeV1Categories(fullAppearance, included.toSet()) }
    var reusePublishedPreviews by rememberSaveable(key) { mutableStateOf(draft?.optBoolean("reusePublishedPreviews", true) ?: true) }
    var previewState by rememberSaveable(key) { mutableStateOf(draft?.optJSONObject("previews")?.toString() ?: "{}") }
    var captionState by rememberSaveable(key) { mutableStateOf(draft?.optJSONObject("captions")?.toString() ?: "{}") }
    var previewSelectionState by rememberSaveable(key) {
        mutableStateOf(draft?.optString("previewSelection", "{}") ?: "{}")
    }
    val previewSelection = remember(previewSelectionState) {
        Json.decodeFromString<SubmissionPreviewSelection>(previewSelectionState)
    }
    var previewTab by rememberSaveable(key) { mutableIntStateOf(0) }
    var selectedPreview by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var expandedPreview by remember(key, previewTab) { mutableStateOf<String?>(null) }
    val captions = remember(captionState) { JSONObject(captionState) }
    val previews = remember(previewState, previewSelection) { buildMap<String, String> {
        val saved = JSONObject(previewState)
        saved.keys().forEach { if (previewSelection.includes(it)) put(it, saved.getString(it)) }
    } }
    fun replacePreviews(updated: JSONObject, unused: Set<String> = emptySet()) {
        val previous = submissionPreviewUris(JSONObject(previewState))
        previewState = updated.toString()
        val retained = submissionPreviewUris(updated)
        removeUnusedSubmissionPreviews(context, drafts, (previous + unused) - retained, retained)
    }
    fun invalidateGeneratedPreviews() {
        reusePublishedPreviews = false
        val remaining = JSONObject(previewState)
        automaticPreviews.forEach { slot ->
            val path = android.net.Uri.parse(remaining.optString(slot)).path
            if (path?.contains("/theme-submission-previews/") == true) remaining.remove(slot)
        }
        replacePreviews(remaining)
    }
    currentPageNavigationEntry()?.let { entry ->
        val cleanup = viewModel<ThemeSubmissionCleanup>(viewModelStoreOwner = entry)
        SideEffect {
            val candidates = submissionPreviewUris(JSONObject(previewState))
            val app = context.applicationContext
            cleanup.onExit = { removeUnusedSubmissionPreviews(app, drafts, candidates) }
        }
    }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var importingPreview by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = picking
        picking = null
        if (uri != null && slot != null) {
            importingPreview = true
            scope.launch {
                var staged: File? = null
                try {
                    val gif = withContext(NzikDispatchers.DATA) {
                        val source = checkNotNull(context.contentResolver.openInputStream(uri))
                        source.use { it.readSubmissionGifOrNull() }?.let { bytes ->
                            requireDecodableSubmissionGif(bytes)
                            writeSubmissionPreview(context, "gif", bytes).also { staged = it }
                        }
                    }
                    val file = gif ?: run {
                        val edited = PickedImageEditor.edit(context, uri) ?: return@launch
                        edited.use { image ->
                            withContext(NzikDispatchers.DATA) {
                                prepareSubmissionPreview(context, image.file).also { staged = it }
                            }
                        }
                    }
                    replacePreviews(JSONObject(previewState).put(slot, android.net.Uri.fromFile(file).toString()))
                    staged = null
                    selectedPreview = slot
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    timber.log.Timber.tag("ThemeStore").w(error, "Could not import submission preview")
                    Toaster.e(R.string.theme_store_preview_import_error)
                } finally {
                    staged?.let { removeUnusedSubmissionPreviews(context, drafts, setOf(android.net.Uri.fromFile(it).toString())) }
                    importingPreview = false
                }
            }
        }
    }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val updated = JSONObject(previewState)
        val received = result.data?.getStringArrayListExtra(ThemePreviewActivity.CapturedFiles)
            .orEmpty().map(::File)
        val bySlot = received.associateBy(File::nameWithoutExtension)
        val existingSlots = previewSelection.published(existing?.previews.orEmpty(), reuseAutomatic = true).keys
        val nonPlayback = (existingSlots + updated.keys().asSequence() + requiredPreviews).toSet() - playbackPreviews.toSet()
        val available = if (nonPlayback.size + playbackPreviews.size <= 36) automaticPreviews else requiredPreviews
        for (slot in available) {
            val file = bySlot[slot] ?: continue
            val previous = android.net.Uri.parse(updated.optString(slot)).path
            if (previous.isNullOrBlank() || previous.contains("/theme-submission-previews/")) {
                updated.put(slot, android.net.Uri.fromFile(file).toString())
            }
        }
        replacePreviews(updated, received.map { android.net.Uri.fromFile(it).toString() }.toSet())
    }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var rights by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val awaiting by GitHubAccount.awaiting.collectAsStateWithLifecycle()
    val revision = localTheme?.theme?.revision ?: ((existing?.revision ?: 0) + 1)
    val publishedPreviews = previewSelection.published(existing?.previews.orEmpty(), reusePublishedPreviews)
    val readyPreviews = publishedPreviews.keys + previews.keys
    val gallerySlots = previewSelection.gallerySlots(publishedPreviews.keys, previews.keys)
    fun captionFor(slot: String): String = captions.optString(slot, publishedPreviews[slot]?.caption.orEmpty())
    val previewSizes by produceState<Map<String, Long>?>(null, step, previews, existing) {
        value = null
        if (step != 2) return@produceState
        value = try {
            withContext(NzikDispatchers.DATA) {
                gallerySlots.associateWith { slot ->
                    val uri = previews[slot]
                    if (uri != null) {
                        val source = android.net.Uri.parse(uri)
                        if (source.scheme == "file") {
                            File(checkNotNull(source.path)).also { check(it.isFile) }.length()
                        } else {
                            val resolver = context.contentResolver
                            val length = resolver.openAssetFileDescriptor(source, "r")?.use { it.length } ?: -1L
                            if (length >= 0) length else checkNotNull(resolver.openInputStream(source)).use {
                                it.readThemeBytes(4 * 1024 * 1024).size.toLong()
                            }
                        }
                    } else {
                        val theme = checkNotNull(existing)
                        checkNotNull(catalog.preview(theme, publishedPreviews.getValue(slot))).length()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            timber.log.Timber.tag("ThemeStore").w(error, "Could not calculate preview size")
            emptyMap()
        }
    }
    val sections = listOf("Information", "Previews", "Review")
    fun saveDraft() {
        val previous = draftPreviewUris(drafts.getString(key, null))
        val data = JSONObject().put("id", themeId).put("title", title).put("description", description)
            .put("minVersionCode", minimumVersionInput)
            .put("tags", tags).put("appearance", appearance).put("previews", JSONObject(previews.toMap()))
            .put("captions", captions)
            .put("previewSelection", previewSelectionState)
            .put("fullAppearance", fullAppearance).put("categorySelection", selection)
            .put("usingCurrentAppearance", usingCurrentAppearance)
            .put("reusePublishedPreviews", reusePublishedPreviews)
        drafts.edit().putString(key, data.toString()).apply()
        useDraft = true
        removeUnusedSubmissionPreviews(context, drafts, previous)
    }
    suspend fun packageTheme(): Pair<StoreTheme, Map<String, ByteArray>> = withContext(NzikDispatchers.DATA) {
        require(validTags)
        val compatibility = StoreCompatibility(checkNotNull(minimumVersionCode))
        val files = mutableMapOf<String, ByteArray>()
        val appearanceBytes = appearance.encodeToByteArray()
        parseThemeV1(appearance)
        files["appearance.json"] = appearanceBytes
        files["reviews.json"] = """{"schemaVersion":1,"reviews":[]}""".encodeToByteArray()
        val gallery = gallerySlots.map { slot ->
            val previous = publishedPreviews[slot]
            val caption = captionFor(slot).trim().takeIf(String::isNotEmpty)
            if (slot !in previews && previous != null && existing != null) {
                val file = checkNotNull(catalog.preview(existing, previous))
                files[previous.path] = file.inputStream().use { it.readThemeBytes(4 * 1024 * 1024) }
                return@map previous.copy(caption = caption)
            }
            val uri = android.net.Uri.parse(previews.getValue(slot))
            val resolver = context.contentResolver
            val mimeType = if (uri.scheme == "file") {
                when (File(checkNotNull(uri.path)).extension) {
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    else -> error("Unsupported preview image")
                }
            } else resolver.getType(uri)
            val extension = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> error("Unsupported preview image")
            }
            val bytes = checkNotNull(resolver.openInputStream(uri)).use { it.readThemeBytes(4 * 1024 * 1024) }
            require(bytes.isNotEmpty())
            val path = "previews/$slot.$extension"
            files[path] = bytes
            val scene = slot.substringBeforeLast('-')
            StorePreview(path, if (scene in listOf("player", "queue", "navigation")) scene else "other", bytes.sha256(), caption)
        }
        val theme = StoreTheme(1, themeId, revision, title.trim(), description.trim(),
            if (login == "local") StoreAuthor(0, login) else StoreAuthor(id, login), submissionTags,
            compatibility, StoreFile("appearance.json", appearanceBytes.sha256()), gallery,
            localCreatorName = localCreator)
        files["manifest.json"] = Json.encodeToString(StoreTheme.serializer(), theme).encodeToByteArray()
        theme to files
    }
    fun saveLocalTheme() {
        busy = true
        scope.launch {
            try {
                val (theme, files) = packageTheme()
                withContext(NzikDispatchers.DATA) {
                    cacheThemeArchivePreviews(context, theme) { files.getValue(it) }
                }
                onLocalSaved(SavedTheme(theme.title, files.getValue(theme.appearance.path).decodeToString(), theme, imported = true, storeOrigin = localTheme?.storeOrigin))
                discardDraft(JSONObject(previewState))
                Toaster.s(R.string.theme_store_local_saved)
                dismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.log.Timber.tag("ThemeStore").w(error, "Could not update local theme")
                Toaster.e(R.string.theme_store_local_save_failed)
            } finally {
                busy = false
            }
        }
    }
    val submit = {
        busy = true
        Toaster.i(R.string.theme_store_submission_sending)
        scope.launch {
            try {
                val token = checkNotNull(GitHubAccount.tokenFor(context, id))
                val (theme, files) = packageTheme()
                val url = submitCreatorProfile(token, id, files.mapKeys { theme.directory + it.key }, theme)
                val number = url.substringAfterLast('/').toInt()
                context.getSharedPreferences("theme_creator_submissions", 0).edit().putString("$id:$number", "open").apply()
                discardDraft(JSONObject(previewState))
                notifyCreatorSubmissionSent(context, id, number, R.string.theme_store_theme_submitted)
                Toaster.s(R.string.theme_store_theme_submitted)
                dismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (blocked: CreatorPublicationBlocked) {
                Toaster.e(blocked.publicMessage, android.widget.Toast.LENGTH_LONG)
            } catch (_: CreatorPublicationWorkflowPermission) {
                GitHubAccount.requireWorkflowAuthorization(context)
                Toaster.i(R.string.theme_store_creator_workflow_authorization)
            } catch (expired: CreatorPublicationSessionExpired) {
                GitHubAccount.clearPublicationToken(context)
                Toaster.i(R.string.theme_store_creator_session_expired)
            } catch (error: Exception) {
                timber.log.Timber.tag("ThemeStore").w(error, "Theme submission failed")
                Toaster.e(R.string.theme_store_theme_submit_error)
            } finally {
                busy = false
            }
        }
        Unit
    }
    val authorizePublication = rememberGitHubPublicationAuthorization(submit)
    fun publish() {
        saveDraft()
        if (GitHubAccount.tokenFor(context, id) != null) submit()
        else authorizePublication(id)
    }
    val informationValid = title.isNotBlank() && description.isNotBlank() && included.isNotEmpty() && validTags &&
        minimumVersionCode != null
    val footerGlass = rememberGlassSettings()
    val footerBackdrop = rememberLayerBackdrop()
    val captureFooter = footerGlass.usesGlassMaterial(GlassArea.SettingsCards) &&
        glassRenderer(footerGlass) != GlassRenderer.Tint
    fun navigateBack() {
        if (step > 0) {
            step--
        } else {
            removeUnusedSubmissionPreviews(context, drafts, submissionPreviewUris(JSONObject(previewState)))
            dismiss()
        }
    }
    BackHandler { if (!busy && !importingPreview) navigateBack() }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colorPalette().background0,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            CompositionLocalProvider(
                LocalIndependentGlassBackdrop provides LocalIndependentGlassBackdrop.current.takeUnless { captureFooter },
            ) {
                UpdaterBottomPanel(
                    backdrop = footerBackdrop.takeIf { captureFooter },
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    val palette = colorPalette()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageButton(
                            onClick = ::navigateBack,
                            modifier = Modifier.weight(1f),
                            enabled = !busy && !importingPreview,
                            elevation = null,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.red),
                        ) {
                            Text(if (step == 0) stringResource(R.string.common_action_cancel) else "Back")
                        }
                        PageButton(
                            onClick = {
                                when {
                                    step < 2 -> step++
                                    localOnly -> saveLocalTheme()
                                    else -> publish()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            elevation = null,
                            enabled = when (step) {
                                0 -> informationValid
                                1 -> !importingPreview && readyPreviews.containsAll(requiredPreviews)
                                else -> informationValid && !busy && !awaiting && (localOnly || rights)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                        ) {
                            Text(when {
                                step < 2 -> "Continue"
                                localOnly -> stringResource(R.string.theme_store_local_save)
                                else -> "Submit for review"
                            })
                        }
                    }
                }
            }
        },
    ) { footerPadding ->
        Column(
            Modifier.fillMaxSize()
                .background(colorPalette().background0)
                .then(if (captureFooter) Modifier.layerBackdrop(footerBackdrop) else Modifier)
                .padding(top = padding.calculateTopPadding()),
        ) {
            SubmissionStepper(sections, step)
            key(step) {
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = footerPadding.calculateBottomPadding() + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (step) {
                        0 -> {
                            SettingsSectionCard("Theme information", R.drawable.ui, bottomSpacing = 0.dp, content = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SubmissionField(title, { if (it.themeTextLength <= 100) title = it }, "Theme title")
                                    SubmissionField(description, { if (it.themeTextLength <= 1000) description = it }, "Description", 3)
                                    SubmissionField(tags, { tags = it }, stringResource(R.string.theme_store_tags_entry))
                                    if (hasDuplicateTags || hasReservedTags) {
                                        SettingsDescription(stringResource(
                                            if (hasReservedTags) R.string.theme_store_tags_reserved
                                            else R.string.theme_store_tags_duplicate,
                                        ))
                                    }
                                    if (suggestedTags.isNotEmpty() && enteredTags.size < 8) {
                                        GlassHomeChips<String?>(
                                            chips = suggestedTags.map { it to storeTagLabel(it) },
                                            currentValue = null,
                                            onValueUpdate = { tag ->
                                                if (tag != null) tags = (enteredTags + tag).joinToString(", ", postfix = ", ")
                                            },
                                        )
                                    }
                                }
                            })
                            if (!localOnly) SettingsSectionCard("Author", R.drawable.github_icon, bottomSpacing = 0.dp, content = {
                                SubmissionAuthor(login)
                            })
                            val minimumLabel = stringResource(R.string.theme_store_submission_minimum_version)
                            SettingsSectionCard(minimumLabel, R.drawable.information, bottomSpacing = 0.dp, content = {
                                SettingsDescription(stringResource(R.string.theme_store_submission_minimum_description,
                                    BuildConfig.VERSION_NAME.substringBefore('-'), currentVersionCode, FirstThemeStoreVersionCode))
                                TextField(
                                    value = minimumVersionInput,
                                    onValueChange = { value ->
                                        if (value.length <= 10 && value.all { it in '0'..'9' }) minimumVersionInput = value
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.theme_store_submission_version_code_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    isError = minimumVersionCode == null,
                                    colors = app.n_zik.android.components.dialog.common.InputDialog.defaultTextFieldColors(),
                                )
                                val versionCodeLink = stringResource(R.string.theme_store_submission_find_version_code)
                                val linkColor = colorPalette().accent
                                Text(
                                    text = buildAnnotatedString {
                                        withLink(LinkAnnotation.Url(
                                            url = UpdateEndpoints.REPO_URL + "/blob/main/Updater/README.md",
                                            styles = TextLinkStyles(style = SpanStyle(
                                                color = linkColor, textDecoration = TextDecoration.Underline,
                                            )),
                                        )) {
                                            append(versionCodeLink)
                                        }
                                    },
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                                )
                                if (minimumVersionCode == null) {
                                    SettingsDescription(stringResource(
                                        if (FirstThemeStoreVersionCode >= currentVersionCode) R.string.theme_store_submission_version_required
                                        else R.string.theme_store_submission_version_range,
                                        FirstThemeStoreVersionCode, maxOf(FirstThemeStoreVersionCode, currentVersionCode),
                                    ))
                                }
                            })
                            SettingsSectionCard(stringResource(R.string.theme_store_submission_included), R.drawable.ui,
                                bottomSpacing = 0.dp, content = {
                                    if (initialAppearance != null || draft != null) {
                                        OtherSettingsEntry(
                                            title = stringResource(R.string.theme_store_local_use_current),
                                            text = stringResource(R.string.theme_store_local_use_current_description),
                                            icon = R.drawable.ui,
                                            onClick = {
                                                val updated = currentAppearance()
                                                if (updated != fullAppearance) {
                                                    storedAppearance = updated
                                                    usingCurrentAppearance = true
                                                    if (initialAppearance != null) selection = -1
                                                    invalidateGeneratedPreviews()
                                                }
                                            },
                                        )
                                    }
                                    if (usingCurrentAppearance && availableCategories.size < ThemeCategory.entries.size) {
                                        val omitted = ThemeCategory.entries.filterNot { it in availableCategories }
                                            .map { stringResource(it.submissionLabel()) }
                                        SettingsDescription(stringResource(
                                            R.string.theme_store_submission_unsupported_categories,
                                            omitted.joinToString(", "),
                                        ))
                                    }
                                    availableCategories.forEach { category ->
                                        val checked = category in included
                                        ThemeSettingsCategoryRow(
                                            category.submissionLabel(), category.submissionIcon(), availableSettings.getValue(category),
                                            checked, expandedCategory == category,
                                            onExpand = { expandedCategory = if (expandedCategory == category) null else category },
                                            onCheckedChange = {
                                                selection = selection xor (1 shl category.ordinal)
                                                invalidateGeneratedPreviews()
                                            },
                                        )
                                    }
                                })
                        }
                        1 -> {
                            SettingsSectionCard("Show your theme", R.drawable.ui, bottomSpacing = 0.dp, content = {
                                Text("Generate Normal and Glass screenshots with demo content, or replace them with images from your device.",
                                    color = colorPalette().textSecondary)
                                OtherSettingsEntry("Generate previews", "${automaticPreviews.count { it in readyPreviews }} / ${automaticPreviews.size} ready", R.drawable.ui,
                                    onClick = { saveDraft(); capture.launch(ThemePreviewActivity.submissionIntent(context, appearance)) })
                                val variants = listOf(
                                    stringResource(R.string.theme_store_preview_normal),
                                    stringResource(R.string.theme_store_preview_glass),
                                )
                                val suffix = if (previewTab == 0) "-normal" else "-glass"
                                val slots = submissionPreviewVariantSlots(gallerySlots, glass = previewTab == 1)
                                val selected = selectedPreview?.takeIf { it in slots } ?: slots.first()
                                val selectedLabel = submissionPreviewLabel(selected)
                                val availableGallery = slots.filter { it in readyPreviews }
                                ThemePreviewGalleryDialog(
                                    previews = availableGallery,
                                    initialPage = expandedPreview?.let(availableGallery::indexOf) ?: -1,
                                    onDismiss = { expandedPreview = null },
                                    itemKey = { it },
                                    caption = { captionFor(it).ifBlank { submissionPreviewLabel(it) } },
                                ) { slot ->
                                    val previous = publishedPreviews[slot]
                                    val remote = if (existing != null && previous != null && slot !in previews) {
                                        rememberStorePreview(existing, previous, catalog, 0)
                                    } else null
                                    val model = previews[slot]?.let(android.net.Uri::parse) ?: remote?.file
                                    ImageCacheFactory.LocalAsyncImage(
                                        model = model,
                                        contentDescription = captionFor(slot).ifBlank { submissionPreviewLabel(slot) },
                                        modifier = Modifier.fillMaxSize(),
                                        placeholder = R.drawable.loader,
                                        contentScale = ContentScale.Fit,
                                        shape = null,
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SettingsDialogTabRow(
                                        labels = variants, selectedTabIndex = previewTab,
                                        equalWidthWhenFitting = true, followNestedTarget = false,
                                    ) { previewTab = it }
                                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                                        val width = (maxWidth - 16.dp) / 3
                                        key(previewTab) {
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(slots, key = { it }) { slot ->
                                                    val previous = publishedPreviews[slot]
                                                    val remote = if (existing != null && previous != null && slot !in previews) {
                                                        rememberStorePreview(existing, previous, catalog, 0)
                                                    } else null
                                                    val model = previews[slot]?.let(android.net.Uri::parse) ?: remote?.file
                                                    val label = submissionPreviewLabel(slot)
                                                    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        ThemeCardSurface(
                                                            modifier = Modifier.fillMaxWidth().aspectRatio(remote?.aspectRatio ?: 9f / 20f),
                                                            onClick = {
                                                                selectedPreview = slot
                                                                if (model != null) expandedPreview = slot
                                                            },
                                                        ) {
                                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                if (model == null) {
                                                                    StorePreviewImage(null, label, showImageRim = false)
                                                                } else {
                                                                    ImageCacheFactory.LocalAsyncImage(model = model,
                                                                        contentDescription = label, modifier = Modifier.fillMaxSize(),
                                                                        placeholder = R.drawable.loader, contentScale = ContentScale.Fit)
                                                                }
                                                            }
                                                        }
                                                        Text(captionFor(slot).ifBlank { label },
                                                            modifier = Modifier.fillMaxWidth().clickable { selectedPreview = slot },
                                                            style = typography().xs,
                                                            textAlign = TextAlign.Center,
                                                            color = if (slot == selected) colorPalette().accent else colorPalette().text,
                                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    PageButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !importingPreview,
                                        onClick = {
                                            picking = selected
                                            picker.launch(SubmissionPreviewMimeTypes)
                                        },
                                    ) {
                                        Text(stringResource(if (selected in readyPreviews) R.string.theme_store_preview_replace_selected
                                            else R.string.theme_store_preview_choose_selected, selectedLabel))
                                    }
                                    PageButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !importingPreview && gallerySlots.size < 36,
                                        onClick = {
                                            picking = "extra-${UUID.randomUUID()}$suffix"
                                            picker.launch(SubmissionPreviewMimeTypes)
                                        },
                                    ) { Text(stringResource(R.string.theme_store_preview_add_extra)) }
                                    if (selected in readyPreviews && selected !in automaticPreviews) {
                                        PageButton(
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !importingPreview,
                                            onClick = {
                                                previewSelectionState = Json.encodeToString(previewSelection.withoutExtra(selected))
                                                replacePreviews(JSONObject(previewState).apply { remove(selected) })
                                                captionState = JSONObject(captionState).apply { remove(selected) }.toString()
                                                selectedPreview = null
                                            },
                                        ) { Text(stringResource(R.string.theme_store_preview_remove_extra)) }
                                    }
                                    SettingsDescription(stringResource(R.string.theme_store_preview_import_requirements_with_gif))
                                    if (selected in readyPreviews) {
                                        SubmissionField(captionFor(selected), { caption ->
                                            if (caption.themeTextLength <= 160) {
                                                captionState = JSONObject(captionState).put(selected, caption).toString()
                                            }
                                        }, if (selected in requiredPreviews) stringResource(R.string.theme_store_caption_for, selectedLabel)
                                        else stringResource(R.string.theme_store_preview_extra_title))
                                    }
                                }

                            })
                        }
                        2 -> {
                            val configurationSize = android.text.format.Formatter.formatShortFileSize(
                                context, appearance.encodeToByteArray().size.toLong(),
                            )
                            val summarySlot = if (rememberGlassSettings().enabled && "player-glass" in readyPreviews) {
                                "player-glass"
                            } else "player-normal"
                            val localCover = previews[summarySlot]?.let(android.net.Uri::parse)?.path?.let(::File)
                            val previous = publishedPreviews[summarySlot]
                            val remoteCover = if (localCover == null && existing != null && previous != null) {
                                rememberStorePreview(existing, previous, catalog, 0)?.file
                            } else null
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                ThemeCardSurface(Modifier.widthIn(max = 208.dp)) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(StorePreviewAspectRatio)) {
                                        StorePreviewImage(
                                            localCover ?: remoteCover,
                                            captionFor(summarySlot).ifBlank { submissionPreviewLabel(summarySlot) },
                                            showImageRim = false,
                                            fitPortraitFrame = true,
                                        )
                                    }
                                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                        ThemeCardIdentity(title, localCreator ?: login, official = !localOnly && canUseReservedTags)
                                        SettingsDescription(description, Modifier.padding(top = 8.dp))
                                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
                                            StoreText(configurationSize, secondary = true, style = typography().xxs)
                                        }
                                    }
                                }
                            }
                            SettingsSectionCard("Submission summary", R.drawable.ui, bottomSpacing = 0.dp, content = {
                                SubmissionSummaryRow("Configuration", configurationSize)
                                SettingsCardSectionDivider()
                                val count = readyPreviews.size
                                val sizes = previewSizes
                                val previewSummary = when {
                                    sizes == null -> stringResource(R.string.theme_store_preview_size_loading, count)
                                    sizes.isEmpty() -> stringResource(R.string.theme_store_preview_size_unavailable, count)
                                    else -> stringResource(R.string.theme_store_preview_size_total, count,
                                        android.text.format.Formatter.formatShortFileSize(context, sizes.values.sum()))
                                }
                                SubmissionSummaryRow("Previews", previewSummary)
                                for (slot in gallerySlots.filterNot { it in automaticPreviews }) {
                                    val sizeLabel = sizes?.get(slot)?.let {
                                        android.text.format.Formatter.formatShortFileSize(context, it)
                                    } ?: "-"
                                    SubmissionSummaryRow(captionFor(slot).ifBlank { submissionPreviewLabel(slot) }, sizeLabel)
                                }
                                SettingsCardSectionDivider()
                                if (!localOnly) SubmissionSummaryRow("Revision", revision.toString())
                                SubmissionSummaryRow(stringResource(R.string.theme_store_submission_minimum_version), minimumVersionLabel)
                            })
                            SettingsSectionCard(stringResource(R.string.theme_store_submission_included), R.drawable.ui, bottomSpacing = 0.dp, content = {
                                included.forEach { category ->
                                    ThemeSettingsCategoryRow(
                                        label = category.submissionLabel(),
                                        icon = category.submissionIcon(),
                                        settings = availableSettings.getValue(category),
                                        checked = true,
                                        isExpanded = expandedReviewCategory == category,
                                        onExpand = {
                                            expandedReviewCategory = category.takeUnless { it == expandedReviewCategory }
                                        },
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                SettingsDescription(stringResource(R.string.theme_store_submission_saved_content))
                            })
                            if (!localOnly) SettingsSectionCard("Before submitting", R.drawable.information, bottomSpacing = 0.dp, content = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("GitHub creates or updates a pull request for review. Explicit content will be rejected and may result in a ban.", style = typography().xs)
                                    Row(
                                        Modifier.fillMaxWidth().toggleable(rights, enabled = !busy,
                                            role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { rights = it }),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        NzikCheckbox(checked = rights, onCheckedChange = null)
                                        Text("I have the rights to publish these previews.", style = typography().xs)
                                    }
                                }
                            })
                            val saveDraftLabel = stringResource(R.string.theme_store_creator_save_draft)
                            SettingsSectionCard(saveDraftLabel, R.drawable.information, bottomSpacing = 0.dp, content = {
                                SettingsDescription(stringResource(R.string.theme_store_creator_draft))
                                PageButton(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    enabled = !busy,
                                    onClick = {
                                        saveDraft()
                                        Toaster.s(R.string.theme_store_creator_draft_saved)
                                        dismiss()
                                    },
                                ) { Text(saveDraftLabel) }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionStepper(sections: List<String>, step: Int) {
    val palette = colorPalette()
    val glass = rememberGlassSettings()
    val area = GlassArea.ActionButtons
    val shape = uiRoundnessShape()
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        sections.forEachIndexed { index, section ->
            val active = index <= step
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                Row(Modifier.fillMaxWidth().padding(top = 13.dp)) {
                    Spacer(Modifier.weight(1f).height(2.dp).background(
                        if (index == 0) androidx.compose.ui.graphics.Color.Transparent
                        else if (active) palette.accent else palette.background1))
                    Spacer(Modifier.weight(1f).height(2.dp).background(
                        if (index == sections.lastIndex) androidx.compose.ui.graphics.Color.Transparent
                        else if (index < step) palette.accent else palette.background1))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val tone = if (active) palette.accent else palette.background1
                    val marker = if (glass.usesGlassMaterial(area)) {
                        Modifier.glassMaterial(glass.forArea(area), shape, filmColor = tone, drawShadow = false)
                    } else {
                        Modifier.background(tone, shape).glassAreaContour(glass, area, shape)
                    }
                    Box(Modifier.size(28.dp).then(marker), contentAlignment = Alignment.Center) {
                        if (index < step) {
                            Icon(painterResource(R.drawable.checkmark), contentDescription = null,
                                modifier = Modifier.size(16.dp), tint = palette.onAccent)
                        } else {
                            Text("${index + 1}", style = typography().xs,
                                color = if (active) palette.onAccent else palette.text)
                        }
                    }
                    Text(section, style = typography().xs, color = if (index == step) palette.text else palette.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SubmissionField(value: String, onChange: (String) -> Unit, label: String, lines: Int = 1) {
    TextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        minLines = lines, singleLine = lines == 1, shape = uiRoundnessShape(),
        colors = app.n_zik.android.components.dialog.common.InputDialog.defaultTextFieldColors())
}

@Composable
private fun SubmissionAuthor(login: String) {
    Column {
        Text(login, style = typography().s)
        Text("@$login", style = typography().xs, color = colorPalette().textSecondary)
    }
}

@Composable
private fun SubmissionSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = typography().s)
        Text(value, style = typography().xs, color = colorPalette().textSecondary)
    }
}


private fun submissionPreviewLabel(slot: String): String =
    if (slot.startsWith("extra-")) "Extra preview"
    else slot.substringBeforeLast('-').replace('-', ' ').replaceFirstChar(Char::uppercaseChar)

private fun ThemeCategory.submissionLabel(): Int = when (this) {
    ThemeCategory.COLORS -> R.string.theme_store_group_colors
    ThemeCategory.PLAYER_AND_QUEUE -> R.string.theme_store_group_player_queue
    ThemeCategory.NAVIGATION -> R.string.theme_store_group_navigation
    ThemeCategory.GLASS -> R.string.theme_store_preview_glass
}

private fun prepareSubmissionPreview(context: android.content.Context, source: File): File {
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(source.path, options)
    require(options.outWidth > 0 && options.outHeight > 0)
    options.inJustDecodeBounds = false
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > SubmissionPreviewMaxEdge) {
        options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
    }
    val bitmap = checkNotNull(android.graphics.BitmapFactory.decodeFile(source.path, options))
    try {
        val buffer = java.io.ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 85, buffer))
        require(buffer.size() in 1..SubmissionPreviewLimit)
        return writeSubmissionPreview(context, "webp", buffer.toByteArray())
    } finally {
        bitmap.recycle()
    }
}

private fun writeSubmissionPreview(context: android.content.Context, extension: String, bytes: ByteArray): File {
    val folder = File(context.filesDir, "theme-submission-imports")
    check(folder.isDirectory || folder.mkdirs() || folder.isDirectory)
    val file = android.util.AtomicFile(File(folder, "${UUID.randomUUID()}.$extension"))
    writeThemeFile(file, bytes)
    return file.baseFile
}

private fun ThemeCategory.submissionIcon(): Int = when (this) {
    ThemeCategory.COLORS -> R.drawable.color_palette
    ThemeCategory.PLAYER_AND_QUEUE -> R.drawable.musical_notes
    ThemeCategory.NAVIGATION -> R.drawable.arrow_forward
    ThemeCategory.GLASS -> R.drawable.sparkles
}

@Composable
internal fun ThemeStoreDraftDialog(onDismiss: () -> Unit, onDiscard: () -> Unit, onContinue: () -> Unit) {
    DefaultDialog(
        title = stringResource(R.string.theme_store_draft_found),
        onDismiss = onDismiss,
        footer = {
            ButtonRow(listOf(
                DialogButton(stringResource(R.string.theme_store_draft_discard), onDiscard,
                    danger = true, placement = DialogButtonPlacement.Start, wrapLabel = true),
                DialogButton(stringResource(R.string.theme_store_draft_resume), onContinue,
                    primary = true, wrapLabel = true),
            ))
        },
    ) {
        SettingsDescription(stringResource(R.string.theme_store_draft_choice))
    }
}
