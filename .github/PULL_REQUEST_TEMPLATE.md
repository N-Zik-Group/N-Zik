<!-- Thanks for the contribution! 🙌
     Fill in what applies to your PR and tick the matching boxes — leave the rest unchecked (don't delete them).
     Hard rules: AGENTS.md + rules/*.md at the repo root. -->

## 📋 What are you changing?
<!-- The problem, your solution, context and decisions. If similar work already exists, link it and explain how this PR differs. -->

## 🔗 Related issues
<!-- Avoid words that auto-close the issue ("fixes", "closes") — use the `issue https://...` format. -->
- Issue: `issue https://github.com/N-Zik-Group/N-Zik/issues/…`

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
<!-- Tells us whether the 🤖 BMAD section of the checklist applies to this PR. -->
- [ ] 🤖 Yes — an AI agent helped produce this change (→ fill the 🤖 BMAD section below)
- [ ] 👤 No — I wrote it myself, no AI assistance

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
> Tick every box that applies to your PR; leave the rest unchecked.

### ✅ Always (every PR)
- [ ] I tested this change myself (on a device or emulator) before opening the PR
- [ ] No force push, no rewritten history
- [ ] Branch named `feat/…`, `fix/…` or `chore/…`
- [ ] Commits follow the repo convention — e.g. `feat(lyrics): add synced lyrics display`
  <!-- `type(scope): description` · English · imperative · under 72 chars · no final period · issue links as `issue https://...` -->

### 🤖 BMAD (only if "Made with AI" is ticked)
- [ ] I completed the full BMAD workflow — all 8 steps, including the final code review
- [ ] This PR is doc-only: it changes only Markdown/text prose — no code, no build files (even a code comment counts as code)
- [ ] `_bmad/` was not edited by hand

### 📝 Changelogs
- [ ] I added a `Done.txt` entry under the right section header — summary line **plus technical sub-bullets** (template: `assets/notes/Changelog_Template.txt`)
- [ ] *Release PR only:* I created both English changelogs — `fastlane/metadata/android/en-US/changelogs/{versionCode}.txt` (≤ 500 chars) and `Updater/changelogs/{versionCode}.txt` — and emptied `Done.txt`

### 🏗️ Code
- [ ] New code lives in `app.n_zik.android.*` — nothing new under the legacy packages; any legacy edit has explicit user approval (quoted in the description)
- [ ] New strings go in `values/strings.xml` only — I didn't touch any `values-*/` file (Crowdin-managed)
- [ ] This is a Crowdin sync PR — I said so in the description (exempt from BMAD + review)
- [ ] Coroutines run on `NzikDispatchers` only (`fireAndForget()` for fire-and-forget) — no `GlobalScope`, no raw `Dispatchers.*`, no `runBlocking` without a "why" comment
- [ ] Compose: `collectAsStateWithLifecycle()`, atomic `_state.update { }`, `key` + `contentType` on lazy lists
- [ ] I didn't add `!!` (any existing one has a comment explaining why)
- [ ] I log with `Timber.tag(...)` only — no `println` / `Log.d`
- [ ] Risky operations are wrapped in `runCatching`, and failures are logged
- [ ] I didn't touch the DB schema
- [ ] New Room code follows the repo style (singular tables, `*Table` DAOs)
- [ ] New dependencies go through `libs.versions.toml` (I asked before adding)
- [ ] No secrets or keystores in the diff
- [ ] External code is MIT/Apache-licensed, with the source cited

### 🧪 Build & tests
- [ ] `./gradlew :ComposeN-Zik:assembleDebug` is green (`gradlew.bat` on Windows)
- [ ] New feature or bug fix → at least one new test, and `./gradlew :ComposeN-Zik:test` passes

## 🗒️ Anything else?
-
