# AGENTS.md (Artha, formerly Hisaab)

Guide for coding agents working on Hisaab. Keep answers and edits short.

## First
- Read `doc/PLAN.md`. Add every new user request there as one line; tick it when done.
- Data stays on the phone. Never add uploads, analytics or remote logging.
- Commit and push only when the user asks.

## App
Offline-first Android personal finance app (Kotlin, Jetpack Compose, Material3, Hilt, Room).
Reads bank SMS, payment-app notifications, email (IMAP app password or Gmail) and PDF statements.

| Module | Holds |
|---|---|
| `parser-core` | Pure Kotlin. SMS/email parsing (`bank/`, `extract/`, `rules/`, `merchant/`), statements (`statement/`). JUnit 5 tests with corpora. |
| `shared` | Room DB (`db/`), repositories, insights, `Subcategories`. Schemas in `shared/schemas/`. |
| `email` | IMAP + Gmail sync, MIME, PDF text, `StatementProcessor`, statement passwords. |
| `app` | Compose UI (`ui/<feature>/`), settings (DataStore), workers, navigation (`ui/nav/HisaabNavHost.kt`). |

## Conventions
- UI copy: professional, concise. No explanatory paragraphs.
- Theme tokens in `ui/theme/` (own palette, no dynamic colour). Top bars use `clearTopBar()`.
- Icons: `ui/components/IconLibrary.kt`. Brand logos: `app/src/main/assets/logos/*.webp` via `AppLogos`.
- Tab sections are customizable: register new sections in `settings/TabLayout.kt` (`TabLayouts.DEFAULTS`).
- DB change = bump `HisaabDatabase.VERSION`, add a `MIGRATION_n_m`, copy the generated schema JSON into `shared/schemas/`.
- New parser behaviour gets a test (`parser-core/src/test/.../FeedbackCasesTest.kt` for real user messages).
- Match surrounding code: KDoc on public pieces, no comment noise.

## Build (Windows)
The repo is on OneDrive, so builds run in a mirror outside it:
1. Mirror: `robocopy <repo> C:\Users\11624\hisaab_build /MIR /XD build .gradle .kotlin .idea .git`
2. Build: in the mirror, with `JAVA_HOME=C:\Users\11624\tools\jdk`:
   `./gradlew :parser-core:test :shared:testDebugUnitTest :app:assembleRelease`
3. Sign with the release key (apksigner, v3 rotation lineage) to `apk/Hisaab-<version>.apk` (gitignored).

## Release
- Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- Push to `main`; `.github/workflows/release.yml` publishes a GitHub release when `versionName` is new
  (needs secrets `HISAAB_RELEASE_KEYSTORE`, `HISAAB_KEYSTORE_PASSWORD`, `HISAAB_DEBUG_KEYSTORE`, `HISAAB_KEY_LINEAGE`).
- Commit messages: `feat: vX.Y.Z - <short summary>`.
