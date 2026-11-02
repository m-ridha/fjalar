# Dependency injection

Fjalar uses Koin 4.2.2 with explicit Kotlin DSL modules at the application composition
boundary. Koin assembles the existing hexagonal architecture. It does not replace
ports, constructor injection, or the pure Kotlin domain. Domain entities,
evaluators, services, and port interfaces have no Koin or platform imports.

## Previous wiring

Previously, `FjalarAppContainer` on Android and `FjalarIosContainer` on iOS
implemented `FjalarPlatformContainer`. Constructor defaults manually constructed
persistence, platform adapters, `ScheduleEvaluator`, and `AlarmService`. The root
`App` and `createDefaultViewModel` also supported fallback wiring, including
in-memory storage and fake adapters. Android OS components reached a mutable
`FjalarApplication.instance`, and some tests replaced static container overrides.
Those overlapping production construction paths could silently create different
repositories, services, or tracking lifetimes.

The migration removes that production container pattern. Pure domain and
presentation tests can still construct their subject directly with test fakes;
they do not need a global Koin application.

## Module organization

Definitions live in `com.muhammadridha.fjalar.di`:

| Module | Responsibility | Lifetime |
| --- | --- | --- |
| `persistenceModule` | `PersistentAlarmRepository` and `PersistentPreferencesAdapter`, exposed through their ports | Application singleton |
| `alarmModule` | Mutable system timezone, schedule evaluator, one `AlarmService`, and all inbound port bindings | Application singleton; every inbound port aliases the same service |
| `presentationModule` | Constructor dependencies for `FjalarViewModel` | ViewModel-store lifetime through Koin's ViewModel integration |
| `androidPresentationModule` | Restricted alarm ViewModel with notification ID parameter | Activity ViewModel-store lifetime |
| `androidPlatformModule` | SharedPreferences storage, Android location, AlarmManager, ringer, and hybrid place search | Application singleton, application context only |
| `iosPlatformModule` | NSUserDefaults storage, CoreLocation, background scheduler, ringer, and hybrid place search | Application singleton |
| `fjalarModule(platformModule)` | Includes shared definitions and the selected platform definitions | One assembled application graph |

Native map views retain their existing composition-owned lifecycle. They are not
application singletons: an activity or `MKMapView` must not be retained in the
application graph. UI visibility/foreground notifications still control map-only
location requests independently from active alarm monitoring.

The location adapter receives a deferred inbound-port provider. It resolves the
service only when delivering a location fix, after construction, avoiding an
eager tracker → service → tracker cycle. The service and its mutation mutex must
remain shared by UI, receivers, and background execution.

## Initialization and shutdown

`FjalarApplication.onCreate()` calls `startFjalar(androidPlatformModule)` with
Koin Android application context and `androidPresentationModule`. Android
activities, the location service, and broadcast receiver dispatch use this same
process graph. The restricted alarm ViewModel receives the notification alarm ID
as a Koin parameter and retains pending operations through activity recreation.

SwiftUI's `iosApp.init()` calls `initializeIosApp()`. Its process-lifetime lazy
application starts Koin once, requests notification authorization, and installs
the timezone observer. Recreating `ViewController()` resolves a ViewModel through
Compose rather than constructing a second native graph. Foreground/background
notifications still synchronize schedules and stop map-only tracking. Platform
tracker and iOS timezone-observer definitions register close callbacks for
isolated graph teardown.

## Contributor conventions

1. Add library coordinates/version entries to `libs.versions.toml` and reference
   them from the appropriate `module.yaml`. Preserve Amper and existing supported
   targets. Do not add a separate Gradle build to install a plugin.
2. Keep classes constructor-injected. Put bindings in the relevant module, expose
   ports at architectural boundaries, and include any new submodule through the
   application module. Do not add DI annotations/imports to `domain/`.
3. Use singleton lifetime only for shared application state/platform resources.
   Register ViewModels with `viewModel { ... }`. Use factories for independent
   short-lived objects. Never retain an Activity, view, or controller in an
   application singleton.
4. Register each platform implementation in its platform module. Android
   adapters use application context. Keep target-specific imports out of common
   and domain code.
5. Resolve dependencies at framework entry points. Compose uses `koinViewModel()`;
   Android framework components use Koin Android injection. Ordinary application
   classes receive ports in constructors instead of using `KoinComponent` or
   global lookups.
6. Test shared graphs with isolated `koinApplication` instances and a fake
   platform module. Override required definitions explicitly and close each test
   graph. Resolve the actual repository/service/port/ViewModel dependencies and
   check shared identity. Do not start real GPS, notifications, or audio merely
   to test wiring.

## Toolchain and verification

Koin versions follow the repository version catalog. The explicit classic DSL
works without code generation. The Koin compiler plugin is recommended by the
upstream Compose Multiplatform quickstart for compile-time graph checks, but the
current Amper toolchain has no documented supported Koin plugin integration.
Fjalar therefore verifies runtime graph resolution with tests and startup smoke
checks rather than claiming compile-time DI verification. No KSP processor,
monitoring SDK, or alternative Gradle project is introduced.

```sh
./kotlin test
./kotlin check
./kotlin build -m androidApp -p android -v debug
```

A passing compilation alone cannot prove that runtime definitions resolve. Keep
isolated graph tests and platform startup checks when changing module assembly,
scopes, callbacks, or initialization. See [Security](security.md) for the separate
Android keyguard checks.

## References

- [Official Koin Compose Multiplatform quickstart](https://insert-koin.io/docs/quickstart/cmp/)
- [Koin Compose integration](https://insert-koin.io/docs/reference/koin-compose/compose/)
- [Koin modules](https://insert-koin.io/docs/reference/koin-core/modules/)

The PR #18 migration verification ran `./kotlin test` and passed the Android application,
shared Android, and iOS simulator suites, including fake overrides, shared port
identity, the shipping iOS platform graph, and the parameterized restricted alarm
ViewModel. Android API 37 startup also resolved the shipping Android graph.
These checks do not establish all alarm behavior on physical devices.
