# Fjalar (Wake Me at - Location-based alarm)

<p style="text-align: center;">
  <img src="assets/icons/icon_master_light_1024.png" alt="Fjalar Logo" width="256" height="256" style="border-radius: 22.37%;">
</p>

<p style="text-align: center;">
  <strong>A modern, privacy-first, location-based wake-up alarm for daily commuters.</strong><br>
  Built with Kotlin Multiplatform (KMM / KMP), Hexagonal Architecture (Ports & Adapters), and an iPhone-inspired Liquid Glass UI for Android and iOS.
</p>

<p style="text-align: center;">
  <a href="#features">Features</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#prerequisites">Prerequisites</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="#safety-invariants">Safety Invariants</a> •
  <a href="AGENTS.md">Agent Guide</a>
</p>

---

Fjalar (named after the mythical Norse rooster that is said to herald the onset of Ragnarök) solves a universal commuter problem: **traditional clock alarms fail on variable public transit commutes**. Whether riding the train, commuter rail, intercity bus, or a car ride-share, commuters set a destination pin and trigger radius on an interactive map, arm the alarm, and rest knowing that Fjalar will reliably wake them before their stop.

Contributor and agent development guidelines, architecture contracts, UI design tokens, and safety invariants live in [AGENTS.md](AGENTS.md). Production-ready 2D flat vector icon assets reside in [`assets/icons/`](assets/icons/).

---

## Features

- **Instant One-Off & Recurring Scheduled Alarms:** Set a quick one-off alarm for an unfamiliar stop, or schedule recurring commute windows (e.g., *Office, Mon–Fri, 07:00–09:30*). Background GPS tracking runs *only* during active windows.
- **Hexagonal Architecture (Ports & Adapters):** Core domain logic is 100% pure Kotlin with zero platform SDK imports. Map providers, location trackers, audio ringers, and persistence can be swapped without touching domain code.
- **iPhone-Inspired Liquid Glass UI:** Layered translucent frosted surfaces (`blur(20px)` to `blur(40px)` with specular highlights) floating over full-bleed maps, with instant **Light (`Daylight Glass`)** and **Dark (`Nocturnal Obsidian Glass`)** mode switching.
- **Full Bilingual Localization (`English` & `Bahasa Indonesia`):** 100% of UI copy, day chips (`M–S` vs. `Sn–Mg`), permission disclosures, and OS notifications are translated with instant in-app switching (`🌐 EN | ID`).
- **Sleepy-Eyes Dismissal Safety:** Rings loudly over locked screens with high-contrast pulsing alerts and a tactile **Slide to Stop Alarm** (`Geser untuk Matikan Alarm`) track—preventing accidental pocket touches or sleepy dismissals.
- **100% Local & Privacy-First:** No accounts, no sign-in, and zero server communication. Your GPS coordinates and commute habits never leave your device.
- **Free & Open Cartography:** Uses **OpenStreetMap / MapLibre** on Android (zero API keys, zero billing) and native **Apple MapKit** on iOS.

---

## Architecture

Fjalar is designed around **Hexagonal Architecture** (reference: Alistair Cockburn / Hilmar van der Veen), ensuring complete isolation between domain logic and platform mechanisms:

```mermaid
flowchart TD
    subgraph DrivingAdapters["Driving (Inbound) Adapters"]
        UI["Compose Multiplatform UI\n(Liquid Glass Sheets, Floating HUD, Slider)"]
        BootRecv["OS Boot & Timezone Receivers"]
    end

    subgraph InboundPorts["Inbound Ports (Use Cases)"]
        UC1["ObserveAlarmsPort"]
        UC2["SaveAlarmPort"]
        UC3["ToggleAlarmPort"]
        UC4["HandleLocationUpdatePort"]
        UC5["DismissAlarmPort"]
        UC6["SkipTodayPort"]
        UC7["SyncSchedulesPort"]
    end

    subgraph DomainCore["Core Domain (Pure Kotlin — shared/src/domain)"]
        Entities["Entities & Value Objects:\nLocationAlarm, AlarmSchedule,\nGeoCoordinates, TriggerRadius"]
        StateMachine["AlarmState Lifecycle:\nDISABLED, SCHEDULED_IDLE,\nARMED_MONITORING, RINGING,\nCOMPLETED_FOR_WINDOW"]
        Evaluators["AlarmEvaluator & ScheduleEvaluator"]
        GeoMath["HaversineCalculator & Accuracy Filter"]
    end

    subgraph OutboundPorts["Outbound Ports (Driven SPIs)"]
        P_Loc["LocationTrackerPort"]
        P_Sched["AlarmSchedulerPort"]
        P_Ring["AlarmRingerPort"]
        P_Map["MapRendererPort"]
        P_Repo["AlarmRepositoryPort"]
        P_Pref["PreferencesPort"]
    end

    subgraph DrivenAdapters["Driven (Outbound) Adapters"]
        A_Loc["AndroidFusedLocationAdapter\nIosCLLocationAdapter"]
        A_Sched["AndroidAlarmManagerSchedulerAdapter\nIosRegionMonitoringSchedulerAdapter"]
        A_Ring["AndroidSystemAlarmRingerAdapter\nIosCriticalAudioRingerAdapter"]
        A_Map["OpenStreetMapLibreAdapter (Android)\nAppleMapKitAdapter (iOS)"]
        A_Repo["SqlDelightAlarmRepositoryAdapter"]
        A_Pref["DataStorePreferencesAdapter"]
    end

    DrivingAdapters --> InboundPorts
    InboundPorts --> DomainCore
    DomainCore --> OutboundPorts
    OutboundPorts --> DrivenAdapters
```

