# Verification notes

Date: 2026-10-01. Nothing below is reported as passed unless it was actually run.

## Environment used so far

The build environment had no access to Google Maven, Maven Central or the Gradle distribution server, so the
Gradle/AGP build could not run. What was possible:

| Check | How | Result |
|---|---|---|
| Unit tests (69) | `kotlinc 2.2.20` + JUnit 4.13.2, compiling `domain/**`, `Entities.kt`, `PracticeModels.kt`, `PracticeStore.kt`, `PracticeService.kt` and all tests (Room annotations replaced by compile-only stubs) | **Passed: 69/69** |
| Kotlin type-check of the whole app (all `ui/**`, DataStore, repositories, activity) | `kotlinc 2.2.20` + Compose compiler plugin against Compose 1.7.5, Material3 1.3.1, Navigation 2.8.4, Lifecycle 2.8.7, DataStore 1.1.1, Activity 1.9.3, Core 1.15.0, `android.jar` (API 35); only Room and `core-splashscreen` were compile-only stubs | **Passed: 0 errors** (only stub-annotation warnings). Note: the project pins newer versions (see README); APIs used exist in both. |
| Gradle wrapper | Regenerated with Gradle 8.14.3; `gradle-wrapper.jar` SHA-256 `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172` | Matches official 8.14.3 wrapper output |
| Resource XML | Well-formedness of manifest, values, drawables, xml rules | Passed |
| Launcher icon | Rendered the foreground vector; artwork inside the 66 dp safe zone | Checked visually |
| Release keystore | `keytool -genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000`, alias `timestablequest`, `CN=TimesTable Quest, O=TimesTable Quest, C=BY`, cert SHA-256 `0D:A4:6D:05:84:33:4C:77:F1:33:63:F7:11:71:79:B8:5F:1B:12:09:74:3A:C5:88:19:F4:F5:F6:D6:C7:49:B0` | Created (stored outside the repo) |

Unit-test coverage: all 144 facts and products; row-specific attribution (reversed fact never credited);
10-question row selection and recency priority; balanced mixed selection, no duplicates, no both-orientation pairs;
low-exposure preference; four distinct options with exactly one correct for every fact × 25 seeds; explanation
arithmetic (split, repeated addition, comparison) for every fact; hints never reveal the product; confidence across
distinct sessions; hint-assisted answers; review entry/resolution/reopening; retry isolation from original accuracy;
daily persistence, reuse, midnight rollover and answer-date attribution; duplicate-answer prevention; retention
pruning without losing fact progress or the review queue; row and full resets; calculator arithmetic, limits,
rounding and errors.

## Pending (not yet performed)

| Item | Status |
|---|---|
| `./gradlew testDebugUnitTest` (real Gradle, KSP/Room codegen) | pending — first CI run |
| Room KSP processing of DAO queries; commit generated `app/schemas/.../1.json` | pending — first CI run |
| `lintRelease` | pending — first CI run |
| Signed release APK + AAB build | pending — CI with the four secrets |
| `apksigner verify --print-certs` (no `CN=Android Debug`) | pending — CI |
| AAB `jarsigner` verification + expected signer SHA-256 | pending — CI |
| Packaged/merged release manifest permission check | pending — CI |
| 16 KB: native libraries in APK/AAB | pending — CI (`check_elf_alignment.py`); expected "no native libraries packaged" |
| R8 + resource shrinking pass | pending — only after a verified non-minified release |
| `adb install` of the signed release APK + `adb logcat` review | pending — device/emulator |
| First launch in airplane mode | pending — device |
| All 12 rows, study mode, arrays/area diagrams | pending — device |
| Row checks, mixed practice, Needs Practice review | pending — device |
| Daily 10 creation, resume after force-stop, date change | pending — device |
| Fact and row progress screens | pending — device |
| Calculator and history | pending — device |
| Rotation, tablet/resizable window, font scale 200 % | pending — device |
| TalkBack labels (tiles, expressions, arrays, options) | pending — device |
| Android Back / predictive Back paths | pending — device |
| Selected-row reset and full reset / clear all data | pending — device |
| No permission prompts, no crashes, no network use | pending — device |

When running the device checks, record here: device or emulator model, Android version, artifact (APK SHA-256 /
CI run), and the result of each item.
