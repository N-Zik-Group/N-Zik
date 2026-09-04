# Code Quality Rules

**Version:** 1.2.0 | **Last updated:** 2026-08-24

## Naming Conventions

- **Classes/PascalCase**: `MusicDatabase`, `PlayerService`, `LyricsScreen`
- **Functions/camelCase**: `getSongById`, `updatePlaylist`, `handlePlaybackError`
- **Constants/UPPER_SNAKE_CASE**: `LOCAL_KEY_PREFIX`, `MAX_RETRY_COUNT`
- **Variables/camelCase**: `songList`, `isPlaying`, `currentPosition`
- **Packages/lowercase**: `app.n_zik.android.playback`, `database.entities`

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
- NEVER use `runBlocking` in suspend code
- NEVER use `collectAsState()` — use `collectAsStateWithLifecycle()`
- NEVER use `_state.value = ...` — use `_state.update { it.copy(...) }`
- Use `StateFlow` over `LiveData` — expose single sealed `UiState` class
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
| Domain-specific dialogs        | `components.{domain}/`                             |
| Domain menus                   | `components/menu.{domain}/`                        |
| Page-level screens             | `components.ui.screens.{screen}/`                  |
| ViewModels                     | co-located with their screen, in `components.ui.screens.{screen}/` |
| Repositories                   | `core/data/` (one repository per domain, e.g. `SongRepository`) |
| Player UI + lyrics             | `components/player/` + `components/player/lyrics/` |
| Settings components            | `components/settings/`                             |
| Enums                          | `enums/`                                           |
| Extensions (optional features) | `extensions.{feature}/`                            |
| Database tables & migrations   | `core/database/`                                   |
| Network layer                  | `core/network/`                                    |
| Services (player, download)    | `playback/services/`, `download/services/`         |
| Dependency injection modules   | `core/di/`                                         |
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

- Use `Dispatchers.IO` for network/disk, `Dispatchers.Default` for CPU, `Dispatchers.Main` for UI
- Use `withContext` to switch dispatchers
- Cancel coroutines in `onCleared()` or `DisposableEffect`
- Avoid holding Activity/Context references in long-lived objects
- Use Coil for image loading
- Profile startup and rendering performance
- Avoid ANR: never block main thread for >5 seconds

## UI — Jetpack Compose + Material 3

- All UI in Jetpack Compose (no XML layouts)
- Use existing theming system (`LocalColorPalette.current`, `LocalTypography.current`)
- Animations under 300ms for snappy feel
- Use `Modifier` for styling, chain for multiple effects
- Keep composables small (single responsibility)

### BottomSheet Animation

When dismissing a `CustomModalBottomSheet` manually, orchestrate hide animation BEFORE changing state:

```kotlin
// CORRECT
coroutineScope.launch {
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

- Entity naming: plural table names (`songs`, `playlists`)
- DAO suffix: `SongDao`, `PlaylistDao`
- Use `@Insert(onConflict = OnConflictStrategy.IGNORE)` for insert-or-ignore
- Use `@Upsert` for insert-or-update
- Use `@Query` with `Flow<T>` for reactive queries
- Use `@Transaction` for multi-step operations
- All DAO methods `suspend` (except Flow-returning queries)
- Migration testing required before reporting

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

- Handle `UnknownHostException` and `SocketTimeoutException`
- Implement retry with exponential backoff for transient failures
- Cache responses where appropriate
- Show user-friendly error for offline state

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

- Use sealed class for routes
- No deep links without validation

## Translations (Crowdin)

- Source strings live ONLY in `values/strings.xml` — this is the single source of truth for translators
- NEVER hand-edit any `values-*/strings.xml` file — these are managed exclusively by the Crowdin sync (automated PR/commit); manual edits get overwritten and cause merge conflicts with translator work
- If a Crowdin sync commit/PR appears (bot-authored, touches only `values-*/strings.xml`), it is exempt from the full BMAD workflow and from code review — merge as-is after a diff sanity check
- Adding a NEW string key: add it to `values/strings.xml` only; Crowdin will propagate it to other locales automatically
- Removing or renaming a string key: check for usages across the codebase first (a stale key breaks translator context, not just compilation)
- Never assume a `values-*` string is wrong because it "reads oddly" in English — flag it to the user/translation team instead of editing it directly

## Dependency Injection

- Follow existing DI patterns in the codebase
- Prefer constructor injection over service locator
