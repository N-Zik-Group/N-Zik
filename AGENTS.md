# AGENTS.md — NZik

**Version:** 1.11.0 | **Last updated:** 2026-10-26

**MANDATORY: Read this file + rules/*.md before any task.**

## Session Startup

1. Read this file entirely
2. Read ALL `rules/*.md` files
3. Match the user's language and announce the critical rules (see rules/WORKFLOW.md "Session Startup Sequence" + "Announcement Template")
4. Ask user via question tool: "Bug, feature, or something else?"
5. If bug or feature → ASK which IDE/tool (ONE at a time) AND which skill to use before loading anything (see rules/WORKFLOW.md Step 3a)

---

## ✅ Always Do

- Code → `app.n_zik.android.*` ONLY — legacy packages (`app.it.fast4x.*` — `rimusic.*` + `compose.persist.*` — / `app.kreate.android.*`) are READ-ONLY: no new files there; MODIFYING an existing legacy file is allowed ONLY when a bug fix cannot be expressed outside it AND ONLY after explicit user approval — prefer implementing the fix in `app.n_zik.android.*` (wrapper/overlay)
- Use Timber with tags (no println/Log.d)
- Dispatch coroutines on `NzikDispatchers` named dispatchers only (`UI`, `PLAYBACK`, `VISUALIZER`, `MEDIA`, `DATA` + Room executors); fire-and-forget scopes via `NzikDispatchers.fireAndForget()` (see rules/CODE.md "Coroutines & Dispatchers")
- Use version catalog refs (`libs.versions.toml`)
- Verify build passes after changes (`./gradlew :ComposeN-Zik:assembleDebug`)
- New features/bug fixes include at least one test (minimum bar, not a quota — volume bar + run strategy in rules/BUILD.md "Testing")
- Show evidence (diffs, test output) — never just claim "done"
- Use the question tool's choice mechanism ONLY for a genuine closed set of options; when waiting for a specific value or free-form input, ask an OPEN question — never a fake "répondre / personnalisé" option that just loops the same prompt (see rules/WORKFLOW.md "Question Tool — Choices vs. Open Answers")
- Keep AGENTS.md + `rules/` lean and current as the project evolves — see "Rules Maintenance & Evolution"
- Use sub-agents by default for substantial, separable work; prioritize fresh, focused contexts and reuse only when useful and still light — see "Sub-agents & Context"
- Match user's language for communication
- Code comments & commits in English
- Removing a string key from `values/strings.xml` requires removing it from every `values-*/strings.xml` too (a dead key left in a locale degrades translator context; Crowdin then drops it from its side) - see rules/CODE.md "Translations"

## ⚠️ Ask First

- Database schema changes (NEVER edit without explicit instruction)
- Adding new dependencies not in `libs.versions.toml`
- Committing code (NEVER without human testing + approval)
- Commit mode at end of workflow: (1) Bump+commit = bump version + write fastlane/Updater changelogs + empty Done.txt, (2) Done+commit = Done.txt only, (3) Wait = nothing — ASK before editing ANY file (see rules/WORKFLOW.md Step 8d)
- Which IDE/tool to use (ask ONE at a time — see BMAD-TOOLS.md for preferred list)

## 🚫 Never Do

- Create files under `app.it.fast4x.*` or `app.kreate.android.*` (legacy tests go under `app.n_zik.android.legacyoffmain.*` — see rules/BUILD.md: 3 pre-existing test files live under the legacy namespace and are grandfathered, do NOT move them)
- Edit `values-*/strings.xml` translation values (source of truth for translator content: `ComposeN-Zik/src/androidMain/res/values/strings.xml`) - EXCEPTION: removing a string key from the source also requires removing it from every `values-*/strings.xml` (a dead key in a locale degrades translator context) - see rules/CODE.md "Translations"
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
│   └── debug/                 Per-buildType source set — `debug` holds its own AndroidManifest.xml (the Compose test-harness activity); the other build types have no source set dir (see rules/BUILD.md)
├── extensions/              API Gradle modules (innertube → module `:oldtube`, kugou, lrclib, musicbrainz, invidious, ktor-client-brotli, lastfm — module names in `settings.gradle.kts`); `piped/` removed entirely (v75)
├── modules/                 Feature submodules — `betterlyrics`, `discordrpc`, `nextvisualizer` are **git submodules**: after a fresh clone run `git submodule update --init --recursive` or Gradle sync fails
├── gradle/libs.versions.toml  Version catalog
├── assets/notes/              Done.txt + Changelog_Template.txt + TODO.txt (repo-root working files — NOT an Android assets source set)
├── fastlane/                  Store metadata (31 locales: short/full descriptions, en-US title + tvBanner) + released changelogs by versionCode in `fastlane/metadata/android/en-US/changelogs/`
├── Updater/                   Released changelogs by versionCode in `Updater/changelogs/` (auto-updater)
├── opencode.json + .devin/wiki.json   Tracked agent-tooling config (not app code)
└── (WORKSPACE ROOT, one level above N-Zik/: `docs/` reference projects, Reference READ-ONLY · `N-Zik-Website/` separate project — NOT part of this repo)
```

