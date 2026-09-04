## 📋 Description
<!-- Describe your changes in detail. What is the problem? How does this PR solve it? -->
<!-- Include context, decisions, and any alternative approaches you considered. -->

## 🔗 Related Issues
<!-- Link to the issue here. Use "issue https://..." format as per BUILD.md rules to prevent auto-closing if necessary, or follow project conventions. -->
- Issue: 

## 🚀 Type of Change
<!-- Check all that apply -->
- [ ] 🐛 Bug fix (`fix`)
- [ ] ✨ New feature (`feat`)
- [ ] 🎨 UI/Design update
- [ ] ⚡ Performance improvement (`perf`)
- [ ] 🧹 Code refactoring (`refactor`)
- [ ] 🤖 Automated/Agentic update (BMAD workflow)
- [ ] 📖 Documentation update (`docs`)
- [ ] 🛠️ Tooling/Build/Chore (`chore`)

## 📸 Screenshots / Video
<!-- Include before/after screenshots or a short video (MP4/GIF) to demonstrate visual or flow changes. -->
| Before | After |
| ------ | ----- |
| <!-- [Image] --> | <!-- [Image] --> |

## 🧪 Verification Plan
<!-- How should a reviewer or QA verify these changes? Please list steps. -->
1. 
2. 
3. 

---

## 🛠️ The Ultimate Project Checklist 
> **MANDATORY**: You must check all applicable boxes. Leave unchecked if NOT applicable, but do not delete them.

### 🤖 1. Git, Workflow & Commits (`WORKFLOW.md`, `BUILD.md` & `AGENTS.md`)
- [ ] **Workflow Complete**: I have completed all 8 steps of the BMAD workflow (including `bmad-code-review`), OR this PR qualifies for the doc-only exception (see below).
- [ ] **Doc-Only Exception**: If used, confirm this PR touches ONLY prose/comments/changelogs — zero `.kt`/`.xml`/`.toml`/`.gradle.kts`/schema changes.
- [ ] **Human Approval**: Human testing and explicit approval was obtained before committing.
- [ ] **Evidence**: Concrete evidence provided (diffs, test output, screenshots), not just claiming it's "done".
- [ ] **No `_bmad/` Edits**: I did NOT manually edit any `_bmad/` internals.
- [ ] **Git History**: I did NOT force push or delete any committed history.
- [ ] **Branch Naming**: Branch is named correctly (`feat/`, `fix/`, or `chore/`).
- [ ] **Commit Format**: Commits strictly follow the `type(scope): description` convention in English, NO period at the end.
- [ ] **Changelogs**: `assets/notes/Done.txt` updated (format: `type(scope): message (issue)`).
- [ ] **Release Notes**: `fastlane/.../changelogs/{version}.txt` and `Updater/changelogs/{version}.txt` updated.
- [ ] **Pre-commit**: Any CI/pre-commit hooks passed successfully before pushing.

### 🏗️ 2. Architecture & File Placement (`CODE.md` & `AGENTS.md`)
- [ ] **Location**: New code is strictly in `app.n_zik.android.*` (or `ComposeN-Zik/src/test/`).
- [ ] **Legacy Rules**: NO files created/edited under `app.it.fast4x.rimusic.*` or `app.kreate.android.*`.
- [ ] **String Resources**: ONLY `values/strings.xml` was edited (NEVER `values-*/strings.xml` — those are Crowdin-managed, see below).
- [ ] **ViewModels/Repositories**: ViewModels co-located with their screen; Repositories in `core/data/`.
- [ ] **DI & Navigation**: DI modules in `core/di/`; new routes use sealed classes in `core/navigation/`.
- [ ] **KMP Modularity**: No Android-specific imports in `commonMain`. `expect/actual` used correctly.
- [ ] **Navigation**: Sealed classes are used for routes.

### 🌍 2b. Translations (`CODE.md`)
- [ ] **Source of Truth**: New/changed strings added ONLY to `values/strings.xml`, never to a `values-*/strings.xml` locale file.
- [ ] **Crowdin Sync**: If this PR is a bot-authored Crowdin sync (touches only `values-*/strings.xml`), it's exempt from BMAD workflow and code review — note this explicitly in the description above.
- [ ] **Key Removal Check**: If a string key was removed/renamed, usages were checked across the codebase first.

