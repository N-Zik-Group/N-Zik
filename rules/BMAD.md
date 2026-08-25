# BMAD Technical Reference

**Version:** 1.2.0 | **Last updated:** 2026-08-24

**MANDATORY: Read this file before executing any BMAD skill.**

---

## Installation Location

**`{project-root}`** = the workspace root directory containing `_bmad/` and `.agents/`. This is a literal placeholder — the agent must resolve it at runtime by finding the directory that contains `_bmad/` or `.agents/`.

> **Important for this project:** `_bmad/` and `.agents/` live at the **parent** of `N-Zik/`. If your CWD is `N-Zik/`, go **up one level** to find `{project-root}`. The actual path is `../` relative to `N-Zik/`.

**Skills are NOT in `_bmad/`** — they are in IDE-specific directories at `{project-root}`:

| IDE                                                    | Skills Directory                 |
| ------------------------------------------------------ | -------------------------------- |
| Cursor, Copilot, Codex, OpenCode, Windsurf, Gemini CLI | `{project-root}/.agents/skills/` |
| Claude Code                                            | `{project-root}/.claude/skills/` |
| Google Antigravity                                     | `{project-root}/.agent/skills/`  |

**`_bmad/` contains:** config, scripts, modules, rendered outputs — NOT skills.

---

## Installation Structure

```
_bmad/
├── _config/                    # Installer metadata (manifest.yaml, CSVs)
├── config.toml                 # Central config — TEAM layer
├── config.user.toml            # Central config — USER layer
├── custom/                     # Human-authored overrides
│   ├── config.toml             # Team overrides (committed)
│   └── config.user.toml        # User overrides (gitignored)
├── scripts/                    # resolve_config.py, resolve_customization.py, memlog.py
├── core/config.yaml            # Core module config
├── <module>/config.yaml        # Per-module config (bmm, cis, bmb, gds, tea, bmad-loop)
└── render/                     # Rendered skill outputs (runtime)
```

## Config Resolution (4-Layer TOML Merge)

| Priority    | Path                            | Owner     | Committed?         |
| ----------- | ------------------------------- | --------- | ------------------ |
| 1 (lowest)  | `_bmad/config.toml`             | Installer | Yes                |
| 2           | `_bmad/config.user.toml`        | Installer | Yes                |
| 3           | `_bmad/custom/config.toml`      | Human     | Yes                |
| 4 (highest) | `_bmad/custom/config.user.toml` | Human     | No (\*.gitignored) |

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

> **Path tip:** If running from `N-Zik/`, `{project-root}` resolves to the parent directory. Use `..` or resolve the absolute path to `N-Zik-Projet/` before running scripts.

**`{skill-root}`** = `{project-root}/{target_dir}/{skill-name}` where `target_dir` depends on your IDE:
- **Cursor/Copilot/Codex/OpenCode/Windsurf:** `{project-root}/.agents/skills/{skill-name}`
- **Claude Code:** `{project-root}/.claude/skills/{skill-name}`
- **Google Antigravity:** `{project-root}/.agent/skills/{skill-name}`

**If script fails** → manually read 3 files in order and merge:

1. `{skill-root}/customize.toml` (defaults)
2. `{project-root}/_bmad/custom/{skill-name}.toml` (team)
3. `{project-root}/_bmad/custom/{skill-name}.user.toml` (personal)

**Merge rules:** Scalars override, tables deep-merge, keyed arrays merge by `code` or `id`, other arrays append. **No removal mechanism** — to suppress a default, override by `code` with no-op.

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

**Types:** decision, constraint, capability, assumption, question, direction, note, event

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
Copilot: `.github/agents/` with `LOAD the FULL {path}/SKILL.md` format.

### OpenCode — Direct Skill Loading

To load a skill directly in OpenCode (bypassing command files), use the `@` prefix with the skill path:
```
@skills/bmad-build
```
This triggers the agent to read and follow the SKILL.md from `.agents/skills/bmad-build/`.

## Skill Naming

- Agents: `bmad-agent-{name}.md` (core) or `bmad-agent-{module}-{name}.md`
- Workflows: `bmad-{module}-{name}.md`

---

## Documentation

- https://docs.bmad-method.org/
- https://github.com/bmad-code-org/BMAD-METHOD
