# Error Recovery & Rollback Rules

**Version:** 1.2.0 | **Last updated:** 2026-08-24

## Build Failure Recovery

1. Read error messages carefully
2. Fix the first error (often cascading)
3. Rebuild
4. **HALT after 3 failed attempts** — report to user with full error log

```
BUILD FAILURE ESCALATION:
Attempt 1 → Fix obvious issue → Rebuild
Attempt 2 → Research error → Fix → Rebuild
Attempt 3 → HALT → Report to user with error log
```

## BMAD Skill Failure

If a BMAD skill fails or gets stuck:

1. **Skill not found** → Search `{project-root}/.agents/skills/` (or `.claude/skills/` for Claude Code, `.agent/skills/` for Antigravity)
2. If still not found → HALT, inform user, suggest re-running BMAD installer
3. **SKILL.md malformed** → HALT, report error, suggest `bmad-module-builder` to rebuild
4. **Skill execution error** → Fallback to `bmad-build` for implementation tasks
5. **Agent stuck in loop** → HALT after 5 iterations, ask user

## Database Migration Failure

1. **NEVER** modify an already-deployed migration
2. If migration fails → HALT immediately
3. Do NOT commit migration code
4. Report error to user
5. Create new migration to fix (never edit old one)

```
MIGRATION FAILURE:
1. HALT — stop all DB operations
2. Log the exact error
3. Report to user
4. If data corruption suspected → do NOT touch DB
5. Create new migration to reverse changes if needed
```

## Code Changes Break Existing Features

1. Revert uncommitted changes: `git checkout -- <file>`
2. Revert uncommitted work: `git stash`
3. Identify what broke
4. Fix incrementally, testing after each change
5. If unable to fix → HALT, report to user with diagnosis

## Network / Dependency Errors

1. Check internet connection
2. Verify Maven/Gradle repositories are accessible
3. Try `./gradlew --refresh-dependencies`
4. If proxy issue → HALT, inform user
5. If repository down → HALT, suggest using cached dependencies

## Gradle Wrapper Issues

If `./gradlew` fails or is corrupted:

1. Check `gradlew` and `gradle/wrapper/gradle-wrapper.jar` exist
2. Try `./gradlew --version` to verify wrapper works
3. If corrupted → HALT, suggest re-cloning or re-downloading wrapper
4. Never modify `gradle-wrapper.properties` without explicit instruction

## KMP Compilation Issues

1. Check `commonMain` for Android-specific imports
2. Verify `expect/actual` declarations match
3. Check source set configuration
4. If unresolved → HALT, report with full compilation output

## Corrupted \_bmad/ Directory

1. **NEVER** manually edit `_bmad/` internals
2. Delete `_bmad/` and re-run installer
3. Verify with `bmad-bmb-setup` skill

## Loop Detection

If you notice yourself repeating the same action:

1. Stop immediately
2. Count iterations — HALT at 5
3. Report to user: "I'm stuck in a loop doing [X]. Please advise."
4. Wait for user input before continuing

## General Rollback

- `git log --oneline -5` — find safe rollback point
- `git reset --soft HEAD~1` — undo last commit but keep changes staged (only if not pushed)
- **NEVER** force push without explicit user instruction
- **NEVER** delete committed history
