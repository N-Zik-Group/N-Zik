# Security Rules

**Version:** 1.5.0 | **Last updated:** 2026-09-26

## Secrets & API Keys

- NEVER commit secrets, API keys, or tokens
- Use `local.properties` for local secrets (gitignored)
- Use `BuildConfig` fields for build-time secrets
- API-key resolution chain (see `build.gradle.kts`, Last.fm as reference): `local.properties` → committed `build.properties` (public values only — see exception below) → environment variables (e.g. `LASTFM_API_KEY`/`LASTFM_API_SECRET`) → empty string. Mirror this chain when adding a new API key
- NEVER log sensitive data (tokens, passwords, user data)

> **Documented exception — `N-Zik/build.properties`:** the Last.fm API key/secret in this file are **intentionally committed** (F-Droid builds from tagged source without environment variables, so public values must live in source — official F-Droid practice; they are already embedded in every released APK, so committing them exposes nothing new). The random canary entries (10-char random names, e.g. `Qx7Kd2Wm9P=…`) are **decoy values, NOT real keys**. Do NOT flag `build.properties` as a secret leak, and do NOT move, gitignore or "clean" these values. The `shazam_proxy_api_key` is intentionally left empty in public builds (kept out on purpose per the file's comments). A real secret (keystore, signing key, private token) appearing anywhere else still triggers the HALT rules below.

## Input Validation

- Validate all user input before processing
- Sanitize data before displaying in UI
- Room handles parameterized queries automatically
- Validate URLs before opening in browser/webview

## Question Tool Input Validation

- All user input arrives via question tool responses
- Validate URLs before opening (never auto-open)
- Validate file paths (prevent path traversal)
- Reject empty/whitespace-only responses for required fields
- Trim and normalize text inputs

## Sensitive Data Storage

- Use `EncryptedSharedPreferences` for sensitive local storage
- Clear sensitive data when user logs out
- Use HTTPS for all network communications
- Do not store credentials in plain text

> **Deliberate exception:** `res/xml/network_security_config.xml` permits cleartext for `localhost` / `127.0.0.1` only (Listen Together local dev servers; production uses `wss://`). Do NOT "fix" this exception — it is intentional.

## Signing & Keystore

- NEVER commit a keystore file (`.jks`, `.keystore`) or its passwords, under any build variant
- Release/Beta/Foss signing credentials come from GitHub Actions secrets only (CI) — NEVER hardcoded in `build.gradle.kts`; `local.properties` is NOT consulted for signing in this repo
- Only the `debug` build type uses debug signing locally; `beta`/`foss`/release APKs are unsigned locally and signed in CI via GitHub Actions secrets — do NOT add a local signing config for non-debug build types without explicit instruction
- NEVER modify signing config blocks (`signingConfigs {}`) without explicit instruction — a wrong signing config can invalidate the Play Store / F-Droid update chain (mismatched signature blocks app updates for all existing users)
- If a keystore or signing secret is found in a diff, commit, or log output → HALT immediately, treat as a leaked secret (same escalation as "Secrets found in code" below)

## License Checks

When using code from external sources (web, GitHub, StackOverflow, AI):

1. Verify the license before using it
2. Open-source (MIT, Apache) = acceptable
3. Copyleft (GPL, AGPL) = check implications before using in Android app
4. Closed-source/proprietary = NEVER acceptable
5. Always cite source and license in a comment

## HALT IMMEDIATELY IF:

| Scenario                   | Action                                                              |
| -------------------------- | ------------------------------------------------------------------- |
| Secrets found in code      | HALT immediately, remove secrets, add to .gitignore, report to user |
| License violation detected | HALT, remove code, report to user with violation details            |
| SQL injection risk         | HALT, verify Room parameterized queries, report                     |
| Hardcoded credentials      | HALT, remove credentials, use BuildConfig or local.properties       |
| Insecure network call      | HALT, switch to HTTPS, verify certificate pinning                   |
| User data leak             | HALT, identify leak source, report to user (fix only after user confirmation)  |
