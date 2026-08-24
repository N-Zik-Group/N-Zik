# Workflow Rules

**Version:** 1.2.0 | **Last updated:** 2026-08-24

## Session Startup Sequence

1. Read AGENTS.md entirely
2. Read all @referenced rules files (BMAD.md, CODE.md, BUILD.md, WORKFLOW.md, SECURITY.md, RECOVERY.md, BMAD-TOOLS.md)
3. Match user's language (from question tool prompt or BMAD `config.user.toml`)
4. Announce critical rules (simplified list below)
5. Ask user using question tool: "Bug, feature, or something else?"
6. Wait for user input
7. If bug or feature: ASK which IDE/tool (ONE at a time), ASK which skill to use

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
6. NEVER skip BMAD workflow — complete FULL workflow before coding
7. User suggestions ≠ shortcut (still complete workflow)
8. Ask IDE ONE at a time (path depends on IDE)
9. NEVER edit values-*/strings.xml (only values/)
10. NEVER edit DB schema without explicit instruction
11. NEVER skip ahead to later steps — follow Step 1→2→3... in order
12. After any interruption, re-announce current step before continuing
13. If you lost context, re-read the current step file
14. After BMAD workflow COMPLETE: if user gives logs/bug/errors, START NEW bmad-cis-problem-solving or bmad-code-review workflow — don't improvise
15. User input (logs, screenshots, errors) = new workflow trigger, NOT freeform response

