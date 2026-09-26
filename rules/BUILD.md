# Build & Test Rules

**Version:** 1.5.0 | **Last updated:** 2026-09-26

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
./gradlew :ComposeN-Zik:assembleBeta           # Beta build
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

## Build Types

| Type    | Command         | Notes                         |
| ------- | --------------- | ----------------------------- |
| `debug` | `assembleDebug` | Primary development build     |
| `foss`  | `assembleFoss`  | Full build without auto-updater (suitable for alternative stores) |
| `beta`  | `assembleBeta`  | Beta build (unsigned locally, signed in CI) |

Other build types (see `ComposeN-Zik/build.gradle.kts`): `full`, `minified` (R8 minify + shrinkResources), `full32`, `minified32`, `beta32`, `dev`, `dev32`. The `release` build type is explicitly **disabled** (`assembleRelease` does not exist). Dedicated per-buildType source sets exist under `ComposeN-Zik/src/`: `src/debug/` holds its own `AndroidManifest.xml` (Compose test-harness activity — see Done.txt), while `src/dev/`, `src/dev32/`, `src/foss/` exist but are currently empty. A custom `assembleFossRelease` task exists as an alias of `assembleFoss`.

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

Test files: `ComposeN-Zik/src/test/kotlin/` — mirror source package structure, EXCEPT tests of legacy code (`app.it.fast4x.rimusic.*` / `app.kreate.android.*`): those MUST live under `app.n_zik.android.legacyoffmain.<mirror>` (existing convention — NEVER under the legacy namespace itself, per AGENTS.md and the Step 6 guard check). Grandfathering: 3 pre-existing test files live under the legacy namespace (`app/it/fast4x/rimusic/models/PlaylistTest.kt`, `app/it/fast4x/rimusic/utils/InvincibleServiceTest.kt`, `app/it/fast4x/rimusic/utils/LandscapeBarsTest.kt`) — do NOT move, rewrite or modify them; every NEW legacy test goes under `legacyoffmain`. If one of the 3 fails or no longer compiles → HALT and ask the user for an explicit decision (the Step 6 guard check flags any legacy path in the diff — quote the approval in the report). Also grandfathered: the top-level test packages `test/kotlin/utils/` and `test/kotlin/painters/` predate the mirroring rule — do NOT move them; new tests follow the mirroring rule.

New features/bug fixes should include at least one test. If no test framework is available → HALT and note it.

## CI Expectations

- No pre-commit hooks are configured in this repo — do not wait for hook signals; run build with the commands in this file
- **No CI workflow runs the unit test suite** — tests are local-only; always run them yourself before reporting (the only workflow that would run tests, `code-coverage.yml`, is disabled via its `.disabled` extension — if it is ever re-enabled, this line must be revisited)
- CI signs the unsigned APKs in GitHub Actions via `secrets.RELEASE_KEYSTORE*` (beta manual, weekly all-flavors, dev nightly + manual, test manual full-only — an author gate for the maintainer `NEVARLeVrai` exists in `build-dev.yml` but no `push` trigger is configured, so it is currently unreachable)
- If CI pipeline fails after push → HALT, investigate, fix

## Code Formatting

- ktlint/detekt are **NOT configured** in this project — no lint task exists; do not search for one
- Use Android Studio auto-format for consistent style
- Follow existing file formatting patterns
- No trailing whitespace, newline at end of file
