# Current project state

Source audit: 2026-11-07, based on main through PR #33. This
records implemented behavior. Notion requirements and historical Stitch mockups may
include planned behavior.

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

The picker frames a visit from the draft destination, then the current fix, then
the last map viewport the user browsed (center and zoom), then the route arguments.
It uses zoom 15 for a destination or fix and the browsed zoom otherwise. The main
map records settled user pans and zooms for that fallback. A recenter tapped before
the first fix stays pending and consumes the first arriving fix. The picker counts
as a visible map for map-only location tracking.

Configure Alarm has an enabled toggle, applied when saving. Save returns after
success and keeps invalid drafts open with a reason. Toolbar Back and
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
See `MapNavigationControls.kt` and `FloatingHudBar.kt` in
`shared/src/ui/components/`, and `ConfigureAlarmScreen.kt` in `shared/src/ui/screens/`.

## Maps, location, and alarms

Android uses `AndroidMapRendererAdapter` with MapLibre 11.5.1 and OSM-derived
cartography. iOS uses `IosMapKitAdapter`. MapLibre is a renderer, not the OSM data
provider. Neither platform provides turn-by-turn routing or a route ETA feature.
Online maps request data for the viewed area from their provider. Centering on
the user can disclose that area. Alarm records and distance calculations remain
on-device.

Despite its historical name, `AndroidFusedLocationAdapter` delegates to
`FjalarLocationService`, which uses Android `LocationManager` GPS and network
providers, not Google Play Services. iOS uses `CLLocationManager` updates. Map-only
tracking has its own foreground owner and stops when the map is hidden or the
app backgrounds. Alarm monitoring has independent ownership. Distance-tiered
adaptive polling is implemented per PRD 7.3 L4 via pure domain `TrackingTier`
(Far: >5 km, Medium: 2–5 km, Near: <2 km) with hysteresis margin. On Android,
`FjalarLocationService` adjusts `LocationManager` polling intervals (60s, 20s, 10s)
and provider accuracy. On iOS, `IosCLLocationAdapter` adjusts Core Location
`desiredAccuracy` (`kCLLocationAccuracyHundredMeters` to `kCLLocationAccuracyBest`)
and `distanceFilter` (100 m down to none). On iOS, native circular geofence monitoring is active via outbound `GeofencePort`
(`IosRegionGeofenceAdapter` wrapping CoreLocation `CLCircularRegion` monitoring),
enabling power-efficient hardware baseband transition detection (ENTER/EXIT) for
commuter journeys. On Android, commuter tracking currently relies on `FjalarLocationService`
distance-tiered polling (`AndroidGeofenceAdapter` implements `GeofencePort` with a testable
engine interface ready for Google Play Services / OS geofence client binding, with the
default fallback engine honestly reporting unavailable).

Snooze persists a two-minute deadline without changing the radius. Scheduled-window
completion suppression and GPS accuracy filtering live in the domain. When a scheduled
alarm transitions to armed monitoring, `AlarmEvaluator` sets `mustExitBeforeTrigger`;
this guard clears only after an accurate fix confirms the commuter is outside the
radius, keeping the alarm suppressed if initial fixes arrive inside the destination area.
Android ongoing monitoring notifications feature a context-aware action: "Skip Today"
for scheduled alarms or "Stop Monitoring" for one-time alarms; Snooze is offered in the
restricted full-screen ringing `AlarmActivity`. Android uses AlarmManager wakeups and
a restricted alarm activity. See [Security](security.md) for the keyguard boundary and
device verification matrix.

iOS schedules user-facing local notifications via `UNUserNotificationCenter` and
supports background schedule synchronization via `BGTaskScheduler` with the permitted
task identifier `com.muhammadridha.fjalar.refresh` (registered in Info.plist with `fetch`
background mode). `IosBackgroundSyncAdapter` registers the task at launch and submits
refresh requests with a minimum 15-minute delay to invoke `SynchronizeAlarmsUseCase` /
`SyncSchedulesPort.syncSchedules()`. Because `BGAppRefreshTask` execution timing is
strictly best-effort and subject to iOS power heuristics, the app continues to
resynchronize schedules when executed again in the foreground. Native circular
geofence monitoring is supported via `IosRegionGeofenceAdapter` (`CLCircularRegion`),
operating within iOS CoreLocation power heuristics.
The ringer uses AVAudioSession/AVAudioPlayer with the bundled `alarm.wav` audio file
for continuous looping alarm chime, falling back to system alert sound if unavailable.
The adapter's name does not establish Apple Critical Alerts entitlement or delivery.
Do not promise reliable automatic iOS window activation while suspended.

