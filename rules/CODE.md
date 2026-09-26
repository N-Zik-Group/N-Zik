# Code Quality Rules

**Version:** 1.5.0 | **Last updated:** 2026-09-26

## Naming Conventions

- **Classes/PascalCase**: `MusicDatabase`, `PlayerService`, `LyricsScreen`
- **Functions/camelCase**: `getSongById`, `updatePlaylist`, `handlePlaybackError`
- **Constants/UPPER_SNAKE_CASE**: `LOCAL_KEY_PREFIX`, `MAX_RETRY_COUNT`
- **Variables/camelCase**: `songList`, `isPlaying`, `currentPosition`
- **Packages/lowercase**: `app.n_zik.android.playback`, `app.n_zik.android.core.database`

## Kotlin/Compose Anti-Patterns

NEVER use these — they cause bugs, crashes, or performance issues:

```kotlin
// BAD — structured scope required
GlobalScope.launch { ... }

// GOOD — use lifecycle-aware scope
viewModelScope.launch { ... }
lifecycleScope.launch { ... }
```

```kotlin
// BAD — blocks thread
runBlocking { ... }

// GOOD — suspend function
suspend fun fetchData() { ... }
```

```kotlin
// BAD — may skip collection in background
collectAsState()

// GOOD — lifecycle-aware
collectAsStateWithLifecycle()
```

```kotlin
// BAD — race condition
_state.value = _state.value.copy(loading = true)

// GOOD — atomic update
_state.update { it.copy(loading = true) }
```

```kotlin
// BAD — no key, bad performance
LazyColumn {
    items(list) { item -> ItemRow(item) }
}

// GOOD — key + contentType
LazyColumn {
    items(list, key = { it.id }, contentType = { "item" }) { item ->
        ItemRow(item)
    }
}
```

Rules:

- NEVER use `GlobalScope` — use `viewModelScope`, `lifecycleScope`, or structured scopes
- NEVER use `runBlocking` in production code (use suspend functions). A few pre-existing production usages carry a justification comment (e.g. ExoPlayer sync APIs) — any NEW one must carry an equivalent comment. In JVM unit tests the established convention is `runBlocking` (100+ existing tests) — match it; `runTest` is not used in this codebase
- NEVER use `collectAsState()` — use `collectAsStateWithLifecycle()`
- NEVER do a read-modify-write (`_state.value = _state.value.copy(...)`) — use `_state.update { it.copy(...) }`. Direct assignment (`_state.value = …`) is allowed only to set the initial state
- Use `StateFlow` over `LiveData` — expose a single `data class *UiState` per feature (sealed state class only when states are mutually exclusive, e.g. `FindUiState`)
- Data params to children: annotate with `@Stable` or `@Immutable`
- No IO/DB/network in composition body
- LazyColumn/LazyRow must have `key` + `contentType`

## Kotlin Null-Safety

- NEVER use `!!` operator unless absolutely justified (add comment explaining why)
- Prefer `?.` + `let` for safe calls
- Use `requireNotNull()` for preconditions with clear error messages
- Use `checkNotNull()` for state assertions
- Return early for null values instead of deeply nested null checks

```kotlin
// BAD — will throw NPE
val name = user!!.name

// GOOD — safe call + let
user?.let { nameTextView.text = it.name }

// GOOD — requireNotNull with message
val playlist = requireNotNull(playlistDao.findById(id)) { "Playlist $id not found" }
```

## File Placement (MANDATORY)

New files MUST go under `app.n_zik.android.*`. NEVER create new files under `app.it.fast4x.rimusic.*` or `app.kreate.android.*`.

