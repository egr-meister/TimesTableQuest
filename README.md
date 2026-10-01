# TimesTable Quest

Offline multiplication-table learning app (1 × 1 … 12 × 12) with a standalone calculator.
Native Android: Kotlin, Jetpack Compose (customized Material 3), Room, DataStore. Interface language: English.

> **Verification status:** see [VERIFICATION.md](VERIFICATION.md). Domain/data unit tests (69) pass locally and the
> full Kotlin source type-checks against AndroidX/Compose jars, but the Gradle/AGP build, release signing,
> lint and on-device checks have **not** been run yet — they are marked *pending* until the first CI run and a
> device session.

---

## Features

| Area | What it does |
|---|---|
| **Multiplication map** (home) | 12 row tiles ×1…×12 in an adaptive grid (2 columns on narrow phones, more on wide screens). Each tile: row label, "✓ n of 12 correct", "★ n confident", a **Study** button, tap = open row. A 12-segment border shows per-fact progress; the same data is always given in text. A slim **Daily 10** strip sits above the map; **Mixed Practice** and **Needs Practice** form a lavender rail below it. All rows are open from the start. |
| **Row screen** | "The 7 times table", Study / Check buttons, row summary, 12 fact tiles (`7 × 4 = 28` + status), and the row's Needs Practice facts. Tapping a fact opens its explanation. |
| **Study mode** | Unscored, untimed. Previous/next fact, big equation, equal-groups explanation, repeated addition or split strategy, comparison with the previous fact, commutativity note, optional dot array (or compact area model for large facts), and **Hide answer** for self-checking. Viewing is stored only as a study visit. |
| **Row check** | 10 distinct columns from one row, least-recently-asked first. "Question 3 of 10", large expression, 4 options, optional Hint. First answer is final → correct answer + explanation + **Next** (no auto-advance, no timer). |
| **Mixed practice** | Choose rows (default: all / parent default). 10 questions spread as evenly as possible, no duplicate ordered facts, both orientations of a pair avoided when alternatives exist. |
| **Needs Practice** | Facts missed in practice. Review sessions of up to 10 (shorter if fewer, never padded). Correct retry → leaves the queue; wrong retry → stays. Original answers are never changed. |
| **Daily 10** | One persistent set per local date, generated and stored (questions + option order) before display. Resume any time on that date. Results: correct / 10, accuracy, row breakdown, "Review mistakes", "Back to map". No streaks, no penalties. |
| **Progress** | All rows: correct-at-least-once /12, confident /12, accuracy, needs-practice count, with definitions. |
| **History** | Latest 100 non-daily sessions + 90 daily sets; each opens a read-only results/question review. |
| **Calculator** | Separate tab. BigDecimal, one binary operation at a time, history of the latest 50 successful calculations. |
| **Settings (grown-ups)** | Daily rows, default mixed rows, reduced decorative animation, reset one row, reset all, clear calculator history, clear all local data, privacy information. Destructive actions sit behind an adult-entry question (a two-digit multiplication typed into a field — an accidental-tap safeguard, not authentication). |

Not included by design: accounts, backend, Firebase, ads, analytics, payments, cloud sync, notifications, rankings,
external links, camera/mic/location/contacts, coins, chests, random rewards or prizes.

## Architecture

Single `:app` module, manual DI (`AppContainer`), MVVM with `ViewModel` + `StateFlow`, lifecycle-aware collection.

```
com.timestablequest.app
├── data/local         Room entities, DAOs, AppDatabase (+ Migrations), PreferencesStore (DataStore)
├── data/repository    PracticeService (session lifecycle, scoring, review, daily, retention, resets),
│                      PracticeStore (persistence boundary) + RoomPracticeStore, repositories, mappers
├── domain/facts       Fact (ordered row × column), Facts (144 facts, row helpers)
├── domain/generation  FactSelector (row/mixed/daily/review selection), AnswerChoices, Explanations
├── domain/progress    Outcome window, ConfidenceRules, Scoring, RowProgress, Clock/DateProvider
├── domain/review      ReviewQueue ordering
├── domain/calculator  CalculatorEngine (BigDecimal)
└── ui/                map, row, study, practice, results, progress, history, calculator, settings, theme, common
```