### 💻 3. Kotlin & Core Patterns (`CODE.md`)
- [ ] **Coroutines**: Used `viewModelScope` / `lifecycleScope` (NO `GlobalScope` or `runBlocking`).
- [ ] **Dispatchers**: Used appropriate dispatchers (`IO` for network/disk, `Default` for CPU, `Main` for UI).
- [ ] **State Updates**: Used atomic updates (`_state.update { ... }`), NOT `_state.value = ...`.
- [ ] **StateFlow**: Prefer `StateFlow` over `LiveData`, exposing a single sealed `UiState` class.
- [ ] **Null-Safety**: NO `!!` operators used (or a clear comment justifies it). Prefer `requireNotNull()`/`checkNotNull()`.
- [ ] **Naming**: PascalCase for classes, camelCase for functions/vars, UPPER_SNAKE_CASE for constants.
- [ ] **TODOs**: Marked specifically as `// TODO(author): description`.

### 🎨 4. Jetpack Compose Quality (`CODE.md`)
- [ ] **Flows**: Used `collectAsStateWithLifecycle()` exclusively (NO `collectAsState()`).
- [ ] **Compose Lists**: `LazyColumn`/`LazyRow` implement both `key` and `contentType`.
- [ ] **Side-Effects**: No IO/DB/network operations run directly in the composition body.
- [ ] **Parameters**: Data params to children are annotated with `@Stable` or `@Immutable`.
- [ ] **Animations**: All UI animations are under 300ms. BottomSheet hide animations complete BEFORE state changes.
- [ ] **Accessibility**: Images/icons have `contentDescription` and touch targets are at least 48dp.
- [ ] **Images**: Used Coil for image loading.

### 📝 5. Code Quality & Error Handling (`CODE.md`)
- [ ] **Timber Only**: Used `Timber` with tags (NO `println`, `Log.d`, `System.out`, or `printStackTrace()`).
- [ ] **Error Catching**: Used `runCatching { ... }` for risky operations. Exceptions are NOT swallowed silently.
- [ ] **Network**: Handled `UnknownHostException` / `SocketTimeoutException` with exponential backoff if applicable.
- [ ] **Documentation**: KDoc is used for public APIs. Internal comments explain *why*, not *what*.
- [ ] **Clean Code**: No dead code, no commented-out code blocks, and no unused imports (ktlint/detekt passed).

### 🗄️ 6. Database & Data (`CODE.md` & `AGENTS.md`)
- [ ] **Schema Integrity**: Database schema was NOT edited (unless explicitly authorized).
- [ ] **Room Patterns**: Tables use plural names (`songs`), DAOs have `Dao` suffix (`SongDao`).
- [ ] **Room Annotations**: DAO methods are `suspend` (except Flows) with `@Insert(onConflict=IGNORE)` or `@Upsert`.
- [ ] **Migration Safety**: If a migration was made, it was tested on realistic data volumes.

### 🔒 7. Security & Dependencies (`SECURITY.md` & `BUILD.md`)
- [ ] **Secrets**: No API keys, tokens, or hardcoded credentials committed (`local.properties` or `BuildConfig` used).
- [ ] **Signing/Keystore**: No `.jks`/`.keystore` file or signing password committed; `signingConfigs {}` untouched unless explicitly instructed.
- [ ] **Sensitive Data**: Used `EncryptedSharedPreferences` for sensitive local storage; HTTPS enforced.
- [ ] **Validation**: User input and URLs are validated/sanitized before display or navigation.
- [ ] **Dependencies**: New dependencies are in `libs.versions.toml` (NO hardcoded versions).
- [ ] **Licenses**: External code respects MIT/Apache licenses (NO proprietary). Source cited in comments.

### 🧪 8. Build & Testing (`BUILD.md`)
- [ ] **Build Success**: Verified that `./gradlew :ComposeN-Zik:assembleDebug` builds successfully.
- [ ] **Unit Tests**: Added at least one test (JUnit 5 + MockK or `createComposeRule()`) for new features/bug fixes.
- [ ] **Tests Pass**: Ran `./gradlew :ComposeN-Zik:test` and all tests pass locally.

### 🗒️ 9. Additional notes
- Notes: 