### Module Structure

```sh
fjalar/
├── .github/                     # PR template & CI workflows
├── androidApp/                  # Android application shell (Amper module)
│   ├── src/                     # MainActivity & Android manifest
│   └── res/                     # Mipmap icons & Android resources
├── iosApp/                      # iOS application shell (Amper module & Xcode wrapper)
│   ├── src/                     # iOS entry point & Asset catalogs
│   └── module.xcodeproj         # Xcode project wrapper
├── shared/                      # Kotlin Multiplatform shared library
│   ├── src/
│   │   ├── domain/              # PURE KOTLIN CORE (Zero platform dependencies)
│   │   │   ├── model/           # GeoCoordinates, TriggerRadius, AlarmSchedule, LocationAlarm
│   │   │   ├── ports/inbound/   # Use cases: Observe, Save, Toggle, HandleLocation, Dismiss, Skip
│   │   │   ├── ports/outbound/  # SPIs: LocationTracker, AlarmScheduler, AlarmRinger, Map, Repo
│   │   │   └── service/         # AlarmEvaluator, ScheduleEvaluator, HaversineCalculator, AlarmService
│   │   └── adapters/            # Platform implementations of outbound ports
│   ├── composeResources/        # Multiplatform strings (values, values-in, values-id) & drawables
│   └── test/                    # In-memory test suites & fakes (commonTest)
├── assets/icons/                # 1024x1024 master 2D flat vector icons (Light & Dark)
├── project.yaml                 # JetBrains Amper project definition
└── libs.versions.toml           # Version catalog (Kotlin 2.x, Compose, Coroutines, DateTime)
```

---

## Safety Invariants (Hard Non-Negotiables)

All contributors and automated agents must preserve these core invariants:

1. **100% On-Device Privacy:** Zero coordinates or commute schedules sent off-device. No telemetry SDKs harvesting location data.
2. **0% Runaway GPS Battery Drain:** Active GPS monitoring runs **only** when an alarm is in `ARMED_MONITORING`. Tracking stops immediately upon arrival, while ringing, after dismissal, or outside the scheduled time window.
3. **Sleepy-Eyes Dismissal Safety:** Ringing displays a full-screen alert with the oversized frosted **Slide to Stop** handle. Accidental taps or pocket bounces must never dismiss an alarm.
4. **Zero Ads on Ringing Screen:** Advertisements must never appear before, during, or obstructing the dismissal of an active wake-up alarm.
5. **Once-Per-Window Completion Guard:** Dismissed alarms mark their window completed (`COMPLETED_FOR_WINDOW`), preventing false re-triggering while the commuter remains at their destination.
6. **Accuracy Filter:** Rejects coarse GPS fixes where `horizontalAccuracy > radius * 0.8` to prevent false jumps from triggering alarms.

---

## Prerequisites

- **JDK 17+** (e.g. Temurin or OpenJDK)
- **Android SDK** with build-tools (API 34+)
- **macOS with Xcode 15+** (required for compiling iOS target / simulator)
- **JetBrains Amper** (`./kotlin` CLI wrapper bundled in repository)

Verify your environment:

```sh
java -version
./kotlin check
```

---

## Quick Start & Commands

```sh
# Run all unit tests across commonTest and platform targets (Android & iOS simulator)
./kotlin test

# Run static compilation checks and dependency verification
./kotlin check

# Run Android application debug build on connected device / emulator
./kotlin run androidApp

# Open iOS project in Xcode (macOS)
open iosApp/module.xcodeproj
```

---

## Testing Standards

- **Core domain is verified in `commonTest`:** Pure Kotlin unit tests run fast without Android emulators or iOS simulators.
- **Test Doubles:** Use the provided in-memory fakes (`InMemoryAlarmRepository`, `FakeLocationTracker`, `FakeAlarmRinger`, `FakeAlarmScheduler`) rather than mocking frameworks.
- **Boundary Verification:** Tests explicitly cover midnight-crossing commute windows (`23:00–02:00`), leap years, accuracy jitter, and ringer failure rollbacks.

---

## Contributing

1. Review [AGENTS.md](AGENTS.md) for coding conventions, Hexagonal rules, and UI token requirements.
2. Follow [semantic commit messages](https://gist.github.com/joshbuchea/6f47e86d2510bce28f8e7f42ae84c716#file-semantic-commit-messages-md): `<type>(<scope>): <subject>` (e.g. `feat(domain): add trigger radius presets`).
3. Fill out the [Pull Request Template](.github/pull_request_template.md) including the **Hexagonal Architecture & Invariants Check** and fresh `./kotlin test` proof.
