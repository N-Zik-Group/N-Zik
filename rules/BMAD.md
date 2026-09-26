# BMAD Technical Reference

**Version:** 1.5.0 | **Last updated:** 2026-09-26

**MANDATORY: Read this file before executing any BMAD skill.**

---

## Installation Location

**`{project-root}`** = the workspace root directory containing both `_bmad/` and `.agents/` (if only one exists, prefer `_bmad/`). This is a literal placeholder — the agent must resolve it at runtime by finding that directory.

> **Important for this project:** `_bmad/` and `.agents/` live at the **parent** of `N-Zik/`. If your CWD is `N-Zik/`, go **up one level** to find `{project-root}`. The actual path is `../` relative to `N-Zik/`.

> **Re-installation is NOT defined in this workspace** — no installer command is documented in these rules. If a re-install is needed (see RECOVERY.md), HALT and ask the user for the re-installation procedure; the upstream documentation is the reference (https://docs.bmad-method.org/).

**Installed version** (per `_bmad/_config/manifest.yaml`): BMAD **6.12.0** (installed 2026-09-11, last updated 2026-09-14) — modules: `core` 6.12.0, `bmm` 6.12.0, `bmb` v2.2.2, `cis` v0.3.2, `tea` v1.26.0, `bmad-loop` v0.11.1; IDEs installed: `claude-code`, `antigravity`, `opencode`. **57 skills** are installed in each IDE directory (`.agents/skills/`, `.agent/skills/`, `.claude/skills/`). Stray non-BMAD files at the `_bmad/` root (e.g. `lt-1.png`) are NOT part of the installation — do not treat them as config.

**Skills are NOT in `_bmad/`** — they are in IDE-specific directories at `{project-root}`:

| IDE                                                    | Skills Directory                 |
| ------------------------------------------------------ | -------------------------------- |
| Cursor, Copilot, Codex, OpenCode, Windsurf, Gemini CLI | `{project-root}/.agents/skills/` |
| Claude Code                                            | `{project-root}/.claude/skills/` |
| Google Antigravity                                     | `{project-root}/.agent/skills/`  |

**`_bmad/` contains:** config, scripts, modules, rendered outputs — NOT skills.

> **`_bmad-output/`** (workspace root, sibling of `_bmad/` — NOT part of the installation) is BMAD's output directory: planning/implementation/test artifacts, `problem-solution-*.md` reports, and `DONTREAD/` (archive of older outputs — respect it, do not delete or reorganize). It is separate from the protected `_bmad/` installation.

---

## Installation Structure

```
_bmad/
├── _config/                    # Installer metadata (manifest.yaml, CSVs)
├── config.toml                 # Central config — installer-managed (team overrides live in custom/config.toml)
├── config.user.toml            # Central config — USER layer
├── custom/                     # Human-authored overrides
│   ├── config.toml             # Team overrides (committed; currently comment-only)
│   ├── config.user.toml        # User overrides (gitignored via custom/.gitignore)
│   └── .gitignore              # Ignores *.user.toml
├── scripts/                    # resolve_config.py, resolve_customization.py, render_skill.py, memlog.py, config_utils.py
├── core/config.yaml            # Core module config
├── <module>/config.yaml        # Per-module config (bmm, cis, bmb, tea, bmad-loop; module dirs also carry module-help.csv / v6-shims/)
└── render/                     # Rendered skill outputs (runtime)
```

## Config Resolution (4-Layer TOML Merge)

| Priority    | Path                            | Owner     | Committed?         |
| ----------- | ------------------------------- | --------- | ------------------ |
| 1 (lowest)  | `_bmad/config.toml`             | Installer | Yes                |
| 2           | `_bmad/config.user.toml`        | Installer | Yes                |
| 3           | `_bmad/custom/config.toml`      | Human     | Yes                |
| 4 (highest) | `_bmad/custom/config.user.toml` | Human     | No (\*.gitignored) |

> In this project, `_bmad/` lives at the workspace root, OUTSIDE the git repo (`N-Zik/`). The "Committed?" column describes the upstream BMAD layout — never `git add` anything from `_bmad/` into the N-Zik repo.

**Merge rules:** Scalars override, tables deep-merge, keyed arrays merge by key, other arrays append.

## Skill Customization (3-Layer TOML Merge)

| Priority    | Path                                  | Owner | Committed?         |
| ----------- | ------------------------------------- | ----- | ------------------ |
| 1 (lowest)  | Skill's `customize.toml`              | Skill | Yes (read-only)    |
| 2           | `_bmad/custom/{skill-name}.toml`      | Team  | Yes                |
| 3 (highest) | `_bmad/custom/{skill-name}.user.toml` | User  | No (\*.gitignored) |

**Invocation:**

```
uv run {project-root}/_bmad/scripts/resolve_customization.py --skill {skill-root} --key agent
uv run {project-root}/_bmad/scripts/resolve_customization.py --skill {skill-root} --key workflow
```

(`--project-root {project-root}` is an optional accepted flag — the CIS skills pass it in their activation sequence.)

> **Path tip:** If running from `N-Zik/`, `{project-root}` resolves to the parent directory. Use `..` or resolve the absolute path to the workspace root (the directory containing `_bmad/`) before running scripts.

**`{skill-root}`** = `{project-root}/{target_dir}/{skill-name}` where `target_dir` depends on your IDE:
- **Cursor/Copilot/Codex/OpenCode/Windsurf:** `{project-root}/.agents/skills/{skill-name}`
- **Claude Code:** `{project-root}/.claude/skills/{skill-name}`
- **Google Antigravity:** `{project-root}/.agent/skills/{skill-name}`

**If script fails** → manually read 3 files in order and merge:

1. `{skill-root}/customize.toml` (defaults)
2. `{project-root}/_bmad/custom/{skill-name}.toml` (team)
3. `{project-root}/_bmad/custom/{skill-name}.user.toml` (personal)

**Merge rules:** Scalars override, tables deep-merge, arrays of tables keyed by `code` or `id` → the matching entry is REPLACED (not field-merged) by the higher-priority one, unmatched entries keep; other arrays append. **No removal mechanism** — to suppress a default, override by `code` with no-op.

**Key files:**

- `persistent_facts` — Rules that travel with the agent into every workflow (file refs loaded, literal text kept verbatim)
- `activation_steps_prepend` — Runs BEFORE greeting
- `activation_steps_append` — Runs AFTER greeting, BEFORE menu

**Critical rules:**

- NEVER edit `customize.toml` — it is overwritten on every update. All customization goes in `_bmad/custom/`.
- Override files must be sparse — only include fields being changed.
- `agent.name` and `agent.title` are read-only — overrides have no effect.
- File references use `{project-root}` prefix.
- Present output in `{communication_language}` from resolved config.
- Prefix all messages with `{agent.icon}` throughout session.

**Full IDE skill directories table:** See `rules/BMAD-TOOLS.md`

## memlog.py — Session Memory System

Some skills use `memlog.py` for append-only session memory.

**Invocation:**

```
uv run {project-root}/_bmad/scripts/memlog.py init --workspace {doc_workspace} --field topic="<topic>"
uv run {project-root}/_bmad/scripts/memlog.py append --workspace {doc_workspace} --type <type> --text "<text>"
uv run {project-root}/_bmad/scripts/memlog.py set --workspace {doc_workspace} --key status --value complete
```

**Types:** free-form (`--type` is not validated by the script) — use the vocabulary of the skill that calls memlog (common values: decision, direction, assumption, question, note, event)

**Rules:**

- NEVER write memlog files by hand — use the script only
- All writes are atomic and append-only
- The `.memlog.md` file is the run's canonical memory and audit trail

## Agent Icon Prefix

For agent skills, prefix ALL messages with `{agent.icon}` throughout the ENTIRE session — not just the greeting.

**Example:** If icon is "🎯", every message starts with "🎯 ..."

## resolve_config.py — Central Config Resolution

Some skills (bmad-help, bmad-advanced-elicitation) use `resolve_config.py` for project-wide configuration.

**Invocation:**

```
uv run {project-root}/_bmad/scripts/resolve_config.py --project-root {project-root}
```

**This is different from `resolve_customization.py`:**

- `resolve_customization.py` → per-skill config (3-layer merge)
- `resolve_config.py` → central project config (4-layer merge)

## Command Pointer Files

OpenCode: `.opencode/commands/` with `@skills/{canonicalId}` format.
Copilot: no command pointer files are currently installed in this workspace (the upstream `.github/agents/` convention does not exist here) — load a skill by reading its `SKILL.md` directly (instruction format: `LOAD the FULL {path}/SKILL.md`).

### OpenCode — Direct Skill Loading

To load a skill directly in OpenCode (bypassing command files), use the `@` prefix with the skill path:
```
@skills/bmad-build
```
This triggers the agent to read and follow the SKILL.md from `.agents/skills/bmad-build/`.

## Skill Naming

- Installed layout: each skill is a directory `{ide-dir}/skills/{skill-name}/` with a `SKILL.md` (+ step/template files)
- Upstream naming (reference only, NOT the installed layout): agents `bmad-agent-{name}.md`, workflows `bmad-{module}-{name}.md`

---

## Documentation

- https://docs.bmad-method.org/
- https://github.com/bmad-code-org/BMAD-METHOD
