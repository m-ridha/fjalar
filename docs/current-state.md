# Current project state

Source audit: 2026-11-03, on branch `feat/map-browsing-alarm-editing`. This
records branch implementation, not a merged release or a claim that every device
scenario has passed. Notion PRD and UI pages also contain future requirements
and historical Stitch mockups.

## Screens and controls

The shared Compose UI has Map, Alarms, Configure Alarm, and Map Picker destinations.
The map uses safe-area HUD controls and a measured bottom dock. Forms scroll and
controls wrap for compact layouts. The Task 8 matrix below records observed
portrait, landscape, font-scale, language, and theme combinations. Foldable
postures and screen-reader speech remain unverified.

The main map browses without selecting a destination. Taps and pans do not create
an alarm or temporary marker; search results move the camera only. The nearest
active alarm's destination and the user blue dot remain the map's markers. A new
alarm opens without a destination, and the map picker requires an explicit choice
before its confirmation button is enabled. Picker search changes its candidate
selection, which is committed to the draft only by picker confirmation. Cancel
returns without changing it.

Configure Alarm has an enabled toggle, applied when saving. Toolbar Back and
Android system Back save a valid new or changed draft. An invalid draft is
discarded on return with a localized reason. A failed save keeps the editor and
draft available for retry. Opening the picker, browsing search, and backgrounding
do not save. An unchanged existing alarm exits without another repository write.

Scheduled Start and End open a 24-hour picker with clock and keyboard modes.
Compact or large-font layouts use numeric input. Cancel leaves the draft time
unchanged. The radius slider spans
50 m to 3 km, with 50 m, 100 m, 200 m, 300 m, 500 m, and 1 km presets. New alarms
default to 100 m. The domain retains its 10 km maximum for saved alarms; this
branch adds no migration or clamping of existing values. Day chips use
`Mo Tu We Th Fr Sa Su` and `Sn Sl Rb Km Jm Sb Mg`. Alarm-list Delete actions are
text-only and aligned to the trailing edge.

Both maps use a blue-dot GPS recenter button with a white ring. The compass appears
when rotated, restores north when tapped, and fades one second after returning
within one degree of north. Its 48 dp slot remains reserved. Armed status shares
the HUD row with the compass. User location and destination markers are distinct.
See `MapNavigationControls.kt`, `FloatingHudBar.kt`, and `ConfigureAlarmScreen.kt`
in `shared/src/ui/`.

## Maps, location, and alarms

Android uses `AndroidMapRendererAdapter` with MapLibre 11.5.1 and OSM-derived
cartography. iOS uses `IosMapKitAdapter`. MapLibre is a renderer, not the OSM data
provider. Neither platform provides turn-by-turn routing or a route ETA feature.

Despite its historical name, `AndroidFusedLocationAdapter` delegates to
`FjalarLocationService`, which uses Android `LocationManager` GPS and network
providers, not Google Play Services. iOS uses `CLLocationManager` updates. Map-only
tracking has its own foreground owner and stops when the map is hidden or the
app backgrounds. Alarm monitoring has independent ownership. Distance-based
adaptive polling and region-geofence registration are not implemented.

Snooze persists a two-minute deadline without changing the radius. Scheduled-window
completion suppression and GPS accuracy filtering live in the domain. The planned
already-at-destination suppression guard is not implemented. Android monitoring
notifications use generic copy and have no quick Stop/Disarm action. Android uses
AlarmManager wakeups and a restricted alarm activity. See [Security](security.md)
for the keyguard boundary and device verification matrix.

iOS schedules user-facing local notifications. They do not run domain code while
the app is suspended. Schedule synchronization runs when the app executes again.
No BGAppRefreshTask registration/submission or region monitoring is implemented.
The plist includes `fetch`, but no permitted task identifier is configured.
The ringer uses AVAudioSession/AVAudioPlayer when bundled alarm audio is available,
otherwise a system alert sound. No mp3/wav alarm file is currently bundled. The
adapter's name does not establish Apple Critical Alerts entitlement or delivery.
Do not promise reliable automatic iOS window activation while suspended.

## Material and accessibility

`glassSurface` draws translucent tinted backgrounds and gradient borders. It does
not implement Gaussian backdrop blur or Haze. Reduced-transparency colors and
1.5 dp borders exist behind a theme flag, but OS setting observation is not wired.
Primary-text token contrast tests do not prove contrast over every live map.

## Architecture and distribution

Koin 4.2.2 assembles application modules while domain and ordinary application
classes stay constructor-injected. Persistence is JSON in platform key-value
storage. See [Dependency injection](dependency-injection.md).

Published version tags build a signed Android APK and SHA-256 checksum after
multiplatform verification. iOS IPA publication is disabled. The Xcode wrapper
still uses a placeholder bundle ID, requiring alignment before distribution.
See [Releasing](releasing.md) for signing secrets and workflow details.

## Verification boundaries

The Task 8 integration pass ran `./kotlin test`, `./kotlin check`, Android debug
build, and iOS simulator Arm64 debug build on this branch. The first test run
exposed two outdated Indonesian alarm-list string expectations; those test
assertions were corrected and the full test rerun passed. String catalog parity
and whitespace checks also passed. The task report retains exact commands, logs,
and exit codes.

| Device and view | Observed on the current build |
| --- | --- |
| Android API 37, 1080 × 2400 | Existing destination pin and radius ring stayed visible after map tap, long press, and nearby pan. Main-map search moved the camera with no search marker. New editor began with no destination; picker search cancellation preserved that absence. Picker pan enabled confirmation. Android system Back saved a valid draft, discarded an invalid draft with a visible reason, and first hid the open keyboard. |
| Android API 37, 320 dp portrait at 1.3 font scale | English Light editor showed destination action, radius presets, and mode text inside the screenshot. The form remained scrollable. |
| Android API 37, landscape at 1.3 font scale | Scrolling exposed the destination action and radius controls. The original size, font scale, and rotation settings were restored. |
| iPhone 17, iOS 27.0 | Fresh app showed Indonesian Dark and English Light maps/editors. Browse search moved the camera without selecting a destination. Picker search candidate canceled without draft change and confirmed into the draft. Toolbar Back created a valid alarm, returned from an unchanged editor, saved a changed radius, and showed the invalid-draft reason. Preset and slider values changed on screen. |
| iPhone Duo, iOS 27.2 | Fresh app launched and rendered its primary display behind a first-run notification prompt. The second display capture was black; interactive layout checks were not completed. |

These observations do not establish map-bearing rotation or reproduce the earlier
missing-pin report. Device injection of persistence failure, an iOS soft keyboard,
foldable postures, screen-reader speech, a full locked-device ringing cycle,
older Android versions, and suspended iOS schedule activation were not tested.
The failure and ringing exit semantics have deterministic source-level tests,
which are separate from native-device evidence.