See rules/*.md for full details.
```

## Step-by-Step Workflow

**This workflow has 8 steps. NEVER stop before Step 8. Steps 4, 7, and 8 are MANDATORY.**

### Step 1: Understand

- Read request carefully
- Ask clarifying questions (each as separate question tool call)
- Identify scope (modules, files, packages affected)

### Step 2: Explore

- Search existing implementations
- Read neighboring files for conventions
- Check imports and dependencies
- Consult reference projects in `docs/` if needed

### Step 3: Execute BMAD Skill (MANDATORY)

NEVER write code or create implementation plans without completing this step.

**Loading a skill ≠ Completing the workflow.** You MUST complete ALL sub-steps below.

#### 3a: Select IDE and Skill

- **ASK FIRST:** Which IDE/tool they are using (before loading any skill) — **ask ONE IDE at a time** (skill path depends on IDE, see BMAD-TOOLS.md). **Order:** Claude Code, Cursor, GitHub Copilot, Codex (⭐ preferred), then OpenCode, then others.
- **ASK FIRST:** Which skill to use (propose recommended, let user choose)
- Identify appropriate skill (analyze skills directory first)
- For bugs: `bmad-cis-problem-solving`, then `bmad-code-review`
- For additions: `bmad-build` (still requires minimal planning at step-02)
- Locate skills on disk (`{project-root}/.agents/skills/` for most IDEs)

#### 3b: BMAD Activation Sequence (MANDATORY for every skill)

**`{project-root}`** = the workspace root directory containing `_bmad/` and `.agents/`. Resolve it at runtime by finding the directory that contains `_bmad/` or `.git/`.

**`{skill-root}`** = `{project-root}/{target_dir}/{skill-name}` where `target_dir` depends on your IDE:
- **Cursor/Copilot/Codex/OpenCode/Windsurf:** `{project-root}/.agents/skills/{skill-name}`
- **Claude Code:** `{project-root}/.claude/skills/{skill-name}`
- **Google Antigravity:** `{project-root}/.agent/skills/{skill-name}`

Example for `bmad-build` with OpenCode: `{project-root}/.agents/skills/bmad-build`

1. Run `resolve_customization.py` to get merged config:

   ```
   uv run {project-root}/_bmad/scripts/resolve_customization.py --skill {skill-root} --key agent
   ```

   (or `--key workflow` for workflow skills)

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

**After activation, read the ENTIRE SKILL.md file before starting the workflow.** Do NOT just read the step headers — read EVERY line including:

- `<template-output>` tags (what to produce at each step)
- `<energy-checkpoint>` tags (when to ask for breaks)
- Checkpoint instructions (what options to present after each step)
- `<action>` tags (what scripts to run)
- All instructions between step tags

**After activation, follow the skill's workflow step by step — NEVER skip to implementation.**

**HALT at every checkpoint.**

#### 3c: Spec Production (MANDATORY)

- If the skill has a `template.md` → you MUST produce a spec document using that template
- **Process:**
  1. Read `{template_file}` (the template structure with `{{placeholders}}`)
  2. After each step, replace the `{{placeholders}}` with actual values
  3. **Write/update the spec file on disk AFTER EACH STEP** (use the Write tool, incremental updates)
  4. Display the content in chat for checkpoint
- **Where to write:** `{output_folder}/{default_output_file}` — read the skill's SKILL.md for the exact path
- **In this project:** `{output_folder}` = `{project-root}/_bmad-output` (at workspace root, one level above `N-Zik/`)
- Show checkpoint separator, display generated content, present options `[a] Advanced Elicitation`, `[c] Continue`, `[p] Party-Mode`, `[y] YOLO`
- Wait for user response before proceeding to next step
- NEVER skip spec production — the spec IS the workflow output
- NEVER just display the spec in chat — it MUST be saved to a file
- NEVER wait until the end to write the spec — write AFTER EACH STEP

#### 3d: on_complete hook (MANDATORY)

- After workflow completes, run: `uv run {project-root}/_bmad/scripts/resolve_customization.py --skill {skill-root} --key workflow.on_complete`
- If the resolved value is non-empty → follow it as final terminal instruction before exiting
- NEVER skip this hook — it is the skill's official completion action

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
2. Count the total number of steps in the `<workflow>` section
3. List all steps: "Steps: 1. X, 2. Y, 3. Z, ..."
4. List all `<template-output>` tags per step (what to produce)
5. List all `<energy-checkpoint>` tags (when to ask for breaks)
6. List all checkpoint instructions (what options to present)
7. Announce: "BMAD workflow has N steps. Starting step 1."

**Enforcement — during the workflow:**

- Before each action, announce: `[BMAD Step X/N: <step name>]`
- After each step, present the checkpoint options from the SKILL.md (e.g., `[a] Advanced Elicitation`, `[c] Continue`, `[p] Party-Mode`, `[y] YOLO`) — NEVER just ask "Step X complete. Proceed to step Y?"
- Before implementing, verify: "All N steps complete. Ready to implement?"
- If you cannot name the current step → HALT, you are lost

**User suggestions are input to the workflow, NOT a shortcut to skip it.** Even if the user suggests a specific fix, complete the skill's full workflow before implementing.

**If user declines BMAD skill:** HALT and explain that BMAD workflow is mandatory per AGENTS.md rules. Ask user to confirm they want to proceed without BMAD.

**If skill not found:**

1. Search `{project-root}/.agents/skills/` (or `.claude/skills/` for Claude Code, `.agent/skills/` for Antigravity)
2. If still not found → HALT, inform user, suggest re-running BMAD installer
3. If SKILL.md is malformed → HALT, report error, suggest `bmad-module-builder` to rebuild

**IMPORTANT: This workflow has 8 steps. NEVER stop before Step 8. Step 8 (Post-BMAD Actions) is MANDATORY.**

## Step-File Architecture

Some skills use micro-file design where each step is in its own file.

**Rules (NO EXCEPTIONS):**

- NEVER load multiple step files simultaneously
- ALWAYS read entire step file before execution
- NEVER skip steps or optimize the sequence
- ALWAYS follow exact instructions in the step file
- ALWAYS halt at checkpoints and wait for human input
- Load next step file ONLY when directed by current step

### Step 4: Validate Plan (MANDATORY)

Before implementing, **MUST ask user using question tool:**

- Read the SKILL.md to see what actions/checkpoints are available after the plan
- Present the actions from the SKILL.md (e.g., `[a] Advanced Elicitation`, `[c] Continue`, `[p] Party-Mode`, `[y] YOLO`)
- Wait for user to choose before proceeding

**NEVER implement without user approval.**

### Step 5: Implement

- Write clean code following all guidelines
- Follow existing patterns
- Handle errors appropriately
- Remove dead code

### Step 6: Verify

- Build: `./gradlew :ComposeN-Zik:assembleDebug`
- Run tests if available
- Review changes for quality

### Step 7: Report

- Summarize what was done and why
- Note files modified or created
- Do NOT commit unless explicitly asked

### Step 8: Post-BMAD Actions (MANDATORY)

After the BMAD workflow completes, **MUST follow this exact flow** — NEVER skip any step:

**Step 8a: Build and Test**

- Run `./gradlew :ComposeN-Zik:assembleDebug`
- Run relevant tests
- If FAILS → fix and rebuild until it passes

**Step 8b: Code Review Proposal**

- **MUST ask user using question tool:**
  ```
  Code is functional. Proceed to code review ?
  1. Yes → launch bmad-code-review
  2. No → re-read code and fix issues
  ```
- If user says "No" → re-read code, fix issues, rebuild, ask again
- If user says "Yes" → load and execute `bmad-code-review` skill

**Step 8c: Post-Review Actions**

- After code review completes, **MUST ask user using question tool:**
  ```
  Code review complete. What next ?
  1. Functional → update Done.txt + git commit
  2. Not functional → re-read and fix
  3. Other → ask user
  ```

**Step 8d: Commit (only if user says "Functional")**

- Update `assets/notes/Done.txt` using its own template (`Changelog_Template.txt` in same folder) — format: `<keyword>(<scope>): <short summary> (issue ref)` + technical sub-bullets, include full issue link
- Update `fastlane/metadata/android/en-US/changelogs/{version}.txt` using its own template (`Changelog_Template.txt` in same folder) — **max 500 characters**
- Update `Updater/changelogs/{version}.txt` using its own template (`Changelog_Template.txt` in same folder) — **no character limit**, include full issue link
- **MUST ask user for commit approval** (NEVER commit without approval)
- If approved → `git commit` with conventional format (`type(scope): description`) — include issue link but avoid keywords that auto-close (e.g., use "issue https://..." not "fixes https://..." or "closes https://...")

**Step 8e: Push & Version (after commit)**

- **MUST ask user using question tool:**
  ```
  Commit done. Push a version?
  1. Yes → git push + version bump
  2. No → skip version bump, continue workflow
  ```
- If "Yes" → `git push` and bump version
- If "No" → Skip version bump and changelogs, continue to Step 8f

**Step 8f: Finish Workflow (always runs)**

- Run `on_complete` hook if present
- Announce: "Workflow complete."

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
- One-line tag `[Step: <name>] [Rule: <#>]` between actions
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