Generation, scoring, confidence and review rules are pure Kotlin with injected `Clock`, `DateProvider` and
`Random`, and are unit-tested without Android. Every scoring write (answer + fact stats + session status) and every
reset runs in one Room transaction (`withTransaction`). All database work runs on `Dispatchers.IO`.

## Toolchain

| Component | Version |
|---|---|
| JDK | 17 (Temurin in CI; any JDK 17–21 locally) |
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.13.0 |
| Kotlin / Compose compiler plugin | 2.2.20 |
| KSP | 2.2.20-2.0.3 |
| Compose BOM | 2025.09.01 |
| Room | 2.8.1 |
| Navigation Compose | 2.9.5 |
| Lifecycle | 2.9.4 |
| Activity | 1.11.0 |
| DataStore | 1.1.7 |
| Core / SplashScreen | 1.17.0 / 1.0.1 |
| Coroutines | 1.10.2 |
| **compileSdk / targetSdk / minSdk** | **36 / 36 / 26** |

All versions are pinned in `gradle/libs.versions.toml`. Every directly used library is declared directly in
`app/build.gradle.kts`. SDK levels must not be lowered to work around build failures.

**Android 16 (API 36) notes:** edge-to-edge is enforced (handled with `enableEdgeToEdge()` + Scaffold/inset
handling, horizontal cutout insets applied at the root); predictive Back is enabled
(`android:enableOnBackInvokedCallback="true"`, Navigation Compose back stack, no custom `onBackPressed`);
orientation and resizability are not restricted (no `screenOrientation`, no `resizeableActivity=false`), so large
screens and windowing work; no immersive mode and no keep-screen-on.

## Build commands

```bash
./gradlew testDebugUnitTest          # unit tests
./gradlew assembleDebug              # debug APK, no release credentials needed
./gradlew lintRelease                # release lint
./gradlew assembleRelease bundleRelease   # signed release APK + AAB (credentials required)
```

Outputs:

- APK: `app/build/outputs/apk/release/app-release.apk` — for local install/verification
- AAB: `app/build/outputs/bundle/release/app-release.aab` — **the only file to upload to Google Play**
- R8 mapping (when minify is enabled): `app/build/outputs/mapping/release/mapping.txt`

## Multiplication facts and answer choices

- 144 ordered facts `row × column`, both 1–12; products 1–144, exact `Int` arithmetic.
- The row factor owns the fact: 3 × 7 → row 3, 7 × 3 → row 7. Progress is never awarded to the reversed fact.
- Each scored question has 4 distinct positive integer options (1–180), exactly one correct, shuffled positions.
  Distractors: adjacent column `r × (c ± 1)`, adjacent row `(r ± 1) × c`, adding instead of multiplying `r + c`,
  nearby/counting errors `p ± 1, p ± 2, p ± 10`. Out-of-range values, duplicates and accidental correct answers
  are filtered; a bounded fallback (`p ± 1, p ± 2, …` inside 1–180) fills any gap. No decimals/NaN/∞ possible.
