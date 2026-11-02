# Current project state

Source audit: 2026-11-02, after PR #19 merged. This records implementation, not a
claim that every device scenario has passed. Notion PRD and UI pages also contain
future requirements and historical Stitch mockups.

## Screens and controls

The shared Compose UI has Map, Alarms, Configure Alarm, and Map Picker destinations.
The map uses safe-area HUD controls and a measured bottom dock. Forms scroll and
controls wrap for compact layouts. Foldable, landscape, large-font, and bilingual
layouts still require visual device checks.

Configure Alarm has an enabled toggle, applied on Save. Scheduled Start and End
open a 24-hour picker with clock and keyboard modes. Compact or large-font layouts
use numeric input. Cancel leaves the draft time unchanged. The radius slider spans
100 m to 10 km, with 300 m, 500 m, 1 km, 2 km, and 3 km presets. Day chips use
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

This documentation audit changes no runtime code. Fresh checks cover icon assets,
string catalogs, local links, whitespace, and prose. Earlier test/build results
belong to their recorded commits and do not count as fresh runs for this audit.
Full locked-device ringing cycles, older Android versions, suspended iOS behavior,
and the complete visual device matrix remain separate verification work.