## Language and notification text

Shared UI, schedule summaries, and iOS notification text use a common catalog
with matching English and Indonesian resources. Android notification content
resolves native resources using the saved app language. A language change updates
an ongoing Android monitoring notification and resynchronizes pending iOS
notification content without replacing its schedule or snooze deadline.
iOS scheduled reminders use neutral wording because wakeups can occur at either
end of a scheduled window. Platform-owned permission prompts and launcher names still use system localization.

Ringing distance text does not assert that the user is currently within the
radius. Snooze can ring again on its timer even after the user leaves that area.
The completed-window label covers both dismissal and skipping; the skip action
can apply to the next scheduled window, including one starting the next day.

## Material and accessibility

`glassSurface` draws translucent tinted backgrounds and gradient borders. It does
not implement Gaussian backdrop blur or Haze. Reduced-transparency colors and
1.5 dp high-contrast borders dynamically react to live OS accessibility settings
via `AccessibilityPort` and `isReduceTransparencyActive()`: observing Android 14+ / API 34+
`Settings.Secure.accessibility_reduce_transparency` and API 36+
`AccessibilityManager.isHighContrastTextEnabled` on Android, and
`UIAccessibility.isReduceTransparencyEnabled` and
`UIAccessibilityReduceTransparencyStatusDidChangeNotification` on iOS.
Primary-text token contrast tests do not prove contrast over every live map.

Screen-reader semantics for TalkBack (Android) and VoiceOver (iOS) are implemented across the primary screens and validated via Compose unit test assertions. Physical device screen-reader speech execution remains to be verified:
- **Alarm Card (`AlarmCard`)**: Merges descendants (`mergeDescendants = true`) into a single natural speech description including alarm label, address, schedule/one-time trip, trigger radius, sound/vibration mode, and active/completed/disabled status. Action buttons (Delete, Skip Today) provide distinct, labeled announcements (`ACCESSIBLE_DELETE_ALARM`).
- **HUD & Navigation Controls (`FloatingHudBar`, `MapNavigationControls`, `ArmedStatusPill`)**: `ArmedStatusPill` announces commute monitoring state (`ACCESSIBLE_ARMED_STATE`); `MapCompassControl` calculates dynamic speech descriptions with normalized integer bearings and cardinal/intercardinal direction names (`ACCESSIBLE_COMPASS_BEARING`); `MapRecenterControl` and HUD Search Pill expose explicit button roles and content descriptions without raw emoji reading.
- **Alarm Editor (`ConfigureAlarmScreen`)**: Radius slider exposes natural content description (`ACCESSIBLE_RADIUS_SLIDER`) and tabular state description (`stateDescription`); radius presets use `Role.RadioButton`; day-of-week chips use `Role.Checkbox` with full day names and localized selection state descriptions (`ACCESSIBLE_DAY_SELECTED` / `ACCESSIBLE_DAY_NOT_SELECTED`).
- **Map Picker (`MapPickerScreen`)**: Center crosshair announces placement instructions; confirmation button dynamically announces the targeted place name or coordinate fallback (`ACCESSIBLE_PICKER_CONFIRM`) rather than symbol glyphs.
All interactive controls enforce touch targets >= 48 dp.

## Commute quick-picks and recent destinations

The map picker and place search support offline-first recent destination quick-picks
via `RecentDestinationsPort` backed by `PersistentRecentDestinationsAdapter` in
platform key-value storage. Confirmed destinations in `MapPickerViewModel` and
`FjalarViewModel` are stored in an LRU list capped at 5 entries, deduplicated by ID,
case-insensitive trimmed name, and coordinate proximity within 50 meters.
In `SearchPlacesSheet`, when the query is blank and recent destinations exist,
horizontal quick-pick chips render station/place icons, destination names, and
accessible content descriptions. Tapping a quick-pick centers the map picker,
focuses the marker, and enables confirmation. All destination coordinates and history
remain 100% on-device.

