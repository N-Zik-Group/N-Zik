# Build & Test Rules

**Version:** 1.9.0 | **Last updated:** 2026-10-26

## Gradle Version Catalog

Use `N-Zik/gradle/libs.versions.toml` references. NEVER hardcode versions.

```kotlin
// GOOD
implementation(libs.timber)
implementation(libs.room)

// BAD
implementation("com.jakewharton.timber:timber:5.0.1")
```

If a needed library isn't in the catalog → HALT and ask user before adding to `libs.versions.toml`.

## Build Commands

```bash
./gradlew :ComposeN-Zik:assembleDebug          # Debug build (primary)
./gradlew :ComposeN-Zik:assembleFoss           # FOSS build
./gradlew :ComposeN-Zik:assembleBeta64         # Beta build (64-bit ABIs only)
./gradlew :ComposeN-Zik:test                   # All tests
./gradlew :ComposeN-Zik:testDebugUnitTest --tests "app.n_zik.android.playback.utils.ShufflerTest"
./gradlew clean :ComposeN-Zik:assembleDebug    # Clean + debug
```

> **Windows:** use `gradlew.bat` instead of `./gradlew` (e.g. `gradlew.bat :ComposeN-Zik:assembleDebug`).
> **CWD:** all `gradlew` and `git` commands run from the repo root `N-Zik/` — the workspace root (the parent of `N-Zik/`) is **not** a git repo (BMAD files live there, outside the repo).

## Verification

ALWAYS verify changes compile before reporting. If build fails:

1. Read error messages
2. Fix the first error (often cascading)
3. Rebuild
4. **HALT after 3 failed attempts** — report to user with full error log (the 3-attempt counter is per autonomous cycle — it resets when the user gives a new explicit direction after a HALT)

```
BUILD FAILURE ESCALATION:
Attempt 1 → Fix obvious issue → Rebuild
Attempt 2 → Research error → Fix → Rebuild
Attempt 3 → HALT → Report to user with error log
```

### Done.txt Format

File: `N-Zik/assets/notes/Done.txt` — repo root (NOT an Android `assets/` source set); same folder holds `Changelog_Template.txt`

When committing, update `Done.txt` using its own template (`Changelog_Template.txt` in same folder):

```
<keyword>(<scope>): <short summary> (issue ref)
  - Technical detail 1
  - Technical detail 2
```

Include full issue link (use `issue https://...` to avoid auto-closing).
Entries are grouped under the section headers defined by the template (`Hotfix:` / `Added:` / `Changed:` / `Improved:` / `Fixed:` / `Refactor:` / `Removed:` / `Deprecated:` / `Other:`) — place each entry under the matching section.
The entry keyword for Done.txt follows the template's sections — it is not automatically a commit type: e.g. `change(...)` is a valid Done.txt entry but NOT a valid commit message type; `improve(...)` is valid in both (see Commit Convention).

## Git Submodules

`modules/betterlyrics`, `modules/discordrpc`, `modules/nextvisualizer` are **git submodules** (see `.gitmodules`) and are included in `settings.gradle.kts`. After a fresh clone, run `git submodule update --init --recursive` BEFORE any Gradle command — sync fails without them (CI always checks out with submodules). A changed submodule pointer in a diff → HALT and ask before touching it.

## Git Worktrees — Parallel Work

When a new task starts while the main checkout holds in-progress work (uncommitted changes), do NOT work in the dirty tree: propose a git worktree via the question tool; on approval, create it and run the whole task there, so the user keeps working in `N-Zik/` in parallel.

- **Base on remote code, never on the local dirty state** (from `N-Zik/`):

  ```bash
  git fetch origin
  git worktree add ../worktrees/n-zik/<branch> -b <branch> origin/main
  ```

  `<branch>` follows "Branching" (`feat/<name>`, `fix/<name>`, `chore/<name>`); `../worktrees/n-zik/` = the `worktrees/` folder at the workspace root (one level above `N-Zik/`, outside the repo — the Desktop Compagnon uses `worktrees/compagnon/`, so the two repos never collide).
- **Set it up exactly like the README's fresh clone** (README "Clone the repo"):
  1. `git submodule update --init --recursive` — a worktree does not auto-init submodules; Gradle sync fails without them
  2. Create `ComposeN-Zik/src/androidMain/res/raw/release_notes.txt` (gitignored — it does not exist in a fresh checkout) with the README's initial content:
     ```
     New:
     - Initial release
     ```
