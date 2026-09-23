# Workflow Rules

**Version:** 1.3.0 | **Last updated:** 2026-09-23

## Session Startup Sequence

1. Read AGENTS.md entirely
2. Read all @referenced rules files (BMAD.md, CODE.md, BUILD.md, WORKFLOW.md, SECURITY.md, RECOVERY.md, BMAD-TOOLS.md)
3. Match user's language (from question tool prompt or BMAD `config.user.toml`)
4. Announce critical rules (simplified list below)
5. Ask user using question tool: "Bug, feature, or something else?"
6. Wait for user input
7. If bug or feature: ASK which IDE/tool (ONE at a time), ASK which skill to use

> **"question tool"** = the IDE's interactive ask mechanism. If your IDE has no such tool, ask in chat and WAIT for the reply before proceeding — the blocking semantics are identical for every gate.

## Announcement Template

Use this format every session — keep it SHORT:

```
📋 Rules loaded:

[CRITICAL]
1. Code → app.n_zik.android.* only (legacy packages READ-ONLY)
2. Timber with tags ONLY (no println/Log.d)
3. Build: ./gradlew :ComposeN-Zik:assembleDebug
4. Version catalog refs only (libs.versions.toml)
5. NEVER commit without human approval
6. NEVER skip BMAD workflow — complete FULL workflow before coding (exception: trivial doc-only edits, see "Doc-Only Exception")
7. User suggestions ≠ shortcut (still complete workflow)
8. Ask IDE ONE at a time (path depends on IDE)
9. NEVER edit values-*/strings.xml (only values/)
10. NEVER edit DB schema without explicit instruction
11. NEVER skip ahead to later steps — follow Step 1→2→3... in order
12. After any interruption, re-announce current step before continuing
13. If you lost context, re-read the current step file
14. After BMAD workflow COMPLETE: if user gives logs/bug/errors, START NEW bmad-cis-problem-solving or bmad-code-review workflow — don't improvise
15. User input (logs, screenshots, errors) = new workflow trigger, NOT freeform response
16. Steps 4 (plan validation), 8b (code review) and 8d (commit mode) are HARD GATES — ask via question tool, wait for the answer, NEVER skip them or force file edits
17. Step 6 code hygiene gate: unused imports / dead code / comments / indentation checked AND shown (evidence) before the build

See rules/*.md for full details.
```

## Step-by-Step Workflow

**This workflow has 8 steps and EVERY step is mandatory. NEVER stop before Step 8. Steps 4, 8b and 8d are HARD GATES.**
**`rules/CODE.md` applies to EVERY line of code at EVERY step — no step is exempt from code quality.**

### Doc-Only Exception (Scope Gate)

Before triggering the full BMAD workflow, check if the request is **doc-only and trivial**:

- Applies ONLY to: typo fixes, `Done.txt`/changelog wording, README/markdown prose — **zero changes to `.kt`, `.xml`, `.toml`, `.gradle.kts`, or any build/schema file** (a comment change inside a code file is NOT doc-only — run the full workflow)
- If it qualifies → SKIP the BMAD workflow, make the edit directly, show the diff, ask for approval before committing (commit approval rule from AGENTS.md still applies)
- If there is ANY doubt whether a change is "trivial" (e.g. it touches a string resource key, not just prose) → treat it as a normal change and run the full workflow
- This exception does NOT apply to code, schema, dependency, or config changes, however small
- Announce when using this exception: `[Doc-only exception — BMAD workflow skipped]` + the exact list of files that will be modified
- The exception is INVALIDATED automatically if the final diff touches any file other than `.md`/`.txt` prose — in that case HALT and run the full workflow
- If the exception is borderline (the user might reasonably disagree it is "trivial") → ASK the user first, don't self-judge

### Step 1: Understand

- Read request carefully
- Ask clarifying questions: one clear question per decision — unrelated decisions are NEVER bundled into one question; related questions MAY be grouped in a single question tool call
- Identify scope (modules, files, packages affected)

### Step 2: Explore

- Search existing implementations
- Read neighboring files for conventions
- Check imports and dependencies
- Consult reference projects in `{project-root}/docs/` (workspace root, e.g. Cubic-Music) if needed
- Show evidence: list the key files found/read (paths) that shaped the approach — never "explore" invisibly

### Step 3: Execute BMAD Skill (MANDATORY)

