## 📋 Description
<!-- What is the problem and how does this PR solve it? Include relevant context, technical decisions and alternative approaches considered. Before submitting, check open issues/PRs for related work (https://github.com/N-Zik-Group/N-Zik/issues/views) — if similar work exists, link it and explain how this PR differs. -->

## 🔗 Related Issues
<!-- Use the `issue https://...` format — never auto-closing keywords like "fixes"/"closes" (see rules/BUILD.md). -->
- Issue:

## 🚀 Type of Change
<!-- Aligned with the commit types in rules/BUILD.md — pick the one(s) this PR maps to -->
- [ ] ✨ New feature (`feat`)
- [ ] 🐛 Bug fix (`fix`)
- [ ] 📈 Improvement of existing behavior (`improve`)
- [ ] ⚡ Performance (`perf`)
- [ ] 🧹 Refactor, no behavior change (`refactor`)
- [ ] 🧪 Tests only (`test`)
- [ ] 📖 Documentation only (`docs`)
- [ ] 🛠️ Build / tooling / dependency (`chore`)

## 📸 Screenshots / Video
<!-- Before/after for visual or flow changes (image, GIF or MP4). Leave empty if not applicable. -->
| Before | After |
| ------ | ----- |
|        |       |

## ✅ Verification & Evidence
<!-- How can a reviewer/QA verify this? List the steps, then provide CONCRETE evidence (diffs, build/test output, screenshots) — never just "done". -->
1.
2.

---

## 🛠️ PR Checklist
> Check every applicable box; leave the rest unchecked (do not delete). Full details live in `AGENTS.md` + `rules/*.md` at the repo root — the boxes below are the hard gates only.

### 🤖 Workflow & Commits (`WORKFLOW.md`, `BUILD.md`)
- [ ] BMAD workflow complete (all 8 steps, including the 8b code-review gate) — **OR** the doc-only exception applies (`.md`/`.txt` prose only; zero `.kt`/`.xml`/`.toml`/`.gradle.kts`/schema changes, and no edits to code files — a comment change inside a code file is NOT doc-only)
- [ ] Human testing + explicit approval obtained before committing
- [ ] No manual edits under `_bmad/`; no force push / deleted history
- [ ] Branch named `feat/…` / `fix/…` / `chore/…`; commits follow `type(scope): description` — English, imperative, under 72 chars, no trailing period, issue links as `issue https://...`
- [ ] Build verified locally before pushing (`gradlew :ComposeN-Zik:assembleDebug`; `gradlew.bat` on Windows — no pre-commit hooks in this repo)

### 📝 Changelogs (Step 8d commit mode)
- [ ] **Done+commit:** `assets/notes/Done.txt` updated using its template (`<keyword>(<scope>): short summary (issue ref)`, grouped under the section headers)
- [ ] **Bump+commit:** `fastlane/metadata/android/en-US/changelogs/{versionCode}.txt` (max 500 chars) + `Updater/changelogs/{versionCode}.txt` (no limit, full issue link) written **in English** from the Done.txt entries, and `Done.txt` emptied

### 🏗️ Code placement & hard rules (`AGENTS.md`, `CODE.md`)
- [ ] New code strictly in `app.n_zik.android.*` — NO new files under legacy `app.it.fast4x.rimusic.*` / `app.kreate.android.*`; any existing legacy file was edited ONLY with the user's explicit approval (quote it in the description above)
- [ ] New/changed strings added ONLY to `values/strings.xml` — never to `values-*/strings.xml` (Crowdin-managed); if this IS a bot-authored Crowdin sync PR, it is exempt from BMAD workflow + code review — state that in the description above
- [ ] Coroutines: `NzikDispatchers` named dispatchers only + `NzikDispatchers.fireAndForget()` for fire-and-forget scopes — no `GlobalScope`, no new raw `Dispatchers.*`, no `runBlocking` without a justification comment
- [ ] Compose: `collectAsStateWithLifecycle()` (never `collectAsState()`), atomic `_state.update { ... }` for state changes, `LazyColumn`/`LazyRow` with `key` + `contentType`
- [ ] No `!!` without a justification comment; Timber with tags ONLY (no `println`/`Log.d`/`System.out`); risky ops wrapped in `runCatching` with failures logged (never swallowed silently)
- [ ] Database schema untouched (or explicitly authorized); Room conventions kept (singular table names, `*Table` DAOs, `@RewriteQueriesToDropUnusedColumns`)
- [ ] No new dependency outside `libs.versions.toml` (ask first); no secrets/keys/keystores in the diff; external code is MIT/Apache-licensed with source cited in a comment

### 🧪 Build & Testing (`BUILD.md`)
- [ ] `./gradlew :ComposeN-Zik:assembleDebug` succeeds
- [ ] At least one test added for the new feature/bug fix (JUnit 5, or JUnit 4 vintage for Compose `createComposeRule()` tests); `./gradlew :ComposeN-Zik:test` passes locally

## 🗒️ Additional notes
-
