# AGENTS.md — NZik

**Version:** 1.5.0 | **Last updated:** 2026-09-26

**MANDATORY: Read this file + rules/*.md before any task.**

## Session Startup

1. Read this file entirely
2. Read ALL `rules/*.md` files
3. Match the user's language and announce the critical rules (see rules/WORKFLOW.md "Session Startup Sequence" + "Announcement Template")
4. Ask user via question tool: "Bug, feature, or something else?"
5. If bug or feature → ASK which IDE/tool (ONE at a time) AND which skill to use before loading anything (see rules/WORKFLOW.md Step 3a)

---

## ✅ Always Do

- Code → `app.n_zik.android.*` ONLY — legacy packages (`app.it.fast4x.rimusic.*` / `app.kreate.android.*`) are READ-ONLY: no new files there; MODIFYING an existing legacy file is allowed ONLY when a bug fix cannot be expressed outside it AND ONLY after explicit user approval — prefer implementing the fix in `app.n_zik.android.*` (wrapper/overlay)
- Use Timber with tags (no println/Log.d)
- Dispatch coroutines on `NzikDispatchers` named dispatchers only (`UI`, `PLAYBACK`, `VISUALIZER`, `MEDIA`, `DATA` + Room executors); fire-and-forget scopes via `NzikDispatchers.fireAndForget()` (see rules/CODE.md "Coroutines & Dispatchers")
- Use version catalog refs (`libs.versions.toml`)
- Verify build passes after changes (`./gradlew :ComposeN-Zik:assembleDebug`)
- New features/bug fixes include at least one test
- Show evidence (diffs, test output) — never just claim "done"
- Match user's language for communication
- Code comments & commits in English

## ⚠️ Ask First

- Database schema changes (NEVER edit without explicit instruction)
- Adding new dependencies not in `libs.versions.toml`
- Committing code (NEVER without human testing + approval)
- Commit mode at end of workflow: (1) Bump+commit = bump version + write fastlane/Updater changelogs + empty Done.txt, (2) Done+commit = Done.txt only, (3) Wait = nothing — ASK before editing ANY file (see rules/WORKFLOW.md Step 8d)
- Which IDE/tool to use (ask ONE at a time — see BMAD-TOOLS.md for preferred list)

## 🚫 Never Do

- Create files under `app.it.fast4x.rimusic.*` or `app.kreate.android.*` (legacy tests go under `app.n_zik.android.legacyoffmain.*` — see rules/BUILD.md: 3 pre-existing test files live under the legacy namespace and are grandfathered, do NOT move them)
- Edit any `values-*/strings.xml` (the only source of truth is `ComposeN-Zik/src/androidMain/res/values/strings.xml`)
- Write code before completing full BMAD workflow
- Skip BMAD workflow steps
- Skip the Step 8b code-review gate, or edit `fastlane/`/`Updater/`/`Done.txt` before the user chose a commit mode (Step 8d) — exception: doc-only edits (rules/WORKFLOW.md "Doc-Only Exception") follow their own commit-approval flow and are NOT subject to the Step 8d mode question
- Commit without human approval
- Use `GlobalScope`, `collectAsState()` (use `collectAsStateWithLifecycle()`) — and `runBlocking` WITHOUT a justification comment: a `runBlocking` is allowed when a synchronous API forces it (existing example: ExoPlayer sync APIs in `StreamResolver`), but every usage — new or pre-existing — must carry a comment explaining why
- Introduce new raw `Dispatchers.IO`/`Dispatchers.Default`/`Dispatchers.Main` usages in app code (use `NzikDispatchers`), use a bare `Job()` for fire-and-forget/process-lifetime scopes (use `SupervisorJob` via `NzikDispatchers.fireAndForget()`), or `shutdown()`/close `NzikDispatchers` executors (process-lifetime, daemon threads — see rules/CODE.md "Coroutines & Dispatchers")
- Use `!!` operator unless justified with comment explaining why
- Edit `_bmad/` internals manually
- Force push or delete committed history

---

## Skill Discovery

**`{project-root}`** = the directory containing both `_bmad/` and `.agents/` folders (if only one exists, prefer `_bmad/`). This is the **workspace root** — the directory containing `_bmad/` — NOT the `N-Zik/` subdirectory where this AGENTS.md lives. Go **up one level** from `N-Zik/` to find it.

> **OpenCode path resolution:** Scripts are at `{project-root}/_bmad/scripts/`. If you're running from `N-Zik/`, use `../_bmad/scripts/` or resolve to workspace root first.

**Skills location** (depends on your IDE):

> This table lists only the 3 preferred tools plus common alternatives. It is NOT the authoritative full list — if the user's IDE isn't shown here, **BMAD-TOOLS.md is the source of truth** for all 45 supported tools and their skills/global/commands directories. Never assume a tool is unsupported just because it's absent from this shorter table.

| IDE                  | Skills Path                                           | How to Load                     |
| -------------------- | ----------------------------------------------------- | ------------------------------- |
| OpenCode ⭐           | `{project-root}/.agents/skills/{skill-name}/SKILL.md` | `@skills/{skill-name}`          |
| GitHub Copilot ⭐     | `{project-root}/.agents/skills/{skill-name}/SKILL.md` | `LOAD the FULL {path}/SKILL.md` |
| Google Antigravity ⭐ | `{project-root}/.agent/skills/{skill-name}/SKILL.md`  | Direct read                     |
| Claude Code          | `{project-root}/.claude/skills/{skill-name}/SKILL.md` | Direct read                     |
| Cursor               | `{project-root}/.agents/skills/{skill-name}/SKILL.md` | Direct read                     |
| Codex                | `{project-root}/.agents/skills/{skill-name}/SKILL.md` | Direct read                     |
| Other tools (42 total) | See `rules/BMAD-TOOLS.md`                          | See `rules/BMAD-TOOLS.md`       |

**When to use which skill:**

| Situation        | Skill                      | Then                 |
| ---------------- | -------------------------- | -------------------- |
| Bug fix          | `bmad-cis-problem-solving` | → `bmad-code-review` |
| New feature      | `bmad-build`               | → `bmad-code-review` |
| Architecture     | `bmad-architecture`        |                      |
| PRD/Requirements | `bmad-prd`                 |                      |
| UX Design        | `bmad-ux`                  |                      |
| Code Review      | `bmad-code-review`         |                      |
| Sprint Planning  | `bmad-sprint-planning`     |                      |

---

## Project Structure

```
N-Zik/                     ← git repo root (run gradlew/git from here)
├── ComposeN-Zik/src/
│   ├── androidMain/           AndroidManifest.xml + res (values, values-*, drawable, mipmap, font, raw, xml…) + kotlin/ (app/n_zik/android/ ★ NEW code + legacy `app.it.*`/`app.kreate.*` read-only)
│   ├── commonMain/            KMP shared logic (effectively one file — `app/it/fast4x/rimusic/Utils.kt`)
│   ├── main/                  res (drawables, mipmap) + proto/listentogether.proto (protobuf source set for Listen Together)
│   ├── test/                  Tests
│   └── debug/ + dev/ + dev32/ + foss/   Per-buildType source sets (`debug` holds its own AndroidManifest.xml; the others are currently empty — see rules/BUILD.md)
├── extensions/              API Gradle modules (innertube → module `:oldtube`, kugou, lrclib, musicbrainz, invidious, ktor-client-brotli, lastfm — module names in `settings.gradle.kts`); `piped/` removed entirely (v75)
├── modules/                 Feature submodules — `betterlyrics`, `discordrpc`, `nextvisualizer` are **git submodules**: after a fresh clone run `git submodule update --init --recursive` or Gradle sync fails
├── gradle/libs.versions.toml  Version catalog
├── assets/notes/              Done.txt + Changelog_Template.txt + TODO.txt (repo-root working files — NOT an Android assets source set)
├── fastlane/                  Store metadata (~25 locales: short/full descriptions, en-US title + tvBanner) + released changelogs by versionCode in `fastlane/metadata/en-US/changelogs/`
├── Updater/                   Released changelogs by versionCode in `Updater/changelogs/` (auto-updater)
└── (WORKSPACE ROOT, one level above N-Zik/: `docs/` reference projects, Reference READ-ONLY · `N-Zik-Website/` separate project — NOT part of this repo · `db/` log captures + `*.sqlite` DB dumps at the root — artifacts referenced by Done.txt)
```

| What         | Where                                      |
| ------------ | ------------------------------------------ |
| Main code    | `app/n_zik/android/` (new) + legacy `app/it/fast4x/rimusic/` & `app/kreate/android/` (≈ same file count — READ-ONLY, see rules/CODE.md) |
| Database     | `app/n_zik/android/core/database/`         |
| Repositories | collocated with their domain (e.g. `recognition/ShazamRepository.kt`) — no central `core/data/` exists |
| DI           | no DI framework in the app module (Hilt and Koin declared in the catalog but never applied) — plain constructor injection + `object Dependencies` service locator in `MainApplication.kt` (process-lifetime `Application` access); introducing a DI framework requires user approval |
| Navigation   | `app/n_zik/android/core/navigation/` — interceptors ONLY; routes are the legacy `NavRoutes` enum + string helpers (see rules/CODE.md Navigation) |
| Player       | `app/n_zik/android/playback/services/`     |
| UI           | `app/n_zik/android/components/ui/screens/` |
| Tests        | `ComposeN-Zik/src/test/`                   |

---

## Build Commands

```bash
./gradlew :ComposeN-Zik:assembleDebug              # Debug build
./gradlew :ComposeN-Zik:test                       # All tests
./gradlew :ComposeN-Zik:testDebugUnitTest --tests "app.n_zik.android.SomeTest"  # Single test
```

> **Build types:** 10 types (`debug`, `full`, `minified`, `full32`, `minified32`, `beta`, `beta32`, `foss`, `dev`, `dev32`) — `release` is disabled, so `assembleRelease` does NOT exist. Variant differences: `*32` builds without FFmpeg, `foss` gets a `.foss` applicationId + no auto-update, `dev` builds get a dated version-name suffix — see rules/BUILD.md "Build Types".

> **Windows:** run `gradlew.bat …` from the repo root `N-Zik/` (e.g. `gradlew.bat :ComposeN-Zik:assembleDebug`). The workspace root (the parent of `N-Zik/`, where `_bmad/` lives) is **not** a git/gradle project — all `git` and `gradlew` commands run from `N-Zik/`.

HALT after 3 failed build attempts → report with full error log.

---

## BMAD Workflow

- **AGENTS.md wins:** code quality, security, commits, logging, database, build
- **BMAD wins:** workflow ordering, templates, checkpoints
- **Conflict:** AGENTS.md wins

→ See `rules/WORKFLOW.md` for full workflow enforcement.
→ See `rules/BMAD.md` for installation, config resolution.

---

## Rules Files

| File                  | Purpose                                               |
| --------------------- | ----------------------------------------------------- |
| `rules/CODE.md`       | Code quality, Kotlin/Compose patterns, dispatchers, file placement |
| `rules/SECURITY.md`   | Secrets, input validation, license checks             |
| `rules/RECOVERY.md`   | Build failures, skill failures, rollback              |
| `rules/BUILD.md`      | Gradle commands, commit convention, testing           |
| `rules/WORKFLOW.md`   | BMAD workflow step-by-step enforcement                |
| `rules/BMAD.md`       | BMAD config, skill customization, scripts             |
| `rules/BMAD-TOOLS.md` | IDE skill directories reference                       |