NEVER write code or create implementation plans without completing this step.

**Loading a skill ≠ Completing the workflow.** You MUST complete ALL sub-steps below.

#### 3a: Select IDE and Skill

- **ASK FIRST:** Which IDE/tool they are using (before loading any skill) — **ask ONE IDE at a time** (skill path depends on IDE, see BMAD-TOOLS.md). **Order:** OpenCode ⭐, GitHub Copilot ⭐, Google Antigravity ⭐, then others (Claude Code, Cursor, Codex, etc.).
- **ASK FIRST:** Which skill to use (propose recommended, let user choose)
- Identify appropriate skill (analyze skills directory first)
- For bugs: `bmad-cis-problem-solving`, then `bmad-code-review`
- For additions: `bmad-build` (still requires minimal planning at step-02)
- Locate skills on disk (`{project-root}/.agents/skills/` for most IDEs)

#### 3b: BMAD Activation Sequence (MANDATORY for every skill)

**`{project-root}`** = the workspace root directory containing both `_bmad/` and `.agents/` (if only one exists, prefer `_bmad/`). Resolve it at runtime by finding that directory.

> **Important for this project:** `_bmad/` and `.agents/` live at the **parent** of `N-Zik/`. If your CWD is `N-Zik/`, go **up one level** to find `{project-root}`.

**`{skill-root}`** = `{project-root}/{target_dir}/{skill-name}` where `target_dir` depends on your IDE:
- **Cursor/Copilot/Codex/OpenCode/Windsurf:** `{project-root}/.agents/skills/{skill-name}`
- **Claude Code:** `{project-root}/.claude/skills/{skill-name}`
- **Google Antigravity:** `{project-root}/.agent/skills/{skill-name}`

Example for `bmad-build` with OpenCode: `{project-root}/.agents/skills/bmad-build`

**TWO SKILL FORMATS EXIST — read the SKILL.md first and pick the matching activation path:**

- **(a) Bootstrap format** (e.g. `bmad-build`): the SKILL.md instructs running `_bmad/scripts/render_skill.py` exactly once. → Run that command FIRST, then follow ONLY the rendered `workflow.md` it prints (its own "On Activation" section supersedes steps 1–8 below). On failure (including `uv` unavailable) → HALT and report the command output; no manual fallback, no direct reading of the workflow sources.
- **(b) Inline format** (e.g. `bmad-cis-*`): follow steps 1–8 below.

1. Run `resolve_customization.py` to get merged config:

   ```
   uv run {project-root}/_bmad/scripts/resolve_customization.py --skill {skill-root} --key agent
   ```

   (or `--key workflow` for workflow skills)

   > **Path tip:** If running from `N-Zik/`, `{project-root}` resolves to the parent directory. Use `..` or resolve the absolute path to `N-Zik-Projet/` before running scripts.

2. If script fails → manually read 3 files in order and merge:
   - `{skill-root}/customize.toml` (defaults)
   - `{project-root}/_bmad/custom/{skill-name}.toml` (team)
   - `{project-root}/_bmad/custom/{skill-name}.user.toml` (personal)

3. Execute `activation_steps_prepend` (before greeting)

4. Load `persistent_facts` — file refs loaded as context, literal text kept verbatim

5. Load config from the skill's module config (e.g., `_bmad/cis/config.yaml` for CIS skills, `_bmad/bmm/config.yaml` for BMM skills) — check the skill's SKILL.md for the correct path

6. Adopt persona (role, identity, communication_style, principles)

7. Greet user using `{user_name}` and `{communication_language}`

8. Execute `activation_steps_append` (after greeting, before workflow)

**After activation, read the ENTIRE workflow source before starting: the full `SKILL.md` (inline skills) or the rendered `workflow.md` (bootstrap skills).** Do NOT just read the step headers — read EVERY line including:

- `<template-output>` tags (what to produce at each step)
- `<energy-checkpoint>` tags (when to ask for breaks)
- Checkpoint instructions (what options to present after each step)
- `<action>` tags (what scripts to run)
- All instructions between step tags

**After activation, follow the skill's workflow step by step — NEVER skip to implementation.**

**HALT at every checkpoint.**

#### 3c: Spec Production (MANDATORY)

