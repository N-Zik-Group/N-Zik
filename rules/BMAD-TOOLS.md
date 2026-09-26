# BMAD IDE Skill Directories

**Version:** 1.5.0 | **Last updated:** 2026-09-26

Reference only — read only when configuring IDE tooling. Skim for your tool, skip the rest. Full reference: upstream BMAD-METHOD repo, `tools/installer/ide/platform-codes.yaml` (NOT present in this workspace).

## Preferred Tools

⭐ = preferred (asked first). **Preferred ≠ installed:** the IDEs in the installer manifest (`_bmad/_config/manifest.yaml`) are `claude-code`, `antigravity`, `opencode` — Copilot is preferred but NOT in the manifest (it still loads the shared `.agents/skills/` dir); Claude Code is installed but listed under "All Other Tools".

| Tool                     | Skills dir        | Global dir                      | Commands dir          |
| ------------------------ | ----------------- | ------------------------------- | --------------------- |
| **OpenCode** ⭐           | `.agents/skills/` | `~/.agents/skills/`             | `.opencode/commands/` |
| **GitHub Copilot** ⭐     | `.agents/skills/` | `~/.agents/skills/`             | `.github/agents/` (upstream convention — none installed here) |
| **Google Antigravity** ⭐ | `.agent/skills/`  | `~/.gemini/antigravity/skills/` | —                     |

> **This machine:** only the **project-local** dirs above are actually installed (57 skills each). The `Global dir` column documents the upstream conventions — they are **NOT present here** (no `~/.agents/skills`, no `~/.gemini/antigravity/skills`; `~/.claude/skills` exists but holds only non-BMAD `synced/` skills; a global `~/.bmad/cache` — BMAD's own cache — does exist).

## All Other Tools

| Tool             | Skills dir            | Global dir                          | Commands dir |
| ---------------- | --------------------- | ----------------------------------- | ------------ |
| Claude Code      | `.claude/skills/`     | `~/.claude/skills/`                 | —            |
| Cursor           | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Codex            | `.agents/skills/`     | `~/.codex/skills/`                  | —            |
| Antigravity CLI  | `.agents/skills/`     | `~/.gemini/antigravity-cli/skills/` | —            |
| AdaL             | `.adal/skills/`       | `~/.adal/skills/`                   | —            |
| Sourcegraph Amp  | `.agents/skills/`     | `~/.config/agents/skills/`          | —            |
| Auggie           | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| IBM Bob          | `.bob/skills/`        | `~/.bob/skills/`                    | —            |
| Cline            | `.cline/skills/`      | `~/.cline/skills/`                  | —            |
| CodeWhale        | `.codewhale/skills/`  | `~/.codewhale/skills/`              | —            |
| CodeBuddy        | `.codebuddy/skills/`  | `~/.codebuddy/skills/`              | —            |
| Command Code     | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Snowflake Cortex | `.cortex/skills/`     | `~/.snowflake/cortex/skills/`       | —            |
| Crush            | `.agents/skills/`     | `~/.config/agents/skills/`          | —            |
| Factory Droid    | `.factory/skills/`    | `~/.factory/skills/`                | —            |
| Firebender       | `.firebender/skills/` | `~/.agents/skills/`                 | —            |
| Gemini CLI       | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Block Goose      | `.agents/skills/`     | `~/.config/agents/skills/`          | —            |
| Hermes Agent     | `.agents/skills/`     | `~/.hermes/skills/`                 | —            |
| iFlow            | `.iflow/skills/`      | `~/.iflow/skills/`                  | —            |
| Junie            | `.junie/skills/`      | `~/.junie/skills/`                  | —            |
| KiloCoder        | `.agents/skills/`     | `~/.kilocode/skills/`               | —            |
| Kimi Code        | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Kiro             | `.kiro/skills/`       | `~/.kiro/skills/`                   | —            |
| Kode             | `.kode/skills/`       | `~/.kode/skills/`                   | —            |
| Mistral Vibe     | `.agents/skills/`     | `~/.vibe/skills/`                   | —            |
| Mux              | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Neovate          | `.neovate/skills/`    | `~/.neovate/skills/`                | —            |
| Ona              | `.ona/skills/`        | —                                   | —            |
| OpenClaw         | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| OpenHands        | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Pi               | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Pochi            | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Qoder            | `.qoder/skills/`      | `~/.qoder/skills/`                  | —            |
| QwenCoder        | `.qwen/skills/`       | `~/.qwen/skills/`                   | —            |
| Replit Agent     | `.agents/skills/`     | —                                   | —            |
| Roo Code         | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Rovo Dev         | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Trae             | `.trae/skills/`       | —                                   | —            |
| Warp             | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Windsurf         | `.agents/skills/`     | `~/.agents/skills/`                 | —            |
| Zencoder         | `.zencoder/skills/`   | `~/.zencoder/skills/`               | —            |

⭐ = Preferred (shown first during install)

**Doc:** https://docs.bmad-method.org/reference/commands/