- Hints and explanations are generated from the fact: equal groups ("7 × 4 = 28. Four groups of 7 make 28."),
  repeated addition (c ≤ 5), split strategy (6–10 → 5 + rest, 11–12 → 10 + rest: "8 × 6 = 48. Split 6 into 5 and 1:
  8 × 5 = 40, then add 8."), one-more-group comparison ("7 × 5 is 7 more than 7 × 4"), commutativity as an idea only.
  Hints never state the product.

## Progress, confidence and review — definitions

Only **original answers** count: the first answer to a question in a row check, mixed practice or Daily 10.
Study browsing and Needs Practice retries never count.

| Metric | Definition |
|---|---|
| Not Practiced | no original answers |
| Practicing | ≥ 1 original answer, Confident criteria not met |
| **Confident** | the latest three original answers are correct **without hints** and come from **≥ 2 distinct sessions**. An incorrect answer returns the fact to Practicing. An app status, not a claim of mastery. |
| Correct at least once | original answers only, out of 12 per row (hint-assisted correct counts as correct) |
| Row accuracy | correct original answers ÷ all original answers in the row × 100 (rounded). No answers → "Not practiced yet". |
| Needs Practice | a fact enters after an incorrect original answer; leaves after a correct review retry; a later incorrect original answer reopens it. |

Per fact (`fact_progress`): original attempts, original correct, latest answer, latest-three outcome window
(`sessionId:C|H|X`), last practiced, last incorrect, last reviewed, review-needed, times asked, last asked.
Review retries are stored separately in `review_attempts`.

Review queue order: (1) queued facts whose latest original answer is still incorrect, (2) more recent incorrect
answers first, (3) least recently reviewed first. Empty state: "No review questions right now. Try a row or mixed
practice."

## Daily 10 and date handling

- Key = device local date (`LocalDate.now()`), stored as ISO `yyyy-MM-dd`; `practice_sessions.dailyDate` is unique.
- Selection: up to 4 unresolved review facts from the selected daily rows, then balanced low-exposure facts across
  those rows, no duplicate ordered facts. All 10 questions and their option order are stored before display.
- Reopening on the same date resumes the same set; returning to a date that already has a set reuses it.
- If midnight passes while a question is shown, that question is answered in its original set; on **Next** the app
  offers the new date's set. Earlier unfinished daily sets become *Incomplete*; their unanswered questions are not
  errors.
- Each answer stores `answeredLocalDate`, so later time-zone changes do not rewrite historical totals.
- Changing daily rows applies to the next date's set. No online time check; deliberate clock changes are not resisted.

## Sessions, results, retention

- One unfinished non-daily session at a time (separate from the daily set). Starting another offers **Resume** or
  **End it and start new**. Visible question, option order, answer, feedback and hint use are persisted.
- Ending early keeps answered questions; unanswered ones are excluded from accuracy; sessions with no answers are
  discarded.
- Retention: latest 100 finished non-daily sessions and latest 90 daily sets (plus today's active one). Pruning removes
  only session/question records — lifetime fact statistics and the Needs Practice queue are never reduced.
- Reset row: clears that row's stats, review queue and study visits, ends active sessions containing the row
  (discarding ones with no answers). Saved results stay as read-only history unless "also delete saved sessions"
  is ticked. Reset all works the same for every row.

## Calculator limits and rounding

Digits, decimal point, + − × ÷, =, C, ⌫, ± (negative operands), history. One binary operation at a time; no
percent, parentheses, scientific functions or expression parsing. `BigDecimal` arithmetic; max |operand| and
|result| = 1,000,000; ≤ 6 fractional digits per operand; division rounded to 6 places `HALF_UP`; trailing zeros
removed; rounded results marked "≈". Multiple decimal points are prevented, leading zeros normalized, repeated
**=** does not repeat the calculation. Division by zero and out-of-range results show friendly errors and are not
added to history. Latest 50 successful calculations are kept. Calculator use never affects multiplication progress.

## Offline, privacy, storage, backup

- Works in airplane mode from first launch. No `INTERNET`, no `ACCESS_NETWORK_STATE`, no runtime permissions,
  no networking libraries.
- Data lives in app-private storage: Room DB `timestablequest.db` and DataStore `timestablequest_prefs`.
- `android:allowBackup="false"`, `fullBackupContent` excludes everything (≤ Android 11) and
  `dataExtractionRules` excludes all domains from cloud backup and device transfer (Android 12+).
- A bundled Privacy screen explains local storage. "Clear all local data" deletes every table and preference after
  the adult-entry confirmation.

## Permission verification

`scripts/check_permissions.sh <apk> [merged-manifest]` dumps permissions with `aapt2` and fails on anything other than
AndroidX's app-private `com.timestablequest.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature-level, added by
`androidx.core`, grants no capability), and always fails on `INTERNET` / `ACCESS_NETWORK_STATE`. CI runs it on the
packaged release APK and prints the merged release manifest.

## Release signing (PKCS12) and GitHub Secrets

`app/build.gradle.kts` defines `signingConfigs.release` with `storeType = "PKCS12"`, loaded from environment
variables (CI) or the git-ignored `keystore.properties` (local). The release build type uses
`signingConfigs.getByName("release")`. If credentials are missing, `validateSigningRelease`, `packageRelease`,
`signReleaseBundle` and `packageReleaseBundle` fail — there is **no** fallback to debug signing. Debug builds need
no credentials.

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64 of the `.p12` file |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `timestablequest` |
| `ANDROID_KEY_PASSWORD` | key password (same as the store password for PKCS12) |

The keystore was generated outside the repository (`../keys/timestablequest/`, with a `secrets.txt` holding the
values above). Never commit it, never print the passwords. Local builds: copy `keystore.properties.example` to
`keystore.properties` and fill it in.

**Upload key vs. app signing key:** with Play App Signing (recommended, default for new apps) this keystore is the
**upload key**. Google generates and keeps the **app signing key** that signs what users install. If the upload
key is lost or leaked, request an upload-key reset in Play Console; the app-signing key is unaffected. Back up the
`.p12` and `secrets.txt` somewhere safe.

## CI (`.github/workflows/android-release.yml`)

1. JDK 17 + Android SDK Platform 36 / build-tools 36.0.0; committed Gradle wrapper (validated).
2. `testDebugUnitTest`, `assembleDebug`, `lintRelease`.
3. Decode the PKCS12 keystore into `$RUNNER_TEMP`.
4. `assembleRelease bundleRelease` (signed).
5. `apksigner verify --print-certs` on the APK; fail on `CN=Android Debug`.
6. `jarsigner -verify` on the AAB and compare its signer SHA-256 with the keystore certificate (a self-signed
   upload certificate is expected and accepted).
7. Release permission check; 16 KB ELF/zip alignment check for APK and AAB; `zipalign -c -P 16`.
8. Upload APK, AAB, mapping (if any), test/lint reports and the exported Room schema.
9. Shred the temporary keystore (`if: always()`).

No emulator test runs in CI. Pull requests run tests, debug build and lint only (no secrets).

## Google Play

Upload **only** `app-release.aab`. Use the APK for `adb install` and local checks.

## 16 KB page-size compatibility

The app has no NDK code, and its declared dependencies (AndroidX, Compose, Room, DataStore, coroutines) are
expected to package **no native `.so` libraries**. This is an expectation, not a verified result: CI's
`scripts/check_elf_alignment.py` inspects every `lib/**.so` in the release APK and AAB (ELF `PT_LOAD` alignment
≥ 16 KB, and 16 KB zip alignment for stored `.so` in the APK) and reports "no native libraries packaged" when
there are none. Record the CI output in VERIFICATION.md. If native libraries ever appear, they must pass this check
and the app must be tested in a 16 KB environment (e.g. a 16 KB-page emulator image) before claiming runtime
compatibility. Targeting API 36 alone does not prove 16 KB compatibility.

## R8 / resource shrinking

Disabled by default (`ttq.minify=false` in `gradle.properties`). Order: first verify a signed **non-minified** release
on a device; then set `ttq.minify=true`, rebuild, repeat the study, generation, scoring, review, calculator and
persistence checks, and keep `mapping.txt` (CI uploads it).

## Room schema and migrations

Schema JSON is exported to `app/schemas/com.timestablequest.app.data.local.AppDatabase/<version>.json` by the
`androidx.room` Gradle plugin. Version 1 is the first schema; **commit `1.json` after the first build** (CI also
uploads it as an artifact). Every future version bump needs an explicit `Migration` in `Migrations.ALL` (or an
`AutoMigration`) plus the new schema JSON. There is no destructive fallback.

## adb install and logcat verification

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd connectivity airplane-mode enable          # or toggle airplane mode in Quick Settings
adb logcat -c && adb shell monkey -p com.timestablequest.app -c android.intent.category.LAUNCHER 1
adb logcat --pid=$(adb shell pidof com.timestablequest.app) '*:W'
adb shell dumpsys package com.timestablequest.app | grep -i permission
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Then walk the checklist in VERIFICATION.md (first launch offline, all 12 rows, study + diagrams, row/mixed/review/
daily, recovery after force-stop, progress, calculator, rotation, font scale 200 %, TalkBack, predictive Back,
resets, no permission prompts / crashes / network use).

## Completed checks and pending items

See [VERIFICATION.md](VERIFICATION.md).
