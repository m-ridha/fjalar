# Agent Guide for Fjalar (Wake me up at!)

## Start here

Read the user's current instructions and inspect affected code before relying on documented behavior or historical passing checks. Canonical requirements and design tokens are tracked in the Notion documents below; private planning files or memory summaries are not prerequisites.

- **PRD (Behavior & Invariants):** [PRD: Fjalar (Wake me up at!) v0.4](https://app.notion.com/p/4030ee070c2381068c72ff3840f77325)
- **UI/UX & Design Tokens:** [Product and UI Design v0.5](https://app.notion.com/p/4030ee070c2381008ebeddc2ab6c654e)
- **Interactive Screens & Themes:** [Google Stitch Project 12482101652095184669](https://stitch.withgoogle.com/projects/12482101652095184669) (`Liquid Glass Transit` & `Liquid Glass Transit Dark Mode`)
- **Approved 2D Vector App Icon:** [App Icon Design v1.1](https://app.notion.com/p/4040ee070c2381ea8359ef1f191029bf) (masters in `assets/icons/`)
- **Package & Namespace:** `com.muhammadridha.fjalar`

---

## Setup and checks

Preserve module `com.muhammadridha.fjalar`, Kotlin 2.x, Compose Multiplatform, and JetBrains Amper project structure (`project.yaml`, `module.yaml`). Prerequisites: JDK 17+, Android SDK, and macOS Xcode (for iOS targets). Do not silently add third-party dependencies or replace Amper with custom Gradle without task authorization.

```sh
./kotlin test           # Run all unit tests across commonTest and platform tests
./kotlin check          # Run code analysis and compilation checks
./kotlin run androidApp # Run Android application debug build
```

Documentation-only changes need path/link checks, consistency review, and `git status -s`, not runtime tests. Report exact fresh commands/results; historical acceptance is not a fresh run.

---

## Architecture & Implementation Rules (Hexagonal Architecture)

Follow strict **Hexagonal Architecture (Ports and Adapters)** (reference: Alistair Cockburn / Hilmar van der Veen):

1. **Dependencies point strictly inward:**
   - **Core Domain (`shared/src/domain/`):** Pure Kotlin. Absolutely **zero platform imports** (no Android, UIKit, MapLibre, SQLDelight, or network/hardware SDKs).
   - Domain contains only Entities, Value Objects (`GeoCoordinates`, `TriggerRadius`, `AlarmSchedule`), and pure domain services (`AlarmEvaluator`, `ScheduleEvaluator`, `HaversineCalculator`).
2. **Ports define all external interactions:**
   - **Inbound Ports (`domain/ports/inbound/`):** Explicit use cases driven by UI and system receivers (`ObserveAlarmsPort`, `SaveAlarmPort`, `HandleLocationUpdatePort`, `DismissAlarmPort`, `SkipTodayPort`).
   - **Outbound Ports (`domain/ports/outbound/`):** SPI contracts declared by the domain for external capabilities (`LocationTrackerPort`, `AlarmSchedulerPort`, `AlarmRingerPort`, `MapRendererPort`, `AlarmRepositoryPort`, `PreferencesPort`).
   - Do not create interfaces for every internal concrete class; use ports at architectural boundaries.
3. **Pluggable & Interchangeable Adapters (`adapters/`):**
   - Platform mechanisms (OpenStreetMap/MapLibre, Apple MapKit, Android `FusedLocationProviderClient`, iOS `CLLocationManager`, Android `RingtoneManager`, DataStore) live solely in adapters.
   - Any adapter must be swappable without modifying domain entities, state machines, or unit tests.
4. **Defensive Domain Validation:**
   - Validate coordinate bounds (`-90..90`, `-180..180`), radius limits (`100m..10,000m`), and minute ranges (`0..1439`) before dispatching to ports or persistence.
   - Distinguish absence from failure; never swallow errors to fabricate false success.
5. **Code Style & Readability:**
   - Write all Kotlin for human readability rather than compactness.
   - Use descriptive names, guard clauses, and clear control flow.
   - Avoid long unreadable chained functional expressions packed onto single lines. Expand logical blocks with consistent indentation.

---

## UI Design Consistency (Liquid Glass & Bilingual Transit)

Follow the [Product and UI Design Specification](https://app.notion.com/p/4030ee070c2381008ebeddc2ab6c654e) and Stitch project `12482101652095184669`.

1. **Materiality & Tokens:**
   - Map is the living canvas filling 100% of the viewport edge-to-edge.
   - Use layered translucent frosted-glass surfaces (`Haze` / `RenderEffect` / `UIVisualEffectView`) with `1 dp` specular top/left rim highlights.
   - Never invent arbitrary hex colors. Use the official tokens:
     - **Light Mode:** Canvas `#FAF9FE` / `#FFFFFF`, Primary `#007AFF`, Secondary `#34C759`, Text `#1A1B1F`.
     - **Dark Mode:** Canvas `#0C0E14` / `#111319`, Primary `#00D2FF`, Secondary `#00E676`, Text `#FFFFFF`.
2. **Bilingual Layout & Text Expansion (`en` & `id`):**
   - Every user-facing string must live in multiplatform `Res.string` catalogs (`en` and `id`). Zero hardcoded English/Indonesian strings in UI or service code.
   - Bahasa Indonesia strings are `15%–25%` longer (e.g. *Geser untuk Matikan Alarm*, *Alarm Tersimpan & Terjadwal*, *Lewati Hari Ini*). UI pills, cards, and bottom sheets must use adaptive auto-wrapping and flexible widths without text clipping at `320 dp–390 dp`.
   - Day chips must display `M T W T F S S` in English and `Sn Sl Rb Km Jm Sb Mg` in Bahasa Indonesia to avoid ambiguous single-letter duplicates (*Senin, Selasa, Sabtu*).
3. **Ergonomics & Tabular Numerals:**
   - Use tabular numbers (`tnum`) for all distance readouts (`420m`), telemetry speeds (`34 km/h`), and time windows (`07:00 – 09:30`).
   - Maintain `>= 4.5:1` contrast over translucent blurred surfaces regardless of underlying map tile brightness.

---

## Safety Constraints & Invariants (Hard Non-Negotiables)

Read and strictly preserve these invariant behaviors:

1. **100% On-Device Privacy:**
   - Location coordinates, saved places (home/office), and commute schedules stay strictly in on-device storage.
   - Zero remote tracking, zero analytics harvesting location coordinates, zero external server sync.
2. **0% Runaway GPS Battery Drain:**
   - Active GPS polling runs **only** when an alarm is actively armed or within its active scheduled time window.
   - Outside of active commute windows or immediately after alarm dismissal, background location polling must stop completely.
3. **Sleepy-Eyes Dismissal Safety:**
   - When the alarm triggers, it must display an unmistakable full-screen lock-screen alert with the oversized frosted **`Slide to Stop Alarm`** track.
   - Accidental single taps, pocket touches, or quick bounces must never dismiss an active alarm.
4. **Zero Distraction / Ad Invariant:**
   - Never display an advertisement before, during, or obstructing the dismissal of an active wake-up alarm.
5. **Once-Per-Window Completion Guard:**
   - When a scheduled alarm triggers and is dismissed during its morning window, it records `last_triggered_window_key` (`YYYY-MM-DD`). It must not re-trigger while the user remains at the office during that same window.
6. **Graceful GPS Degradation & Accuracy Filter:**
   - Reject single inaccurate GPS fixes where `horizontalAccuracy > radius * 0.8`.
   - In tunnels and subways without satellite lock, accept fused network/cell coarse updates and notify rather than failing silently.

---

## Tests

1. **Test pure domain logic in `commonTest`:**
   - Test `HaversineCalculator`, `ScheduleEvaluator`, and `AlarmEvaluator` thoroughly using pure Kotlin without mocks or platform SDKs.
   - Cover boundary conditions: midnight-crossing windows (e.g., `23:00–02:00`), leap years, weekend/weekday bitmasks, holiday skip logic, and GPS accuracy jitter.
2. **Use Mock Adapters / Test Fakes:**
   - Use `InMemoryAlarmRepository`, `FakeLocationTracker`, `FakeAlarmRinger`, and `FakeAlarmScheduler` for use case testing.
   - Use fixed clocks / timestamps and deterministic coordinates to eliminate flaky test timing.
3. **Catalog Parity:**
   - Maintain an automated test asserting identical string keys and argument placeholders (`%1$s`, `{distance}`) across `en` and `id` catalogs.

---

## Working Discipline and Pull Requests

1. **Autonomy & Edits:** Continue authorized reversible work autonomously. Inspect `git status` and preserve user edits.
2. **Commit Subject Format:** `<type>: [<scope>] <subject>` (concise present tense).
   - Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`.
   - Branch convention: `<type>/<short-kebab-case-subject>`.
3. **Pull Request Descriptions:** Must follow [`.github/pull_request_template.md`](.github/pull_request_template.md) including the Hexagonal Architecture & Invariants checklist, concrete changes made, and fresh `./kotlin test` execution evidence.