## Architecture and distribution

Koin 4.2.2 assembles application modules while domain and ordinary application
classes stay constructor-injected. Persistence is JSON in platform key-value
storage. See [Dependency injection](dependency-injection.md).

For published releases with valid version tags, the `android` job builds a
signed APK and the `verify` job runs multiplatform verification. After both
jobs pass, the `publish` job generates the SHA-256 checksum and uploads both
assets. iOS IPA publication is disabled. The Xcode project bundle identifier
is aligned to canonical `com.muhammadridha.fjalar` across configurations.
See [Releasing](releasing.md) for signing secrets and workflow details.

## Verification boundaries

The Task 8 integration pass ran `./kotlin test`, `./kotlin check`, Android debug
build, and iOS simulator Arm64 debug build on this branch. The first test run
exposed two outdated Indonesian alarm-list string expectations; those test
assertions were corrected and the full test rerun passed. String catalog parity
and whitespace checks also passed. The task report retains exact commands, logs,
and exit codes.

| Device and view | Recorded observations on PR #22 builds |
| --- | --- |
| Android API 37, 1080 × 2400 | Existing destination pin and radius ring stayed visible after map tap, long press, and nearby pan. Main-map search moved the camera with no search marker. New editor began with no destination; picker search cancellation preserved that absence. Picker pan enabled confirmation; an earlier map-tap attempt was inconclusive because its coordinate and settlement state were not recorded. Android system Back saved a valid draft, discarded an invalid draft with a visible reason, and first hid the open keyboard. A destination-selected blank-name draft and an existing edit with equal start/end times discarded with visible reasons; the existing record retained its original schedule. The slider reached 50 m and 3 km on screen. |
| Android API 37, 320 dp portrait at 1.3 font scale | English Light editor showed destination action, radius presets, and mode text inside the screenshot. The form remained scrollable. |
| Android API 37, landscape at 1.3 font scale | Scrolling exposed the destination action and radius controls. The original size, font scale, and rotation settings were restored. |
| iPhone 17, iOS 27.0 | Fresh app showed Indonesian Dark and English Light maps/editors. Browse search moved the camera without selecting a destination. Picker search candidate canceled without draft change and confirmed into the draft. Toolbar Back created a valid alarm, returned from an unchanged editor, saved a changed radius, and showed the invalid-draft reason. A destination-selected blank-name draft and an existing edit with equal start/end times discarded with visible reasons; the existing list retained its original 07:00–09:30 window and 200 m radius. The 50 m preset and intermediate slider values changed on screen; exact slider maximum was not reached through the available coordinate input. |
| iPhone Duo, iOS 27.2 | Fresh app launched and rendered its primary display behind a first-run notification prompt. The second display capture was black; interactive layout checks were not completed. |
| Android API 34 (Cloud Device, 720 × 1616) | Verified PR #33 debug APK via Ramus: foreground location and notification permissions accepted; background location rationale dismissed. MapLibre canvas edge-to-edge; Light mode and Dark mode tokens toggled cleanly. Real-time language switch between English and Bahasa Indonesia verified across HUD, navigation, and day chips (`Sn Sl Rb Km Jm Sb Mg`). Offline station search and Metro/MRT category filtering loaded without network requests. MapPicker pin placement confirmed destination; alarm created with 200m preset and custom label; alarm card rendered with active badge; skip today transitioned to `COMPLETED_FOR_WINDOW` once-per-window state; alarm delete confirmation dialog cancelled and confirmed cleanly with safe empty-state return. |

These observations do not establish map-bearing rotation or reproduce the earlier
missing-pin report. Device injection of persistence failure, an iOS soft keyboard,
foldable postures, screen-reader speech, a full locked-device ringing cycle,
older Android versions, and suspended iOS schedule activation were not tested.
The failure and ringing exit semantics have deterministic source-level tests,
which are separate from native-device evidence.

The later wording and notification-language changes have not been verified on
devices with different app and OS languages. The PR #22 device observations above
do not establish that additional coverage.
