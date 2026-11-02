# Agent Guide for Fjalar (Wake Me at - Location-based alarm)

## Start here

Read the user's current instructions and inspect affected code before relying on documented behavior or historical passing checks. Canonical requirements and design tokens are tracked in the Notion documents below. Private planning files or memory summaries are not prerequisites.

- **PRD (Behavior & Invariants):** [PRD: Fjalar (Wake Me at) v0.4](https://app.notion.com/p/PRD-Fjalar-Wake-Me-at-4030ee070c2381068c72ff3840f77325)
- **UI/UX & Design Tokens:** [Product and UI Design: Fjalar (Wake Me at) v0.5](https://app.notion.com/p/Product-and-UI-Design-Fjalar-Wake-Me-at-4030ee070c2381008ebeddc2ab6c654e)
- **Interactive Screens & Themes:** [Google Stitch Project 12482101652095184669](https://stitch.withgoogle.com/projects/12482101652095184669) (`Liquid Glass Transit` & `Liquid Glass Transit Dark Mode`)
- **Approved 2D Vector App Icon:** [App Icon Design v1.1](https://app.notion.com/p/4040ee070c2381ea8359ef1f191029bf) (masters in `assets/icons/`)
- **Origin Epic:** [Wake Me at (Location-based alarm)](https://app.notion.com/p/Wake-Me-at-Location-based-alarm-4020ee070c2380d7a136ee85ffaf3e3e)
- **Package & Namespace:** `com.muhammadridha.fjalar`
- **Display Name:** "Wake Me at" (English) / "Bangunkan Saya di" (Bahasa Indonesia)

---

See [Current project state](docs/current-state.md) for implemented behavior and known platform limits. Keep product requirements distinct from verified implementation.

## Setup and checks

Preserve module `com.muhammadridha.fjalar`, Kotlin 2.x, Compose Multiplatform, and JetBrains Amper project structure (`project.yaml`, `module.yaml`). Prerequisites: JDK 17+, Android SDK, macOS Xcode 26.4+ (for iOS targets), and Python 3.10+ with Pillow (for icon generation and verification). Do not silently add third-party dependencies or replace Amper with custom Gradle without task authorization.

```sh
./kotlin test           # Run all unit tests across commonTest and platform tests
./kotlin check          # Run code analysis and compilation checks
./kotlin run androidApp # Run Android application debug build
python3 scripts/test_generated_icons.py # Verify icon assets and safe-zone constraints
```

Documentation-only changes need path/link checks, consistency review, and `git status -s`, not runtime tests. Report exact fresh commands/results. Historical acceptance is not a fresh run.

---

## Architecture & Implementation Rules (Hexagonal Architecture)

Follow strict **Hexagonal Architecture (Ports and Adapters)** (reference: Alistair Cockburn / Hilmar van der Veen):

1. **Dependencies point strictly inward:**
   - **Core Domain (`shared/src/domain/`):** Pure Kotlin. Absolutely **zero platform imports** (no Android, UIKit, MapLibre, SQLDelight, or network/hardware SDKs).
   - Domain contains only Entities, Value Objects (`GeoCoordinates`, `TriggerRadius`, `AlarmSchedule`, `SearchResultPlace`, `PlaceCategory`), and pure domain services (`AlarmEvaluator`, `ScheduleEvaluator`, `HaversineCalculator`).
2. **Ports define all external interactions:**
   - **Inbound Ports (`domain/ports/inbound/`):** Explicit use cases driven by UI and system receivers (`ObserveAlarmsPort`, `SaveAlarmPort`, `HandleLocationUpdatePort`, `DismissAlarmPort`, `SkipTodayPort`, `SyncSchedulesPort`).
   - **Outbound Ports (`domain/ports/outbound/`):** SPI contracts declared by the domain for external capabilities (`LocationTrackerPort`, `AlarmSchedulerPort`, `AlarmRingerPort`, `MapRendererPort`, `SearchPlacesPort`, `AlarmRepositoryPort`, `PreferencesPort`).
   - Do not create interfaces for every internal concrete class. Use ports at architectural boundaries.
3. **Pluggable & Interchangeable Adapters (`adapters/`):**
   - Platform mechanisms (OpenStreetMap/MapLibre, Apple MapKit, Android `LocationManager`, iOS `CLLocationManager`, Android `RingtoneManager`, HybridPlaceSearch, SharedPreferences/NSUserDefaults) live solely in adapters.
   - Any adapter must be swappable without modifying domain entities, state machines, or unit tests.
4. **Defensive Domain Validation:**
   - Validate coordinate bounds (`-90..90`, `-180..180`), radius limits (`100m..10,000m`), and minute ranges (`0..1439`) before dispatching to ports or persistence.
   - Distinguish absence from failure. Never swallow errors to fabricate false success.
5. **Code Style & Readability:**
   - Write all Kotlin for human readability rather than compactness.
   - Use descriptive names, guard clauses, and clear control flow.
   - Avoid long unreadable chained functional expressions packed onto single lines. Expand logical blocks with consistent indentation.

---

## Dependency injection and Android security

- Koin definitions belong in `shared/src/di/` and target-specific `di/` packages. Follow [Dependency injection](docs/dependency-injection.md) for module assembly, lifetimes, startup, and fake platform graphs.
- Keep domain and ordinary application classes constructor-injected and Koin-free. Resolve dependencies only at framework composition boundaries. All inbound alarm ports must share one `AlarmService` and mutation mutex.
- Do not restore manual production containers or silently fall back to fake adapters/storage. Tests use isolated Koin applications or explicit constructor-injected fakes.
- Preserve Amper. The current classic DSL uses runtime graph tests. Do not claim compiler-plugin verification without supported integration and actual build evidence.
- `MainActivity` must never show ordinary navigation over the keyguard. Only the non-exported restricted `AlarmActivity` may show while locked, with generic alarm copy and dismiss/snooze actions. Keep it in its separate task and finish it when ringing ends. Follow [Security](docs/security.md) for regression and device checks.
- Treat event text printed by CI as untrusted runner-command input. Use a fresh unpredictable stop/resume token around such output, keep values in environment variables, and run `python3 scripts/test_workflow_command_safety.py` after workflow changes.

---

## UI Design Consistency (Liquid Glass & Bilingual Transit)

Follow the [Product and UI Design Specification](https://app.notion.com/p/4030ee070c2381008ebeddc2ab6c654e) and Stitch project `12482101652095184669`.

1. **Materiality & Tokens:**
   - Map is the living canvas filling 100% of the viewport edge-to-edge.
   - Use layered translucent frosted-glass surfaces with `1 dp` specular top/left rim highlights.
   - Official tokens:
     - **Light Mode (`Daylight Glass`):** Canvas `#FAF9FE` / `#FFFFFF`, Primary `#007AFF`, Secondary `#34C759`, Text `#1A1B1F`.
     - **Dark Mode (`Nocturnal Obsidian Glass`):** Canvas `#0C0E14` / `#111319`, Primary `#00D2FF`, Secondary `#00E676`, Text `#FFFFFF`.
2. **Accessibility & Reduce Transparency Fallback:**
   - `Modifier.glassSurface` currently draws tinted backgrounds and gradient borders, without Gaussian backdrop blur. When its reduced-transparency flag is enabled, it selects 92% opaque tinted surfaces. Automatic OS accessibility-setting observation is not wired:
     - `ReducedTransparencyLightLiquidGlassColors`: `#F4F3F8` @ 92% with 1.5 dp high-contrast border.
     - `ReducedTransparencyDarkLiquidGlassColors`: `#191B22` @ 92% with 1.5 dp high-contrast border.
   - Target `>= 4.5:1` WCAG AA contrast over surfaces. Token tests verify primary text above 15:1 against fallback colors, not every live map composition.
3. **Bilingual Layout & Text Expansion (`en` & `id`):**
   - Every user-facing string must live in multiplatform `Res.string` catalogs (`en` and `id`), with OS-owned display names localized via native iOS `InfoPlist.strings` and Android manifest resource files. Display app names: *"Wake Me at"* (EN) and *"Bangunkan Saya di"* (ID).
   - Bahasa Indonesia strings are `15%–25%` longer (e.g. *Geser untuk Matikan Alarm*, *Alarm Tersimpan & Terjadwal*, *Lewati Hari Ini*). UI pills, cards, and bottom sheets must use adaptive auto-wrapping and flexible widths without text clipping at `320 dp–390 dp`.
   - Day chips must display `Mo Tu We Th Fr Sa Su` in English and `Sn Sl Rb Km Jm Sb Mg` in Bahasa Indonesia to avoid ambiguous single-letter duplicates (*Senin, Selasa, Sabtu*).
4. **Ergonomics & Tabular Numerals:**
   - Use tabular numbers (`tnum`) for all distance readouts (`420m`), telemetry speeds (`34 km/h`), and time windows (`07:00 – 09:30`).

---

## Safety Constraints & Invariants (Hard Non-Negotiables)

Read and strictly preserve these invariant behaviors:

1. **100% On-Device Privacy:**
   - Location coordinates, saved places (home/office), and commute schedules stay strictly in on-device storage.
   - Zero remote tracking, zero analytics harvesting location coordinates, zero external server sync.
2. **0% Runaway GPS Battery Drain:**
   - Background GPS polling runs only for active alarm monitoring. A visible foreground Map may independently request location even without an armed alarm. Its map-only request must stop when the Map is hidden or the app backgrounds.
   - Outside active alarm monitoring or immediately after alarm dismissal, background location polling must stop completely.
3. **Sleepy-Eyes Dismissal Safety:**
   - When the alarm triggers, it must display an unmistakable full-screen lock-screen alert with the oversized frosted **`Slide to Stop Alarm`** track.
   - Accidental single taps, pocket touches, or quick bounces must never dismiss an active alarm.
4. **Zero Distraction / Ad Invariant:**
   - Never display an advertisement before, during, or obstructing the dismissal of an active wake-up alarm. (v1 is 100% free and ad-free).
5. **Once-Per-Window Completion Guard:**
   - When a scheduled alarm is dismissed during an active window, it transitions to `COMPLETED_FOR_WINDOW` and records `suppressedUntil` set to that window's end instant. It must not re-trigger while inside that window or before that instant (including across midnight).
6. **Graceful GPS Degradation & Accuracy Filter:**
   - Reject inaccurate GPS fixes where `horizontalAccuracy > radius * 0.8` from transitioning an alarm to `RINGING`, preventing false wakeups.
   - In tunnels and subways without satellite lock, accept fused network/cell coarse updates for degraded telemetry and user notification rather than failing silently. Only fixes meeting the accuracy threshold may trigger the alarm.
7. **Snooze:**
   - Snooze persists a two-minute deadline. On iOS, its local notification is user-facing. Suspended state is reconciled on the next execution on a best-effort basis.
8. **iOS Background Execution Boundary:**
   - On Android, `AlarmManager.setExactAndAllowWhileIdle()` triggers a broadcast receiver with a `WakeLock` to invoke `syncSchedules()`.
   - On iOS, local notifications scheduled via `UNUserNotificationCenter` are strictly user-facing alerts. They do not afford background code execution to transition an alarm from `SCHEDULED_IDLE` to `ARMED_MONITORING` while the app is suspended without user interaction.
   - Background refresh on iOS via `BGAppRefreshTask` requires the `fetch` background mode (`UIBackgroundModes`) and the task identifier in `BGTaskSchedulerPermittedIdentifiers` in the app's Info.plist. It is strictly best-effort: `earliestBeginDate` serves only as a lower bound. If background execution is delayed or does not execute, the alarm remains in `SCHEDULED_IDLE` until schedule synchronization runs (either when the delayed background task executes or when the user foregrounds the application).

---

## Tests & Asset Validation

1. **Test pure domain logic in `commonTest`:**
   - Test `HaversineCalculator`, `ScheduleEvaluator`, `AlarmEvaluator`, and `HybridPlaceSearchAdapter` thoroughly using pure Kotlin without mocks or platform SDKs.
   - Cover boundary conditions: midnight-crossing windows (e.g., `23:00–02:00`), leap years, weekend/weekday bitmasks, holiday skip logic, and GPS accuracy jitter.
2. **Use Mock Adapters / Test Fakes:**
   - Use `InMemoryAlarmRepository`, `FakeLocationTracker`, `FakeAlarmRinger`, `FakeAlarmScheduler`, and `FakeSearchPlacesAdapter` for use case testing.
   - Use fixed clocks / timestamps and deterministic coordinates to eliminate flaky test timing.
3. **App Icon Asset Generation & Verification:**
   - All mobile launcher icons must be generated from `assets/icons/icon_master_*.png` via `python3 scripts/generate_app_icons.py`.
   - Verify compliance using `python3 scripts/test_generated_icons.py`:
     - Android adaptive foreground and monochrome layers must reside strictly inside the inner 66 dp (264 px / radius 132 px at xxxhdpi) safe center circle.
     - iOS 18 Asset Catalog must register Universal Light, Dark (`luminosity = dark`), and Tinted appearances in `Contents.json`.
4. **Catalog Parity:**
   - Maintain automated tests asserting identical string keys and argument placeholders across `en` and `id` catalogs.

---

## Working Discipline and Pull Requests

1. **Autonomy & Edits:** Continue authorized reversible work autonomously. Inspect `git status` and preserve user edits.
2. **Commit Subject Format:** `<type>(<scope>): <subject>` (concise present tense, e.g. `feat(domain): add trigger radius presets`, `fix(ui): resolve contrast`). Reference: [Semantic Commit Messages](https://gist.github.com/joshbuchea/6f47e86d2510bce28f8e7f42ae84c716#file-semantic-commit-messages-md).
   - Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`.
   - Branch convention: `<type>/<short-kebab-case-subject>`.
3. **Pull Request Descriptions:** Must follow [`.github/pull_request_template.md`](.github/pull_request_template.md) including the Hexagonal Architecture & Invariants checklist, concrete changes made, and fresh `./kotlin test` execution evidence.
4. **Code Review Workflow (Threaded Inline Comments):**
   - **Requesting Changes via Inline Comment Threads:**
     Reviewers (human or agent) must post targeted **inline comments** on the exact diff lines or files requiring attention. Clearly categorize findings (e.g. 🚨 *Safety Invariant Violation*, ⚠️ *Quality/Edge-case*, 💡 *PRD Alignment*).
   - **Author Response & Commit Reference:**
     The author or implementing agent must address every inline thread. After making fixes, commit and push to the PR branch, then **reply directly to the comment thread** explaining what was changed and referencing the commit SHA (e.g., `Addressed in commit abc1234: ...`). Authors must not resolve comment threads themselves.
   - **Reviewer Verification & Resolution:**
     The reviewer inspects the updated code, runs fresh verification commands (`./kotlin test` / `./kotlin check`), and either:
     - **Resolves the thread:** If the fix is verified, the reviewer replies confirming the verification (e.g., `Verified in commit abc1234: ...`) and marks the thread resolved.
     - **Requests further refinement:** If issues remain, the reviewer replies to the thread explaining what is still missing.
   - **Zero Unresolved Threads to Merge:** A PR must not be merged until 100% of review comment threads are verified and resolved, and all automated checks pass cleanly.