- Run all `gradlew`/`git` commands from the worktree root, exactly as from `N-Zik/`
- Git refuses a branch already checked out elsewhere — worktree branches are always fresh
- Cleanup after the branch is merged/pushed: `git worktree remove ../worktrees/n-zik/<branch>` — ask the user before removing; never `--force` on a worktree with uncommitted work

## Build Types

| Type     | Command          | Notes                                                                    |
| -------- | ---------------- | ------------------------------------------------------------------------ |
| `debug`  | `assembleDebug`  | Primary development build (universal, 4 ABIs)                             |
| `foss`   | `assembleFoss`   | FOSS build without auto-updater (universal, 4 ABIs, suitable for alternative stores) |
| `beta64` | `assembleBeta64` | Beta build, 64-bit ABIs only (unsigned locally, signed in CI)            |

Other build types (see `ComposeN-Zik/build.gradle.kts`): `release64`, `release32`, `minified64` (R8 minify + shrinkResources), `minified32`, `beta32`, `dev64`, `dev32`. The `release` build type is explicitly **disabled** (`assembleRelease` does not exist). **Build-matrix rename (spec-ffmpeg-extension-module):** every ABI-specific build carries an explicit `64`/`32` suffix; `foss` + `debug` are universal and keep their bare names (`foss` keeps its name for the Obtanium `apkFilterRegEx`). **FFmpeg is no longer bundled in any build**; the FFmpegKit Java classes are in every APK but the native `.so`s are stripped from all builds (`packaging.jniLibs.excludes`); FFmpeg ships as a downloadable extension module at runtime (the `app.n_zik.android.modules/` system, Extensions page in Settings). **ABI restriction:** `64` types ship only arm64-v8a + x86_64, `32` types only armeabi-v7a + x86, `foss` + `debug` ship all 4 ABIs. The only dedicated per-buildType source set is `src/debug/`, which holds its own `AndroidManifest.xml` (Compose test-harness activity, see Done.txt). A custom `assembleFossRelease` task exists as an alias of `assembleFoss`. Variant behavioral differences (see `build.gradle.kts` + CI): `dev`/`dev32` get a dated `versionNameSuffix` (CI passes `-PdevDate`); `foss` additionally sets `IS_AUTOUPDATE=false` and a `.foss` applicationId suffix.

## Proguard/R8

- `minified` build uses R8 shrinkResources
- Do NOT add Proguard rules unless explicitly asked
- Test minified build if modifying serialization or reflection-heavy code

## Commit Convention

Format: `type(scope): short description`