- If the skill has a spec template file (`template.md` or `spec-template.md` — check the skill root's file listing) → you MUST produce a spec document using that template
- **Process:**
  1. Read `{template_file}` (the template structure with `{{placeholders}}`)
  2. After each step, replace the `{{placeholders}}` with actual values
  3. **Write/update the spec file on disk AFTER EACH STEP** (use the Write tool, incremental updates)
  4. Display the content in chat for checkpoint
  5. After each write, state the ABSOLUTE PATH of the spec file on disk (the path is the evidence — never just claim "saved")
- **Where to write:** Read the skill's SKILL.md for the exact output path (resolved from config, e.g. `{project-root}/_bmad-output/`)
- **In this project:** `{output_folder}` = `{project-root}/_bmad-output` (at workspace root, one level above `N-Zik/`)
- Show checkpoint separator, display generated content, present the checkpoint options DEFINED BY THE LOADED SKILL (CIS skills: `[a] [c] [p] [y]`; step-file skills: their `### CHECKPOINT` sections — verbatim)
- Wait for user response before proceeding to next step
- NEVER skip spec production — the spec IS the workflow output
- NEVER just display the spec in chat — it MUST be saved to a file
- NEVER wait until the end to write the spec — write AFTER EACH STEP
- At the spec's final checkpoint, verify the file actually exists on disk (read it back) before presenting the options

#### 3d: on_complete hook (MANDATORY)

- After workflow completes, run: `uv run {project-root}/_bmad/scripts/resolve_customization.py --skill {skill-root} --key workflow.on_complete`
- If the resolved value is non-empty → follow it as final terminal instruction before exiting
- NEVER skip this hook — it is the skill's official completion action
- If the script cannot be executed (uv missing, path or permission error) → say so explicitly to the user, then continue — never fail silently, never "skip" without reporting

#### 3e: Energy checkpoints (MANDATORY)

- If the skill has `<energy-checkpoint>` tags → pause and ask the user about their energy level
- Present the checkpoint message exactly as written in the skill
- Wait for user response before proceeding
- NEVER skip energy checkpoints — they prevent burnout during long sessions

#### 3f: Co/Fast path choice (MANDATORY)

- Some skills (bmad-architecture, bmad-prd, bmad-ux, bmad-product-brief) require offering:
  - **Coaching path** — guided, explains each step
  - **Fast path** — streamlined, skips explanations
- Ask user which path before any drafting begins
- NEVER skip this choice — it affects the entire workflow

#### 3g: external_handoffs (MANDATORY)

- If the skill has `{workflow.external_handoffs}` → execute it and surface returned URLs/IDs
- Skip and flag unavailable tools (don't crash)
- This routes artifacts to external systems (Confluence, Notion, Jira, etc.)

#### 3h: doc_standards (MANDATORY)

- If the skill has `{workflow.doc_standards}` → apply them in order
- Structural passes before prose — do not polish soon-to-be-cut text

#### 3i: finalize_reviewers (MANDATORY)

- If the skill has `{workflow.finalize_reviewers}` → dispatch reviewer lenses as parallel subagents
- Each reviewer lens evaluates the artifact independently
- Surface all reviewer feedback to user before finalizing

**Before writing ANY code:** verify you have completed EVERY step of the loaded BMAD skill's workflow. Read the skill's step files in order — if any step is incomplete → HALT, do NOT write code.

**Enforcement — before starting the workflow:**

1. Read the skill's SKILL.md file — **EVERY line, NOT just step headers**
2. Count the total number of steps in the loaded workflow — either the `<workflow>` section (inline skills), the `## On Activation`/step sections of the rendered `workflow.md`, or the `step-NN-*.md` files (bootstrap/step-file skills)
3. List all steps: "Steps: 1. X, 2. Y, 3. Z, ..."
4. List the output artifact per step (from `<template-output>` tags when present, otherwise from each step's explicit "write `{spec_file}`" instruction)
5. List the break points (from `<energy-checkpoint>` tags when present, otherwise from `### CHECKPOINT N` sections and "WAIT FOR INPUT" instructions)
6. List all checkpoint instructions (what options to present — from the skill's checkpoint sections, verbatim)
7. Announce: "BMAD workflow has N steps. Starting step 1."

**Enforcement — during the workflow:**

- Before each action, announce the current step — TWO formats only: `[Step X/8: <name>]` for the 8-step wrapper workflow (at every transition) and `[BMAD Step X/N: <step name>]` for the loaded BMAD skill's internal steps. For the Step 8 sub-steps use `[Step 8x: <name>]` (e.g. `[Step 8b: Code Review Proposal]`, `[Step 8d: Commit]`)
- After each step, present the checkpoint options DEFINED BY THE LOADED SKILL (CIS skills use `[a] [c] [p] [y]`; `bmad-build` uses its own `### CHECKPOINT N` sections — present them verbatim). If the skill defines no option list, present its checkpoint message verbatim — NEVER invent options, and NEVER just ask "Step X complete. Proceed to step Y?"
- Before implementing, verify: "All N steps complete. Ready to implement?"
- If you cannot name the current step → HALT, you are lost

**User suggestions are input to the workflow, NOT a shortcut to skip it.** Even if the user suggests a specific fix, complete the skill's full workflow before implementing.

**If user declines BMAD skill:** HALT and explain that BMAD workflow is mandatory per AGENTS.md rules. Ask user to confirm they want to proceed without BMAD. If the user confirms: record the explicit waiver, but it covers the BMAD skill (Step 3) ONLY — Step 6 (hygiene gate + build + tests), Step 8b (review gate) and Step 8d (commit mode gate) still apply. When Step 3 is waived, Step 4 is replaced by: present the implementation plan (files, approach, risks) and ask the same plan-approval question (HARD GATE unchanged). Steps 1, 2, 5, 7, 8a, 8b, 8c (after review) and 8e still apply unchanged. If the user does not confirm → HALT, no code is written.

**If skill not found:**

1. Search the IDE-specific skills directory for the user's IDE (see `rules/BMAD-TOOLS.md` table — most IDEs use `{project-root}/.agents/skills/`)
2. If still not found → HALT, inform user, suggest re-running BMAD installer
3. If SKILL.md is malformed → HALT, report error, suggest `bmad-module-builder` to rebuild

**IMPORTANT: This workflow has 8 steps. NEVER stop before Step 8. Step 8 (Post-BMAD Actions) is MANDATORY.**

#### 3j: Step-File Skills (micro-file design)

Some skills use micro-file design where each step is in its own file.

**Rules (NO EXCEPTIONS):**

- NEVER load multiple step files simultaneously
- ALWAYS read entire step file before execution
- NEVER skip steps or optimize the sequence
- ALWAYS follow exact instructions in the step file
- ALWAYS halt at checkpoints and wait for human input
- Load next step file ONLY when directed by current step

### Step 4: Validate Plan (MANDATORY — HARD GATE)

- This step is a hard gate: after the plan/spec is produced, HALT and wait for the user's answer before editing ANY file, running ANY build, or starting ANY implementation — no step 5, no code, no "it's obvious, proceeding anyway".
- Before implementing, **MUST ask user using question tool** — process:
  - Read the SKILL.md to see what actions/checkpoints are available after the plan
  - Present the actions from the SKILL.md verbatim (e.g. `[a] [c] [p] [y]` for CIS skills; the `### CHECKPOINT` sections for `bmad-build`)
  - Wait for user to choose before proceeding
- If the session was interrupted before the question was answered → on resume, re-announce Step 4 and ask the question again (never assume a previous answer)

**NEVER implement without user approval.**

### Step 5: Implement

- Write clean code following ALL of `rules/CODE.md` (naming, imports, comments, dead code, Timber-only logging, null-safety, Compose anti-patterns, file placement) — apply them WHILE coding, never defer cleanup to the end
- Follow existing patterns
- Handle errors appropriately
- Remove dead code and unused imports as you go

### Step 6: Verify

- **Code hygiene gate (MANDATORY — BEFORE the build):** run a cleanup pass over every file in the diff and verify each item (per `rules/CODE.md`):
  - Unused imports removed; no wildcard imports; imports grouped (stdlib → third-party → project)
  - No dead code: no commented-out code blocks, no unused functions/classes/variables/parameters (if intentionally kept: `// TODO(author): reason`)
  - Comment hygiene: no comments that restate the code, KDoc on public APIs, TODOs as `// TODO(author): description`
  - Indentation & formatting: consistent with the surrounding code, no trailing whitespace, newline at end of file; run ktlint/detekt if configured in the project
  - Timber with tags ONLY (no `println`/`Log.d`/`System.out`), no `!!` without a justification comment, no `GlobalScope`/`runBlocking`/`collectAsState()`
- Show evidence for the hygiene gate: list the files cleaned + what was removed (or explicitly state "no issues found") — never just claim "clean"
- Build: `./gradlew :ComposeN-Zik:assembleDebug` (on Windows: `gradlew.bat`)
- Run tests (new feature/bug fix → at least one new test, per AGENTS.md: list the test file(s) added)
- Review changes for quality
- **Guard check (MANDATORY):** run `git diff --name-only HEAD` (staged + unstaged) AND `git status --porcelain` (includes untracked new files) from the repo root `N-Zik/`, and verify that NEITHER contains any file under `app.it.fast4x.rimusic.*` or `app.kreate.android.*` nor any `values-*/strings.xml` file — if one appears, HALT, revert it and report to the user (EXCEPTION: a legacy file modification explicitly approved by the user per the AGENTS.md legacy-modification rule — quote the approval in the report; the guard still applies to any NEW legacy file)
- Show evidence: paste the build output tail + test results — never just claim "done"

### Step 7: Report

- Summarize what was done and why
- Note files modified or created
- Do NOT commit unless explicitly asked

### Step 8: Post-BMAD Actions (MANDATORY)

After the BMAD workflow completes, **MUST follow this exact flow** — NEVER skip any step:

**Step 8a: Build and Test**

- Run `./gradlew :ComposeN-Zik:assembleDebug` (on Windows: `gradlew.bat`)
- Run relevant tests
- Show evidence: paste the tail of the build output (`BUILD SUCCESSFUL` / failing tests + counts) — never claim "build passed" without output
- If FAILS → fix and rebuild with the SAME 3-attempt limit as BUILD.md/RECOVERY.md: **HALT after 3 failed attempts** → report to user with the full error log (NEVER loop "until it passes" indefinitely)

**Step 8b: Code Review Proposal (HARD GATE — NEVER SKIP)**

- This step is a hard gate: after Step 8a, HALT and wait for the user's answer before doing ANYTHING else — no 8c/8d/8e, no commit-related file edits, no "workflow complete" announcement, no reporting the task as done.
- **MUST ask user using question tool, translated into `{communication_language}`:**
  ```
  Code is functional. Proceed to code review ?
  1. Yes → launch bmad-code-review
  2. No → structured self-check pass + fixes
  ```
- If user says "No" → do a structured self-check pass (null-safety, structured concurrency/lifecycle, Timber usage, error handling, test coverage), LIST the findings (or explicitly state "none found"), fix them, rebuild, then ask the Step 8b question again (the user may change their mind) — ask this question at most ONE more time; if the user says "No" again, record it as an explicit decision to skip the review (announce it: "8c skipped — no review") and proceed to Step 8d. No further self-check loops.
- If user says "Yes" → load and execute `bmad-code-review` skill
- If the `bmad-code-review` skill fails to load or execute (render/uv error, skill HALT) → HALT, report the error verbatim, and ask the user via question tool: (1) retry the skill, (2) proceed to the structured self-check pass of the "No" branch — explicitly announced as NOT fulfilling the 8b review gate
- If the session was interrupted before the question was answered → on resume, re-announce Step 8b and ask the question again (never assume a previous answer)

**Step 8c: Post-Review Actions**

- First, present the review findings VERBATIM to the user (every issue, with severity and file refs). The verdict below is the USER's call — the agent NEVER decides on its own that the review is "good enough" or "functional".
- After code review completes, **MUST ask user using question tool, translated into `{communication_language}`:**
  ```
  Code review complete. What next ?
  1. Functional → proceed to commit
  2. Not functional → fix the findings (then re-review)
  3. Other → ask user
  ```
- If "Not functional" → fix the findings, rebuild + re-run tests, then RE-RUN `bmad-code-review` on the fixed scope before asking 8c again (fixes to review findings require a fresh review — never present self-judged fixes as review-passed). After **3** fix/re-review cycles without a "Functional" verdict → HALT and report the recurring findings to the user instead of looping again
- If the session was interrupted before the question was answered → on resume, re-announce Step 8c and ask the question again (never assume a previous answer)

**Step 8d: Commit (only if user says "Functional") — ASK MODE FIRST, THEN EDIT**

- **MUST ask user using question tool BEFORE editing ANY file** (NEVER create or modify `fastlane/`, `Updater/`, `assets/notes/Done.txt` or version numbers before the user has chosen a mode), **translated into `{communication_language}`**:
  ```
  Code is functional. How do you want to commit ?
  1. Bump + commit → full release
  2. Done + commit → Done.txt only, no release
  3. Wait → nothing
  ```
- Wait for the user's answer before touching any file, then apply ONLY the chosen mode:
  - **1. Bump + commit (full release):**
    - Bump `nzikVersionCode` (+1) and `nzikVersionName` in `gradle/libs.versions.toml` (ask the user for the new `nzikVersionName` when it is not obvious)
    - Create `fastlane/metadata/android/en-US/changelogs/{newVersionCode}.txt` from the `Done.txt` entries, using its own template (`Changelog_Template.txt` in same folder) — **max 500 characters**
    - Create `Updater/changelogs/{newVersionCode}.txt` from the `Done.txt` entries, using its own template — **no character limit**, include full issue link
    - Both changelogs are written in ENGLISH (fastlane metadata is en-US) — even when the session language is another one
    - Empty `assets/notes/Done.txt` (the released entries now live in the changelogs)
  - **2. Done + commit (no release):**
    - Append the new work to `assets/notes/Done.txt` using its own template (`Changelog_Template.txt` in same folder) — format: `<keyword>(<scope>): <short summary> (issue ref)` + technical sub-bullets, include full issue link — NO version bump, NO `fastlane/`/`Updater/` files
  - **3. Wait:**
    - Do NOT edit any file, do NOT commit — announce that the task is paused and how to resume (re-ask the Step 8d commit-mode question in a new conversation — do NOT re-ask Step 8b, which is already resolved)
- If the session was interrupted before the question was answered → on resume, re-announce Step 8d and ask the question again (never assume a previous answer)
- After the chosen edits, show the diff AND the proposed commit message (conventional format `type(scope): …` per BUILD.md), then **MUST ask user for commit approval** (NEVER commit without approval) — ONE prompt, not separately, **translated into `{communication_language}`**:
  ```
  Do you approve this commit ?
  Message: <type(scope): short description>
  1. Commit + push
  2. Commit only (no push)
  3. Cancel
  ```

> **Rule:** every user-facing prompt template in this file is written in English as a reference — agents MUST present it translated into `{communication_language}` (resolved from BMAD config), never mix languages within the same session.
- If "commit + push" → `git commit` + `git push`
- If "commit only" → `git commit` only, no push
- If "cancel" → leave the working tree as-is (edited files stay uncommitted), report what is pending

**Step 8e: Finish Workflow (always runs)**

- Run the `on_complete` hook ONLY if it was NOT already executed in Step 3d — it is a single hook: never run it twice (if already run, state so and move on)
- Announce: "Workflow complete."
- **Start a new conversation** — the next task should begin with fresh context. Instruct the user (in `{communication_language}`) to open a new conversation for the next task.

**NEVER skip any of these steps. The BMAD workflow is NOT complete until code is verified, reviewed, and committed (if approved).**

## Multi-Module Changes

When changes span multiple modules (`extensions/`, `modules/`, `ComposeN-Zik/`):

1. Identify all affected modules before starting
2. Build each module individually if possible
3. Test cross-module interactions
4. Verify no circular dependencies introduced
5. Report which modules were affected

## Announce Steps

Before ANY file edit, command, or tool call, output a short plan:

- Detailed plan when starting new task or deviating
- Between actions inside a step, the same tags apply (no other formats)
- At every transition between the 8 workflow steps (1→2→…→8), announce `[Step X/8: <name>]` — the user must always be able to see which step is running
- NEVER skip this rule, even for "obvious" fixes

## BMAD Dual Enforcement

Follow BOTH AGENTS.md AND BMAD rules IN PARALLEL — at EVERY step of the workflow.

- AGENTS.md wins on: code quality, security, commits, logging, database, build
- BMAD wins on: workflow ordering, templates, checkpoints
- **AGENTS.md rules apply DURING the BMAD workflow, not just after**

**Conflict resolution example:**

```
CONFLICT:
AGENTS.md says: "Never commit without human approval"
BMAD workflow says: "Mark story complete and commit"
RESOLUTION: AGENTS.md wins — HALT, ask user for commit approval
```

**NEVER use "I'm following BMAD" as an excuse to skip AGENTS.md rules.**