| Component type                 | Location                                           |
| ------------------------------ | -------------------------------------------------- |
| Generic reusable dialogs       | `components/dialog/`                               |
| Domain-specific dialogs        | `components/dialog/{domain}/` (e.g. `dialog/song/`, `dialog/album/`) |
| Domain menus                   | `components/menu.{domain}/`                        |
| Page-level screens             | `components.ui.screens.{screen}/`                  |
| ViewModels                     | co-located with their screen (`components/ui/screens/{screen}/`, or the domain screen package, e.g. `components/musicbrainz/insights/`) |
| Repositories                   | collocated with their domain package (one repository per domain, e.g. `ShazamRepository` in `recognition/`) |
| Player UI + lyrics             | `components/player/` + `components/player/lyrics/` |
| Settings components            | `components/settings/`                             |
| Enums                          | `enums/`                                           |
| Extensions (optional features) | package `extensions/{feature}/` under `app.n_zik.android` (the Gradle subprojects in `N-Zik/extensions/` are separate API modules — see `settings.gradle.kts`) |
| Database tables & migrations   | `core/database/` (migrations in `core/database/migration/`; note: `core/migration/` holds 3 launch-time cleanup objects — NOT Room migrations) |
| Network layer                  | `core/network/`                                    |
| Services (player, download)    | `playback/services/`, `download/services/`         |
| Dependency injection           | plain constructor injection (no DI framework in the app module) |
| Navigation (sealed route defs) | `core/navigation/`                                 |
| Utilities                      | `utils/`                                           |

## Imports

- Imports at top of file ALWAYS
- NO wildcard imports (`import com.example.*`)
- NO inline fully qualified names (`java.util.List`) unless absolute naming conflict
- Remove unused imports before committing
- Group: stdlib, third-party, project-internal

## Comments

- Add comments only for complex or non-obvious logic
- Do NOT restate what the code already says
- Use KDoc for public APIs (see example below)
- Mark TODOs with `// TODO(author): description`

```kotlin
// BAD - restates the code
// Increment counter by one
counter++

// GOOD - explains WHY
// Offset by 1 because Room IDs are 1-indexed but list indices are 0-indexed
val adjustedIndex = roomIndex - 1
```

### KDoc Format

```kotlin
/**
 * Fetches lyrics for a given song from the LRCLIB API.
 *
 * @param songId The unique identifier of the song
 * @param artistName The artist name for search
 * @param songTitle The song title for search
 * @return LyricsResult containing synced lyrics or error
 * @throws NetworkException if API is unreachable
 */
suspend fun fetchLyrics(songId: String, artistName: String, songTitle: String): LyricsResult
```

## Dead Code

- Remove commented-out code blocks
- Remove unused functions, classes, variables, parameters
- If kept for reference, add `// TODO: reason`

## Logging — Timber ONLY

NEVER use `println`, `Log.d`, `System.out`, `e.printStackTrace()`. Use Timber with tags.

> **Sanctioned exception:** `utils/logging/FileLoggingTree.kt` is itself a Timber `Tree` implementation and may use `android.util.Log` internally — do not flag or "fix" it.

```kotlin
import timber.log.Timber

class MyClass {
    fun doSomething() {
        Timber.tag("MyClass").d("Doing something")
    }
}
```

### Logging Levels

- **d** (debug): Development-only diagnostics, stripped in release
- **i** (info): Important lifecycle events (app start, feature used)
- **w** (warn): Recoverable issues (deprecated API, fallback used)
- **e** (error): Unrecoverable failures (API call failed, data corruption)

## Error Handling — runCatching

```kotlin
runCatching {
    riskyOperation()
}.onFailure { e ->
    Timber.tag("MyClass").e(e, "Operation failed")
}
```

NEVER swallow exceptions silently. ALWAYS log with Timber.

## Performance

- Use `NzikDispatchers` named dispatchers only — see **Coroutines & Dispatchers** below (raw `Dispatchers.*` is allowed inside `NzikDispatchers` itself, nowhere else new)
- Use `withContext` to switch between named dispatchers
- Cancel coroutines in `onCleared()` or `DisposableEffect`
- Avoid holding Activity/Context references in long-lived objects
- Use Coil for image loading
- Profile startup and rendering performance
- Avoid ANR: never block main thread for >5 seconds

## Coroutines & Dispatchers — NzikDispatchers (MANDATORY)