| Type       | When to use                                |
| ---------- | ------------------------------------------ |
| `feat`     | New feature or capability                  |
| `fix`      | Bug fix                                    |
| `refactor` | Code restructuring without behavior change |
| `chore`    | Build, CI, dependency, or tooling changes  |
| `docs`     | Documentation changes only                 |
| `test`     | Adding or updating tests                   |
| `perf`     | Performance improvement                    |
| `improve`  | Incremental improvement/refinement of existing behavior (used widely in this repo's history) |

Examples:

```
feat(lyrics): add synced lyrics display
fix(player): handle seek to end of track
refactor(database): extract playlist DAO logic
chore(deps): update room to 2.6.0
```

Rules:

- Under 72 characters
- Imperative mood ("add" not "added")
- Scope optional but recommended
- No period at end
- Include GitHub issue URL when applicable — use `issue https://...` (avoid keywords that auto-close issues like "fixes" or "closes")
- The table is the single source of truth — some historical subjects (e.g. "Agents : Updates rules") predate the convention and are NOT a pattern to follow

## Branching

- Work on `main` unless user specifies a branch
- Branch naming: `feat/<name>`, `fix/<name>`, `chore/<name>`
- If merge conflict → HALT, report to user

## Testing — JUnit 5 (Jupiter) + JUnit 4 (vintage engine, incl. Compose `createComposeRule` tests) + MockK

Both run on the JUnit Platform (`useJUnitPlatform()` + `junit-vintage-engine`). For a new test, mirror the framework of the test files it belongs to (check neighboring imports).

```kotlin
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShufflerTest {
    @Test
    fun `shuffle should return list of same size`() {
        val shuffler = Shuffler()
        val input = listOf(1, 2, 3, 4, 5)
        val result = shuffler.shuffle(input)
        assertEquals(input.size, result.size)
    }
}
```

Test files: `ComposeN-Zik/src/test/kotlin/` — mirror source package structure, EXCEPT tests of legacy code (`app.it.fast4x.*` / `app.kreate.android.*`): those MUST live under `app.n_zik.android.legacyoffmain.<mirror>` (existing convention — NEVER under the legacy namespace itself, per AGENTS.md and the Step 6 guard check). Grandfathering: 3 pre-existing test files live under the legacy namespace (`app/it/fast4x/rimusic/models/PlaylistTest.kt`, `app/it/fast4x/rimusic/utils/InvincibleServiceTest.kt`, `app/it/fast4x/rimusic/utils/LandscapeBarsTest.kt`) — do NOT move, rewrite or modify them; every NEW legacy test goes under `legacyoffmain`. If one of the 3 fails or no longer compiles → HALT and ask the user for an explicit decision (the Step 6 guard check flags any legacy path in the diff — quote the approval in the report). Also grandfathered: the top-level test packages `test/kotlin/utils/` and `test/kotlin/painters/`, and the packages `app/n_zik/android/database/` (source in `core/database/`) and `app/n_zik/android/lyrics/` (no matching main package) — all predate the mirroring rule, do NOT move them; new tests follow the mirroring rule.

New features/bug fixes should include at least one test. If no test framework is available → HALT and note it.

### Test volume — a bar, not a quota

"at least one test per fix" is a **minimum**, not a target: one focused test that would have caught the regression (or proves the fix) is enough. A fix spanning N distinct areas → at most one test file per area. Do not pad for coverage — a green, targeted test beats an exhaustive suite nobody re-runs. The repo's large suite (~3k tests) is a byproduct of agent-driven dev where tests are the per-fix evidence pillar (AGENTS.md "Show evidence") — not a per-fix quota.

### Run strategy — targeted locally, full at the gates

- **Local dev:** targeted run on the touched classes only (`--tests "app.n_zik.android…Test"`). A full suite is NOT required for every change.
- **Full suite** (`:ComposeN-Zik:test`, ~22 min, carries pre-existing env failures — see RECOVERY.md "Known Flaky Tests") → only before a version bump/release, or via CI.

## CI Expectations

- No pre-commit hooks are configured in this repo — do not wait for hook signals; run build with the commands in this file
- **No CI workflow runs the unit test suite** — tests are local-only; always run them yourself before reporting
- CI signs the unsigned APKs in GitHub Actions via `secrets.RELEASE_KEYSTORE*` — the 4 build workflows: `build-beta-flavor.yaml` (beta manual), `build-all-flavors-weekly.yml` (weekly all-flavors), `build-dev.yml` (dev nightly + manual — an author gate for the maintainer `NEVARLeVrai` exists but no `push` trigger is configured, so it is currently unreachable), `build-test.yaml` (test manual full-only)
- `cache-builder.yaml` is another **active** Gradle workflow: it runs builds for cache warming only (`assembleBeta`/`Full`/`Minified --build-cache`) on 3 triggers — `push` (path-filtered to `**/*.gradle*` + wrapper), weekly `schedule` (Friday 16:00) and manual `workflow_dispatch` — it neither signs nor releases
- The remaining 11 **active** workflows in `.github/workflows/` (16 total) are repo-automation + security, NOT Gradle builds: `add-labels.yaml`, `auto-assign-issues.yml`, `close-stale-tickets.yaml`, `comment-on-label.yaml`, `house-keeper.yaml`, `metrics.yml` (auto-commits stats on a 2h cadence + on release), `sync-metroproto.yml` (weekly proto sync from Metrolist into `src/main/proto/listentogether.proto`), `update-android-lockdown-countdown.yml`, `weekly-update-contributors.yaml` (rewrites `res/raw/contributors.json` weekly), `weekly-update-translators.yaml` (rewrites `res/raw/translators.json` weekly — the in-app translator credits list fetched from the Crowdin API) and `codeql.yml` (security scan). Bot commits on `main` from these are expected traffic, not rule violations — do NOT manually edit their targets (`res/raw/contributors.json`, `res/raw/translators.json`) in a feature commit without flagging it
- If CI pipeline fails after push → HALT, investigate, fix

## Code Formatting

- ktlint/detekt are **NOT configured** in this project — no lint task exists; do not search for one
- Use Android Studio auto-format for consistent style
- Follow existing file formatting patterns
- No trailing whitespace, newline at end of file
