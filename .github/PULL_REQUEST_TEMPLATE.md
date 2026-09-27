<!-- Thanks for the contribution! 🙌
     Fill in what applies to your PR and tick the matching boxes — leave the rest unchecked (don't delete them).
     Hard rules: AGENTS.md + rules/*.md at the repo root. -->

## 📋 What are you changing?
<!-- The problem, your solution, context and decisions. If similar work already exists, link it and explain how this PR differs. -->

## 🔗 Related issues
<!-- Format: `issue https://...` — avoid words that auto-close ("fixes", "closes"). -->
- Issue:

## 🚀 Type of change
<!-- Pick the type(s) matching the commit types in rules/BUILD.md -->
- [ ] ✨ New feature (`feat`)
- [ ] 🐛 Bug fix (`fix`)
- [ ] 📈 Improvement of existing behavior (`improve`)
- [ ] ⚡ Performance (`perf`)
- [ ] 🧹 Refactor, no behavior change (`refactor`)
- [ ] 🧪 Tests only (`test`)
- [ ] 📖 Docs only (`docs`)
- [ ] 🛠️ Build / tooling / deps (`chore`)

## 🤖 Made with AI?
<!-- Tells us whether the BMAD section of the checklist applies to this PR. -->
- [ ] 🤖 Yes — the change was produced with the help of an AI agent (BMAD workflow used)
- [ ] 👤 No — written by hand, the BMAD section does not apply

## 📸 Screenshots / video
<!-- Before/after for visual changes (image, GIF or MP4). Skip if not applicable. -->
| Before | After |
| ------ | ----- |
|        |       |

## ✅ How can we verify it?
<!-- Steps for a reviewer + concrete evidence: diffs, build/test output, screenshots. "It works" alone doesn't cut it 🙂 -->
1.
2.

---

## 🛠️ Checklist
> Tick what applies — leave the rest unchecked.

### ✅ Always (every PR)
- [ ] The change was tested by a human and is approved for merge (never merge/commit without human approval)
- [ ] No force push, no rewritten history
- [ ] Branch named `feat/…`, `fix/…` or `chore/…`
- [ ] Commits follow `type(scope): description` — English, imperative, under 72 chars, no final period, issue links as `issue https://...`
- [ ] Local build green before pushing — `gradlew :ComposeN-Zik:assembleDebug` (`gradlew.bat` on Windows; no pre-commit hooks in this repo)

### 🤖 BMAD (only if "Made with AI" is ticked)
- [ ] BMAD workflow complete — all 8 steps, including the 8b code-review gate
- [ ] *Doc-only PR:* the diff touches only `.md`/`.txt` prose — zero code or build files (a comment inside a code file does NOT count)
- [ ] `_bmad/` was not edited by hand

### 📝 Changelogs
- [ ] `assets/notes/Done.txt` updated using its template (`<keyword>(<scope>): short summary (issue ref)`, under the section headers)
- [ ] *For a release:* changelogs written **in English** — `fastlane/metadata/android/en-US/changelogs/{versionCode}.txt` (max 500 chars) + `Updater/changelogs/{versionCode}.txt` (full issue link) — and `Done.txt` emptied

### 🏗️ Code
- [ ] New code in `app.n_zik.android.*` — nothing new under the legacy packages; a legacy file was only edited with explicit user approval (quoted in the description)
- [ ] New strings in `values/strings.xml` only — never in `values-*/` (Crowdin-managed). If this IS a Crowdin sync PR, say so in the description (exempt from BMAD + review)
- [ ] Coroutines on `NzikDispatchers` only (`fireAndForget()` for fire-and-forget) — no `GlobalScope`, no raw `Dispatchers.*`, no `runBlocking` without a "why" comment
- [ ] Compose: `collectAsStateWithLifecycle()`, atomic `_state.update { }`, `key` + `contentType` on lazy lists
- [ ] No `!!` without a reason · Timber with tags only · risky ops in `runCatching`, failures always logged
- [ ] DB schema untouched (unless explicitly authorized) · Room style kept (singular tables, `*Table` DAOs)
- [ ] New deps via `libs.versions.toml` only (ask first) · no secrets or keystores in the diff · external code is MIT/Apache + source cited

### 🧪 Build & tests
- [ ] `./gradlew :ComposeN-Zik:assembleDebug` is green
- [ ] New feature or bug fix → at least one new test, and `./gradlew :ComposeN-Zik:test` passes

## 🗒️ Anything else?
-
