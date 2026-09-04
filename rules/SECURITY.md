# Security Rules

**Version:** 1.2.0 | **Last updated:** 2026-08-24

## Secrets & API Keys

- NEVER commit secrets, API keys, or tokens
- Use `local.properties` for local secrets (gitignored)
- Use `BuildConfig` fields for build-time secrets
- NEVER log sensitive data (tokens, passwords, user data)

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

## Signing & Keystore

- NEVER commit a keystore file (`.jks`, `.keystore`) or its passwords, under any build variant
- Release/Beta/Foss signing credentials are referenced via `local.properties` or CI secrets (GitHub Actions secrets) — NEVER hardcoded in `build.gradle.kts`
- `debug` and `Beta` build types may use debug signing — this is expected, do NOT "fix" it into release signing without explicit instruction
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
| User data leak             | HALT, identify leak source, report to user, fix immediately         |