| What         | Where                                      |
| ------------ | ------------------------------------------ |
| Main code    | `app/n_zik/android/` (new — 555 .kt) + legacy `app/it/fast4x/rimusic/` (315 .kt) + `app/it/fast4x/compose/persist/` (5 .kt) & `app/kreate/android/` (21 .kt) — the legacy tree is being migrated out (READ-ONLY, see rules/CODE.md) |
| Database     | `app/n_zik/android/core/database/`         |
| Repositories | collocated with their domain (e.g. `recognition/ShazamRepository.kt`) — no central `core/data/` exists |
| DI           | no DI framework in the app module (Hilt and Koin declared in the catalog but never applied) — plain constructor injection + `object Dependencies` service locator in `MainApplication.kt` (process-lifetime `Application` access); introducing a DI framework requires user approval |
| Navigation   | `app/n_zik/android/core/navigation/` — interceptors ONLY; routes are the legacy `NavRoutes` enum + string helpers (see rules/CODE.md Navigation) |
| Player       | `app/n_zik/android/playback/services/`     |
| Contract server (wire) | `app/n_zik/android/bridge/` — the phone-side local server of the wire contract with the N-Zik-Desktop-Compagnon app (39 .kt: `BridgeServer` + Core/Controller/Service, `DeviceAuthenticator`, sub-pkgs `audio/`, `command/`, `library/`, `pairing/`, `state/`) — the spec is shared: `{project-root}/_bmad-output/specs/spec-n-zik-pc-bridge/contract/CONTRACT-v1.md` (contract 1.x, current 1.12.0) |
| Listen Together      | `app/n_zik/android/listentogether/` (12 .kt) + `src/main/proto/listentogether.proto` |
| Widgets / shortcuts  | `app/n_zik/android/widget/` (6 .kt), `app/n_zik/android/shortcuts/` |
| UI           | `app/n_zik/android/components/ui/screens/` |
| Tests        | `ComposeN-Zik/src/test/`                   |

---

## Build Commands

```bash
./gradlew :ComposeN-Zik:assembleDebug              # Debug build
./gradlew :ComposeN-Zik:test                       # All tests
./gradlew :ComposeN-Zik:testDebugUnitTest --tests "app.n_zik.android.SomeTest"  # Single test
```

> **Build types:** 10 types (`debug`, `release64`, `release32`, `minified64`, `minified32`, `beta64`, `beta32`, `foss`, `dev64`, `dev32`) — `release` is disabled, so `assembleRelease` does NOT exist. No build bundles FFmpeg; it ships as a downloadable extension module (the `app.n_zik.android.modules/` system, Extensions page in Settings). ABI: `64` types ship only arm64-v8a + x86_64, `32` types only armeabi-v7a + x86, `foss` + `debug` are universal; `foss` also gets a `.foss` applicationId + no auto-update, `dev` builds get a dated version-name suffix — see rules/BUILD.md "Build Types".

> **Windows:** run `gradlew.bat …` from the repo root `N-Zik/` (e.g. `gradlew.bat :ComposeN-Zik:assembleDebug`). The workspace root (the parent of `N-Zik/`, where `_bmad/` lives) is **not** a git/gradle project — all `git` and `gradlew` commands run from `N-Zik/`.

> **Parallel work (worktrees):** when a new task starts while `N-Zik/` holds in-progress (uncommitted) work, do NOT work in the dirty tree — propose a git worktree from remote code per rules/BUILD.md "Git Worktrees — Parallel Work" (setup = the README fresh clone: submodules + `release_notes.txt`). At task end, propose the merge into main; after approval remove the worktree and delete the branch (same rule).

HALT after 3 failed build attempts → report with full error log.

---

## Sub-agents & Context — avoid compaction

Context control takes priority over agent reuse. Delegate substantial, separable work by default; keep the main conversation focused on decisions and integration.

- **Delegate broadly:** exploration, reading, independent investigations, reviews, tests and separable implementation. Parallelize independent tasks within the available agent limit; keep small targeted actions inline when delegation adds no value.
- **One bounded scope:** prefer an agent per task or screen. Reuse it within that scope only while its retained context is directly useful and still light. After a long investigation, repeated follow-ups, a scope change or compaction, start a fresh agent instead. Never reuse an agent merely because it already exists.
- **Compact handoff:** give a fresh agent the repository, objective, constraints, relevant decisions, rule/spec paths, owned files, completed checks and remaining work. Avoid inheriting the full conversation when this brief is enough.
- **Concise returns:** request findings with file/line evidence, decisions, changed files, verification and unresolved issues. Avoid raw file dumps or full investigation transcripts. Preserve useful results before replacing an agent; re-read source only to verify a finding or resolve uncertainty.
- **Main agent ownership:** retain orchestration, user communication, decisions, integration and final verification. Delegated edits need disjoint file ownership; never edit the same files concurrently or run competing builds in one checkout.
- **Same safeguards:** delegation never bypasses repository rules, workflow checkpoints or human commit approval.

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

---

## Rules Maintenance & Evolution

These files (AGENTS.md + `rules/`) are a **living document** — they evolve with the project. Keep them lean and current, not a one-off spec frozen at setup.

- **Match reality.** When the codebase changes, update the rules to describe the state it is in *now*: a screen or feature lands, a tool is added (ktlint, a new build variant), the legacy→`app.n_zik.android.*` migration reaches a milestone, a convention is fixed, a build type changes.
- **Lean & project-specific.** Short, actionable rules over prose. Add a generic explanation ONLY when it is necessary or logically required (e.g. *why* legacy packages stay READ-ONLY, *why* `NzikDispatchers` is the single threading source of truth). Cut "best-practice 101" that adds nothing project-specific.
- **Delete, don't stack.** When a rule is superseded or completed (e.g. a "to be introduced" note once the tool exists; a "known flaky test" note once the test is fixed), rewrite or remove it — never leave stale notes piling up.
- **Bump on change.** Editing any of these files → bump its `Version` and `Last updated` (patch = wording, minor = new rule/section, major = restructure).
- **Doc-only.** Rule edits follow the "Doc-Only Exception" flow (show the diff, ask before commit) — the BMAD code workflow does not apply to rule edits.
- **When to update:** migration state changes · a tool/framework is added or configured · file layout changes · a security/config decision is made · a recurring mistake is found (turn it into a rule).
