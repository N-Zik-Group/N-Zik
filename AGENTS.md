# AGENTS.md — NZik

**MANDATORY: Read this file + rules/*.md before any task.**

## Session Startup

1. Read this file entirely
2. Read ALL `rules/*.md` files
3. Match the user's language and announce the critical rules (see rules/WORKFLOW.md "Session Startup Sequence" + "Announcement Template")
4. Ask user via question tool: "Bug, feature, or something else?"
5. If bug or feature → ASK which IDE/tool (ONE at a time) AND which skill to use before loading anything (see rules/WORKFLOW.md Step 3a)

---

## ✅ Always Do

- Code → `app.n_zik.android.*` ONLY (legacy packages are READ-ONLY)
- Use Timber with tags (no println/Log.d)
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

- Create files under `app.it.fast4x.rimusic.*` or `app.kreate.android.*` (tests covering legacy classes are the single exception: they go under `app.n_zik.android.legacyoffmain.*` — see rules/BUILD.md)
- Edit `values-*/strings.xml` (only `values/strings.xml`)
- Write code before completing full BMAD workflow
- Skip BMAD workflow steps
- Skip the Step 8b code-review gate, or edit `fastlane/`/`Updater/`/`Done.txt` before the user chose a commit mode (Step 8d) — exception: doc-only edits (rules/WORKFLOW.md "Doc-Only Exception") follow their own commit-approval flow and are NOT subject to the Step 8d mode question
- Commit without human approval
- Use `GlobalScope`, `runBlocking`, `collectAsState()` (use `collectAsStateWithLifecycle()`)
- Use `!!` operator unless justified with comment explaining why
- Edit `_bmad/` internals manually
- Force push or delete committed history

---

## Skill Discovery

**`{project-root}`** = the directory containing `_bmad/` and `.agents/` folders. This is the **workspace root** (`N-Zik-Projet/`), NOT the `N-Zik/` subdirectory where this AGENTS.md lives. Go **up one level** from `N-Zik/` to find it.

> **OpenCode path resolution:** Scripts are at `{project-root}/_bmad/scripts/`. If you're running from `N-Zik/`, use `../_bmad/scripts/` or resolve to workspace root first.

**Skills location** (depends on your IDE):

> This table lists only the 3 preferred tools plus common alternatives. It is NOT the authoritative full list — if the user's IDE isn't shown here, **BMAD-TOOLS.md is the source of truth** for all ~40 supported tools and their skills/global/commands directories. Never assume a tool is unsupported just because it's absent from this shorter table.

| IDE                  | Skills Path                                           | How to Load                     |
| -------------------- | ----------------------------------------------------- | ------------------------------- |
| OpenCode ⭐           | `{project-root}/.agents/skills/{skill-name}/SKILL.md` | `@skills/{skill-name}`          |
| GitHub Copilot ⭐     | `{project-root}/.agents/skills/{skill-name}/SKILL.md` | `LOAD the FULL {path}/SKILL.md` |
| Google Antigravity ⭐ | `{project-root}/.agent/skills/{skill-name}/SKILL.md`  | Direct read                     |
| Claude Code          | `{project-root}/.claude/skills/{skill-name}/SKILL.md` | Direct read                     |
| Cursor               | `{project-root}/.agents/skills/{skill-name}/SKILL.md` | Direct read                     |
| Codex                | `{project-root}/.agents/skills/{skill-name}/SKILL.md` | Direct read                     |
| Other tools (~40 total) | See `rules/BMAD-TOOLS.md`                          | See `rules/BMAD-TOOLS.md`       |

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
│   ├── androidMain/kotlin/app/n_zik/android/  ★ NEW code
│   └── test/                                   Tests
├── extensions/              API modules (innertube, lrclib)
├── modules/                 Feature submodules
├── composeApp/              DEAD folder — NOT a gradle module (excluded from settings.gradle.kts); orphaned sources + stale build artifacts → ignore, never build/modify
├── gradle/libs.versions.toml  Version catalog
└── (docs/ lives at the WORKSPACE ROOT, one level above N-Zik/ — Reference, READ-ONLY)
```

| What         | Where                                      |
| ------------ | ------------------------------------------ |
| Main code    | `app/n_zik/android/`                       |
| Database     | `app/n_zik/android/core/database/`         |
| Repositories | `app/n_zik/android/core/data/`             |
| DI modules   | `app/n_zik/android/core/di/`               |
| Navigation   | `app/n_zik/android/core/navigation/`       |
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

> **Windows:** run `gradlew.bat …` from the repo root `N-Zik/` (e.g. `gradlew.bat :ComposeN-Zik:assembleDebug`). The workspace root (`N-Zik-Projet/`, where `_bmad/` lives) is **not** a git/gradle project — all `git` and `gradlew` commands run from `N-Zik/`.

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
| `rules/CODE.md`       | Code quality, Kotlin/Compose patterns, file placement |
| `rules/SECURITY.md`   | Secrets, input validation, license checks             |
| `rules/RECOVERY.md`   | Build failures, skill failures, rollback              |
| `rules/BUILD.md`      | Gradle commands, commit convention, testing           |
| `rules/WORKFLOW.md`   | BMAD workflow step-by-step enforcement                |
| `rules/BMAD.md`       | BMAD config, skill customization, scripts             |
| `rules/BMAD-TOOLS.md` | IDE skill directories reference                       |