All named threads/dispatchers in the app come from `NzikDispatchers` (`app.n_zik.android.utils.coroutines`) — the single source of truth for threading (issue #606). NEVER add new raw `Dispatchers.IO` / `Dispatchers.Default` / `Dispatchers.Main` / hand-rolled `Executors.*` usages in app code — use the named entries below. (A few pre-existing raw `Dispatchers.IO` usages remain in the rewind screen — do NOT opportunistically migrate them unless the user asks.)

| Entry point                                            | Threads                       | Use for                                                                                                                        |
| ------------------------------------------------------ | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `NzikDispatchers.UI`                                   | `Dispatchers.Main`            | UI & gestures; the ONLY place to touch ExoPlayer `player.*`                                                                     |
| `NzikDispatchers.PLAYBACK`                             | single `nzik-playback`        | Audio & playback work — single thread guarantees ordering                                                                       |
| `NzikDispatchers.VISUALIZER`                           | single `nzik-visualizer`      | Visualizer FFT capture — serializes access to the native `Visualizer` (shared per sessionId)                                    |
| `NzikDispatchers.MEDIA`                                | pool of 2 `nzik-media-*`      | CPU-bound media work: queue conversion at playback start, palette extraction, LRC/TTML parsing, bitmap circling, challenge JSON parsing |
| `NzikDispatchers.DATA`                                 | `Dispatchers.IO`              | Network & disk IO: album art, song info, DB changes, downloads                                                                  |
| `NzikDispatchers.ROOM_QUERY_EXECUTOR` / `ROOM_TX_EXECUTOR` | 4-thread `nzik-room-query-*` / `nzik-room-tx-*` | Pass to Room's `queryExecutor` / `transactionExecutor` — same off-main behavior as Room's default, visible by name in ANR/profiler traces |

Rules:

- Fire-and-forget scopes (deliberately never cancelled): build them with `NzikDispatchers.fireAndForget(dispatcher)` (or the `CoroutineContext` overload) — it adds a `SupervisorJob` + a Timber `CoroutineExceptionHandler`, so one exception can no longer cancel siblings or crash the process. The helper NEVER cancels the scope: callers that used to cancel their scope keep doing so on the returned `CoroutineScope`
- NEVER use a bare `Job()` for a scope that must survive individual failures (fire-and-forget / process-lifetime scopes): with a plain `Job`, one unhandled exception cancels the whole scope and its siblings — `SupervisorJob` is the only correct cancellation root here (the `fireAndForget` helpers provide it; if assembling a context by hand, use `SupervisorJob()` + `CoroutineName` for traceability)
- When the scope's context already carries a parent `Job` that must stay the cancellation root (e.g. a lifecycle-scoped child), use the `CoroutineContext` overload — it keeps that Job as the cancellation root and only adds a `SupervisorJob` when the context carries no Job
- NEVER cancel process-lifetime scopes from component lifecycle code (`onDestroy()` of an Activity/Service) — same reason as the executor rule above
- `NzikDispatchers` is a process-lifetime singleton with daemon threads — NEVER `shutdown()` its executors or close them from an `onDestroy()`/service scope: that kills the pools for the rest of the process
- Use `withContext(NzikDispatchers.X)` to move work between named dispatchers; cancel regular coroutines in `onCleared()` / `DisposableEffect`
- Flow collection off the main thread: `collectAsStateWithLifecycle(..., context = NzikDispatchers.DATA)` is the established pattern — use it, do not collect on `UI`
- Unit tests may still use `Dispatchers.setMain()`/`Dispatchers.resetMain()`; the established test style is `runBlocking` (see Testing)

## UI — Jetpack Compose + Material 3

- All UI in Jetpack Compose (no XML layouts)
- Use the existing theming system — dynamic palette via legacy `app.it.fast4x.rimusic.ui.styling.ColorPalette` (`dynamicColorPaletteOf`, READ-ONLY); palette animation in `app.n_zik.android.components.theme.AnimatedAppearance`; legacy `ColorPaletteMode`/`ColorPaletteName` enums in `app.it.fast4x.rimusic.enums/`; typography via legacy `app.it.fast4x.rimusic.ui.styling.Typography` (read-only). Do NOT invent new CompositionLocals
- Animations under 300ms for snappy feel
- Use `Modifier` for styling, chain for multiple effects
- Keep composables small (single responsibility)

### BottomSheet Animation

When dismissing a `CustomModalBottomSheet` manually, orchestrate hide animation BEFORE changing state:

```kotlin
// CORRECT
val scope = rememberCoroutineScope()
scope.launch {
    if (sheetState.isVisible) sheetState.hide()
    showSheet = false // Only AFTER animation
}

// INCORRECT — causes sudden disappearance
onDismiss = { showSheet = false }
```

## Accessibility

- All images/icons must have `contentDescription`
- Use semantic properties in Compose
- Maintain WCAG AA contrast ratios (4.5:1 text, 3:1 large text)
- Minimum touch target: 48dp
- Test with TalkBack when possible
- If accessibility violation detected → HALT, fix before continuing

## Database

NEVER edit schema without explicit instruction. Never add, remove, or rename columns, tables, or constraints.

### Room Patterns

- Table naming: **singular** (`Song`, `Playlist`) — all 13 existing tables are singular
- DAO suffix: **`*Table`** (`SongTable`, `PlaylistTable`) — 13 DAO interfaces, none named `*Dao`
- DAOs carry `@RewriteQueriesToDropUnusedColumns` (12 of 13 existing — `ImportSongTable` is the exception) — new DAOs must carry it
- Use `@Insert(onConflict = OnConflictStrategy.IGNORE)` for insert-or-ignore
- Use `@Upsert` for insert-or-update
- Use `@Query` with `Flow<T>` for reactive queries
- Use `@Transaction` for multi-step operations
- All DAO methods `suspend` (except Flow-returning queries)
- Migration testing required before reporting — schema JSON exports live in `ComposeN-Zik/src/test/resources/schemas/app.n_zik.android.core.database.DatabaseInitializer/` (used by the `From*To*MigrationTest` suite)

### Migration Safety

- Always backup test database before migration testing
- Test migration with realistic data volumes
- If migration fails → HALT, do NOT commit, report to user
- Never modify an already-deployed migration — create a new one

## KMP (Kotlin Multiplatform)

- `commonMain` for shared logic
- `androidMain` for Android-specific code
- Use `expect/actual` declarations in correct source sets
- Never add Android-specific imports in commonMain
- Platform-specific features go in their respective source sets

## Network Resilience

- Handle `UnknownHostException` (network down) — the exception actually handled across the new code
- Implement retry with exponential backoff for transient failures
- Cache responses where appropriate
- Show user-friendly error for offline state

## Testing Conventions

- Test method names are backtick descriptive sentences (`` fun `shuffle should return list of same size`() ``) — match this style
- **Off-main tests** (name suffix `*OffMainTest`, 28 existing): assert that offloaded work actually lands on a named `NzikDispatchers` thread (thread-name prefix assertion, e.g. `nzik-media-`). Any new work moved off the main thread gets one — this is the verification pillar of issue #606
- Tests of legacy classes go under `app.n_zik.android.legacyoffmain.<mirror>` — never under the legacy namespace (see rules/BUILD.md, incl. the 3 grandfathered exceptions)

## Compose UI Testing

- Use `createComposeRule()` for Compose tests
- Test state changes with `onNodeWithTag` / `onNodeWithText`
- Use `SemanticsMatcher` for accessibility checks
- Test theme/color changes with `CompositionLocalProvider`

```kotlin
import androidx.compose.ui.test.junit4.createComposeRule

class LyricsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lyricsDisplay() {
        composeRule.setContent { LyricsScreen(lyrics = testLyrics) }
        composeRule.onNodeWithText("Verse 1").assertIsDisplayed()
    }
}
```

## Navigation

- Routes are a legacy `enum class NavRoutes` (`app/it/fast4x/rimusic/enums/NavRoutes.kt`, READ-ONLY) plus string route helpers (e.g. `rewindDeckRoute(year, month)` in `MainActivity`) — no sealed route class exists; do NOT invent one without explicit instruction
- `core/navigation/` holds interceptors (e.g. `MiniPlayerQueueInterceptor`), NOT route definitions
- No deep links without validation

## Translations (Crowdin)

- Source strings live ONLY in `values/strings.xml` — this is the single source of truth for translators
- NEVER hand-edit any `values-*/strings.xml` file — these are managed exclusively by the Crowdin sync (automated PR/commit); manual edits get overwritten and cause merge conflicts with translator work
- If a Crowdin sync commit/PR appears (bot-authored, touches only `values-*/strings.xml`), it is exempt from the full BMAD workflow and from code review — merge as-is after a diff sanity check (human commit approval from AGENTS.md still applies: show the diff, ask before merging/committing)
- Adding a NEW string key: add it to `values/strings.xml` only; Crowdin will propagate it to other locales automatically
- Removing or renaming a string key: check for usages across the codebase first (a stale key breaks translator context, not just compilation)
- Never assume a `values-*` string is wrong because it "reads oddly" in English — flag it to the user/translation team instead of editing it directly

## Dependency Injection

- Follow existing DI patterns in the codebase
- Prefer constructor injection over service locator
