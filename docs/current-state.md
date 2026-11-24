# Current project state

Source audit: 2026-11-24, based on main through v0.4.2 preparation (PRs #63 and #64, commit
`1f920fa`). This records implemented behavior across Phases 1 through 10 and post-v0.3.0
wiring, security, editor UX, app icon updates, Android map cartography, native splash
gating, worldwide online place search, Liquid Glass v2 tokens, E2E bugfixes,
vertical recent destinations, cold-start splash night-mode sync, and picker,
attribution, and empty-state UI polish. Notion requirements and historical Stitch
mockups may include planned future enhancements.

## Release readiness (v0.4.2)

Fjalar v0.4.2 ships core domain logic, Liquid Glass v2 UI (`#F2F3F8` Light canvas and
`1 dp` pill rims), native startup splash with last-known-location viewport gating and
unconditional API 31+ per-app night-mode sync, worldwide online place search
(`PlaceSearchAdapter` with OSM Photon and Apple MapKit `MKLocalSearch`), vertical
recent destinations in `SearchPlacesSheet` with browse/picker selection recording in
`FjalarViewModel`, search-aware picker controls, symmetric-margin iOS map attribution
clearance, bilingual transit localization with aligned empty-state copy, dynamic
accessibility observation, wired background refresh and circular geofencing, explicit
alarm editor save/discard confirmation, updated crowing-bolt app icons, zoom-tiered
Android map POI labeling, and multiplatform release packaging:

- **Bilingual transit (`en` & `id`)**: Full English and Bahasa Indonesia catalog parity across
  UI, day chips (`Mo..Su` / `Sn..Mg`), notifications, unsaved-changes discard dialog, and
  worldwide place search.
- **Accessibility & Contrast**: Live observation of OS reduce-transparency / high-contrast
  settings on Android and iOS via Koin's `AccessibilityPort` singleton (with preview/test
  fallback), falling back to 92% opaque tinted surfaces and >= 15:1 WCAG AAA primary-text
  contrast. Semantic content descriptions (with decorative glyph nodes cleared) and
  `>= 48 dp` touch targets cover primary HUD, list, navigation, editor, and ringing-alert
  controls.
- **iOS Background Refresh**: `BGAppRefreshTask` (`com.muhammadridha.fjalar.refresh`, `fetch`
  mode in `iosApp/src/Info.plist`) is implemented via `BackgroundSyncPort` /
  `IosBackgroundSyncAdapter`, registered at iOS launch (`shared/src@ios/di/IosModule.kt`), and
  wired into production lifecycle and alarm mutations via `SynchronizeAlarmsUseCase` in
  `FjalarViewModel` (triggered on launch, foreground, background entry via
  `UIApplicationDidEnterBackgroundNotification`, locale change, and alarm mutations). Pending
  refresh requests are submitted only when an enabled `SCHEDULED` alarm (`SCHEDULED_IDLE`,
  `ARMED_MONITORING`, or `COMPLETED_FOR_WINDOW`) or `SNOOZED` alarm exists, and cancelled
  otherwise.
- **Native Circular Geofencing**: `GeofencePort` (`IosRegionGeofenceAdapter` via
  `CLCircularRegion` and `AndroidGeofenceAdapter`, whose default engine reports
  `isAvailable() == false`) and inbound `HandleGeofenceTransitionPort` are wired into
  `AlarmService`. `AlarmService` reconciles up to 20 armed alarm regions (sorted by `alarm.id`,
  registering `ENTER` + `EXIT` when `mustExitBeforeTrigger` is set, querying active OS regions via
  `getRegisteredRegionIds()` to prune orphaned regions across restarts and retry rejected
  registrations, and unregistering all regions when disarmed). Geofence transitions escalate
  tracking to `TrackingTier.APPROACH` in a single `startTracking` call (retained across syncs
  until a fresh GPS fix arrives) without bypassing GPS accuracy filtering or
  `mustExitBeforeTrigger`.
- **Automated Multiplatform Packaging & App Icons**: Headless build and packaging scripts build
  and verify signed Android APKs (`scripts/build_release_android.sh`) and unsigned physical-device
  iOS `.xcarchive` / `.ipa` packages (`scripts/build_release_ios.sh`, stamping
  `CFBundleShortVersionString` and `CFBundleVersion` in both the outer archive plist and embedded
  `Fjalar.app/Info.plist`) with SHA-256 checksums. Platform launcher icons and iOS 18 Light,
  Dark, and Tinted appearances are generated from the crowing-bolt master artwork via
  `scripts/generate_app_icons.py` within the 66 dp radial safe circle.
- **Android Map POI Cartography**: Three zoom-gated `SymbolLayer` tiers read Carto's existing
  `protomaps` -> `pois` vector tiles with zero extra tile requests, rendering themed vector
  sprites and OSM place-name labels. Neighborhood labels anchor above minor alleys below
  `roadname_sec` so dense streets no longer suppress mosques and schools at `z17+`, and exact
  `15.0` camera targets settle at `15.05` so downward `z17+ -> z15` animations stay above
  Carto's `z15` tile threshold.
- **Security & Keyguard Isolation**: Non-exported `AlarmActivity` isolated with `FLAG_SECURE`,
  `excludeFromRecents="true"`, `launchMode="singleTask"`, separate `taskAffinity`,
  `setShowWhenLocked(true)`, `BackHandler` guarding while loading/ringing/pending, and
  `finishAndRemoveTask()` on dismissal.

## Screens and controls

The shared Compose UI has Map, Alarms, Configure Alarm, and Map Picker destinations.
The map uses safe-area HUD controls and a measured bottom dock. Forms scroll and
controls wrap for compact layouts. The Task 8 matrix below records observed
portrait, landscape, font-scale, language, and theme combinations. Foldable
postures and screen-reader speech remain unverified.

On cold start, `FjalarUiState.isInitialViewportReady` gates `NavHost` and
`PlatformMapView` until the initial viewport is known so the map never flashes
the neutral `(0.0, 0.0)` origin. The gate opens on the first of: a position
restored from disk, an arriving location fix, a ringing alarm,
known-unavailable location access (denied permission or disabled location
services) once alarms load, or a 2.5 s timeout
(`STARTUP_VIEWPORT_TIMEOUT_MILLIS`). Every fresh fix is persisted in the
device-local preference store as the last known position once it moves 100 m
from the previous one (`LAST_KNOWN_LOCATION_MOVE_METERS`), so a returning user
opens on their last area and the first live fix then moves the camera exactly
once; a map gesture before that fix keeps the browsed viewport. When no fix
arrives before opening,
startup falls back to selected, nearest-armed, or first-saved alarm coordinates
when available, and only a first launch without any position reaches the
neutral origin. Android holds `androidx.core:splashscreen` via
`setKeepOnScreenCondition` (with a 4 s hard cap) and fades out over 200 ms (or
0 ms when animations are disabled). Both platforms render `StartupPlaceholder`
over the canvas until ready, fading instantly when reduced transparency is
active. Launch artwork is derived from the approved icon masters by
`scripts/generate_splash_assets.py` and kept inside the 192 dp circle Android
12+ paints, which `scripts/test_generated_splash_assets.py` verifies. Android
reads the persisted `ThemeMode` synchronously before `installSplashScreen()` and
unconditionally syncs `UiModeManager.setApplicationNightMode(...)` (API 31+) in
`StartupTheme.syncApplicationNightMode` without comparing against the system-wide
`uiModeManager.nightMode` (so switching back to match the OS theme never skips
updating the cold-start splash), or wraps the activity base context (API 30-) so the
native splash matches Light, Dark, or System mode and the first Compose frame seeds
`themeMode` from `PreferencesPort.peekThemeMode()`.
On iOS, `UILaunchScreen` follows system appearance through `LaunchIcon` and
`LaunchBackground` light/dark assets, while `StartupPlaceholder` immediately
matches the stored `ThemeMode` (a brief mismatch can occur when the stored
theme differs from the iOS system appearance). `AlarmActivity` and receiver
paths do not wait on the startup gate.

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
the first fix stays pending and consumes the first arriving fix unless superseded by a
browse search selection, user map pan/zoom, or opening an alarm editor. The picker counts
as a visible map for map-only location tracking. `shouldShowMapPickerControls(isSearchVisible)`
in `MapPickerScreen` hides the top HUD pills (Back, hint pill, search bar),
`MapNavigationControls`, and bottom `Choose destination` CTA while `SearchPlacesSheet`
is open (keeping `SearchBackHandler` active), preventing controls from bleeding through
behind the translucent search modal. `SearchPlacesSheet` renders as a 4-sided
rounded floating glass modal (`RoundedCornerShape(28.dp)`) with outer spacing above the
bottom navigation dock or picker edge and scroll content clipped inside its rounded border.

Configure Alarm has an enabled toggle, applied when saving. Explicit `Save` validates
in place, saves valid new or changed drafts, skips redundant repository writes for an
unchanged existing alarm, keeps invalid drafts open with a localized reason, and retains
the editor for retry if persistence fails. Toolbar Back and Android system Back never
auto-save: when there are no unsaved changes they exit immediately, and when the draft
has unsaved changes (including after picking coordinates or a failed save) they show a
2-action confirmation dialog (`Keep editing` / `Discard`; `Lanjut mengedit` / `Buang`).
Opening the picker, browsing search, and backgrounding do not save or trigger the dialog.

Scheduled Start and End open a 24-hour picker with clock and keyboard modes.
Compact or large-font layouts use numeric input. Cancel leaves the draft time
unchanged. The radius slider spans
50 m to 3 km, with 50 m, 100 m, 200 m, 300 m, 500 m, 1 km, 2 km, and 3 km presets
arranged in two center-aligned equal-width 5-column rows (sub-km in row 1; 1–3 km in row 2). New alarms
default to 100 m. The domain retains its 10 km maximum for saved alarms.
`v0.4.2` adds no migration or clamping of existing values. Day chips use
`Mo Tu We Th Fr Sa Su` and `Sn Sl Rb Km Jm Sb Mg` across two center-aligned equal-width 5-column
rows (`Mo–Fr` / `Sn–Jm` in row 1; `Sa–Su` / `Sb–Mg` in row 2). `AlarmCard` keeps compact metadata pills
(`radius` and sound/vibration badges) in its metadata `FlowRow` and places the scheduled
skip/completed control (`CARD_SKIP_TODAY` / `CARD_SKIPPED_TODAY`) and trailing `Delete` outlined
button (without a trash icon) in a single shared bottom action row so toggling or skipping a
scheduled alarm preserves card height without clipping `Delete`, while Configure Alarm uses
a full-width centered danger-tinted pill.

Both maps use a blue-dot GPS recenter button with a white ring. The compass appears
when rotated, restores north when tapped, and fades one second after returning
within one degree of north. Its 48 dp slot remains reserved. Armed status shares
the HUD row with the compass. User location and destination markers are distinct.
See `MapNavigationControls.kt` and `FloatingHudBar.kt` in
`shared/src/ui/components/`, and `ConfigureAlarmScreen.kt` in `shared/src/ui/screens/`.

## Maps, location, and alarms

Android uses `AndroidMapRendererAdapter` with MapLibre 11.5.1 and OSM-derived
cartography (`Carto Positron` / `Carto Dark Matter`). Three zoom-gated OSM Carto-style POI
`SymbolLayer` tiers (`fjalar-poi-major` at `z13+`, `fjalar-poi-neighborhood` at `14.8f+`, and
`fjalar-poi-local` at `z16+`) read Carto's existing `protomaps` -> `pois` vector tiles with
zero extra network tile requests, rendering 23 themed `48x48 px` vector sprites
(`AndroidPoiIcons`) and color-coded category labels (`place_of_worship` split across
`muslim` / `christian` / general worship, hospitals, clinics, pharmacies, rail/bus/ferry/airport
hubs, schools, banks/ATMs, shops, petshops, food/cafes, parks, and civic landmarks) while
excluding map noise (`administrative`, `tree`, `crossing`, `traffic_signals`, `construction`).
`fjalar-poi-local` sits below `roadname_minor` so street names keep priority over dense local
shops, `fjalar-poi-neighborhood` anchors via `resolveNeighborhoodPoiAnchorLayerId` below
`roadname_sec` (falling back through `roadname_pri`, `roadname_major`, and user/pin overlays)
so minor alleys do not suppress neighborhood mosques and schools at `z17+`, and `fjalar-poi-major`
sits above `roadname_*` and below user/pin overlays. `AndroidMapRendererAdapter.resolveCameraZoom`
biases exact `15.0` camera targets to `15.05` (well below `16.0`) so downward `z17+ -> z15`
animations do not settle underneath Carto's `z15` tile threshold.
`AndroidMapRendererAdapter.resolveGeofenceAnchorLayerId` inserts geofence
fill and stroke layers below the base style's trailing symbol-label block (for example
`watername_ocean` in Light `Carto Positron`—skipping the early `waterway_label` symbol layer
positioned below tunnels, roads, and buildings—and `waterway_label` in Dark
`Carto Dark Matter`), falling back below Fjalar overlay layers (`USER_ACCURACY_LAYER_ID`,
`USER_DOT_LAYER_ID`, `PIN_LAYER_ID`, and the 3 POI tiers) when no trailing base symbol layer
exists, so geofence overlays draw above roads, buildings, and borders while map and POI labels
stay readable on top. `PlatformMapView` accepts `bottomOverlayClearance: Dp = 0.dp`
(no-op on Android where MapLibre attribution renders below floating bottom controls),
wired to `dockHeight` on `MapRoute` (`FjalarApp.kt`) and measured `confirmBarHeight`
(`0.dp` when search is open) on `MapPickerScreen`. iOS uses `IosMapKitAdapter`, where
`updateBottomOverlayClearance` and `FjalarMKMapView.safeAreaInsetsDidChange()` apply
symmetric vertical `MKMapView.layoutMargins` (`bottomSafeArea + clearance` with
`insetsLayoutMarginsFromSafeArea = false`) so ` Maps Legal` clears floating bottom
overlays without shifting `centerCoordinate` from geometric bounds center. MapLibre
is a renderer, not the OSM data
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
(`FAR`: > 5 km, `MEDIUM`: 2–5 km, `APPROACH`: < 2 km) with a 100 m hysteresis margin
(`shared/src/domain/service/LocationTrackingPolicy.kt`). On Android,
`FjalarLocationService` adjusts `LocationManager` polling intervals (`60s`, `20s`, `10s`)
and minimum displacement (`minDistanceMeters`: `50 m`, `10 m`, `0 m`) while keeping both
`GPS_PROVIDER` and `NETWORK_PROVIDER` registered across all tiers. On iOS,
`IosCLLocationAdapter` adjusts Core Location `desiredAccuracy`
(`kCLLocationAccuracyHundredMeters` for `FAR` and `MEDIUM`,
`kCLLocationAccuracyBest` for `APPROACH`) and `distanceFilter` (`50 m`, `10 m`, down to
`kCLDistanceFilterNone`). Native circular geofencing is wired through `GeofencePort` and inbound
`HandleGeofenceTransitionPort` in `AlarmService`, backed by `IosRegionGeofenceAdapter`
(`CLCircularRegion`) and `AndroidGeofenceAdapter` (whose default engine reports
`isAvailable() == false`, leaving Android on distance-tiered `LocationManager` polling).
`AlarmService` reconciles up to 20 armed regions sorted by `alarm.id`, removes all regions when
no alarms are armed, and escalates tracking to `TrackingTier.APPROACH` on matching transitions
without directly triggering `RINGING` or clearing `mustExitBeforeTrigger`.

Snooze persists a two-minute deadline without changing the radius. Scheduled-window
completion suppression and GPS accuracy filtering live in the domain. When a scheduled
alarm transitions to armed monitoring, `AlarmEvaluator` sets `mustExitBeforeTrigger`;
this guard clears only after an accurate fix confirms the commuter is outside the
radius, keeping the alarm suppressed if initial fixes arrive inside the destination area.
Android ongoing monitoring notifications feature a context-aware action: "Skip Today"
for scheduled alarms or "Stop Monitoring" for one-time alarms; Snooze is offered in the
restricted full-screen ringing `AlarmActivity`. Android uses AlarmManager wakeups, a
restricted `FLAG_SECURE` alarm activity, and the bundled `androidApp/res/raw/alarm.wav`
as its final fallback ringtone after system default and installed `TYPE_ALARM` tones. See
[Security](security.md) for the keyguard boundary and device verification matrix.

iOS schedules user-facing local notifications via `UNUserNotificationCenter` and
declares `BGTaskScheduler` support with the permitted task identifier
`com.muhammadridha.fjalar.refresh` (`fetch` background mode in `iosApp/src/Info.plist`).
`IosBackgroundSyncAdapter` registers the task handler at launch
(`shared/src@ios/di/IosModule.kt`). `FjalarViewModel` invokes `SynchronizeAlarmsUseCase` on
launch, foreground/background transitions (`UIApplicationWillEnterForegroundNotification` and
`UIApplicationDidEnterBackgroundNotification`), locale updates, and alarm mutations, submitting
a `BGAppRefreshTaskRequest` only when a scheduled (`SCHEDULED_IDLE`, `ARMED_MONITORING`,
`COMPLETED_FOR_WINDOW`) or `SNOOZED` alarm needs refresh and cancelling pending requests
otherwise. Once fired, `handleTask()` calls `SyncSchedulesPort.syncSchedules()` and either
reschedules with a 15-minute minimum delay (when alarms still need refresh) or cancels pending
requests; if `syncSchedules()` fails, the task completes without rescheduling, and if
reschedule/cancel fails, the error is caught without retry. Because `BGAppRefreshTask` execution
timing is strictly best-effort and subject to iOS power heuristics, the app continues to
resynchronize schedules when executed again in the foreground. `IosRegionGeofenceAdapter`
(`CLCircularRegion`) is wired to `AlarmService` for region reconciliation and `APPROACH`
escalation via `HandleGeofenceTransitionPort`.
The ringer uses `AVAudioSession`/`AVAudioPlayer` with the bundled `alarm.wav` audio file
for a continuous looping alarm chime, falling back to system alert sound if unavailable.
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

`glassSurface` draws translucent tinted backgrounds (`Liquid Glass v2` tokens) and
gradient borders (1 dp standard, 1.5 dp reduced transparency), including unselected
trigger-radius presets and day chips in `ConfigureAlarmScreen`. It does
not implement Gaussian backdrop blur or Haze. Reduced-transparency colors and
1.5 dp high-contrast borders react to live OS accessibility settings via
`AccessibilityPort` and `isReduceTransparencyActive()`: observing `Settings.Secure`
string key `"accessibility_reduce_transparency"` (`AndroidAccessibilityAdapter.ACCESSIBILITY_REDUCE_TRANSPARENCY`)
and API 36+ `AccessibilityManager.isHighContrastTextEnabled` on Android, and
`UIAccessibilityIsReduceTransparencyEnabled()` plus
`UIAccessibilityReduceTransparencyStatusDidChangeNotification` on iOS. `isReduceTransparencyActive()`
first resolves Koin's `AccessibilityPort` singleton via `KoinPlatformTools.defaultContext()`
and falls back to instantiating the platform adapter inside Compose `remember` only when
Koin is uninitialized or omits an `AccessibilityPort` binding.
Primary-text token contrast tests do not prove contrast over every live map.

Screen-reader semantics for TalkBack (Android) and VoiceOver (iOS) are wired
across the primary screens, with semantic helper functions validated by pure Kotlin
unit tests (`AlarmCardSemanticsTest.kt`, `ConfigureAlarmSemanticsTest.kt`,
`MapPickerSemanticsTest.kt`, `MapNavigationSemanticsTest.kt`, rather than Compose UI
semantics-tree assertions). Physical device screen-reader speech execution remains to
be verified:
- **Alarm Card (`AlarmCard`)**: Merges descendants (`mergeDescendants = true`) into a single natural speech description including alarm label, address, schedule/one-time trip, trigger radius, sound/vibration mode, and active/completed/disabled status. Action buttons (Delete, Skip Today) provide distinct, labeled announcements (`ACCESSIBLE_DELETE_ALARM`).
- **HUD & Navigation Controls (`FloatingHudBar`, `MapNavigationControls`, `ArmedStatusPill`, `FrostedBottomNavigationBar`)**: `ArmedStatusPill` announces commute monitoring state (`ACCESSIBLE_ARMED_STATE`); `MapCompassControl` calculates dynamic speech descriptions with normalized integer bearings and cardinal/intercardinal direction names (`ACCESSIBLE_COMPASS_BEARING`); `MapRecenterControl`, the HUD Search Pill, `LanguageDropdown`, and Theme Toggle keep `Role.Button` semantics and localized `contentDescription` on their clickable containers while clearing decorative/redundant child `Text` nodes (`"🔍"`, search placeholder text, `"🌐 EN ▾"`, `"🌙"` / `"☀️"`) with `clearAndSetSemantics {}` without stripping `OnClick` semantics. `FrostedBottomNavigationBar` clears inner emoji and duplicate label `Text` semantics on `NavTabItem` (`Role.Tab`) and only mounts on verified `MapRoute` or `AlarmsRoute` destinations so soft-keyboard resizing never surfaces it on `ConfigureRoute`.
- **Alarm Editor (`ConfigureAlarmScreen`)**: Radius slider exposes natural content description (`ACCESSIBLE_RADIUS_SLIDER`) and tabular state description (`stateDescription`); radius presets use `Role.RadioButton`; day-of-week chips use `Role.Checkbox` with localized day abbreviations (`DAY_NAME_*`: `Mon..Sun` in EN, `Sn..Mg` in ID) and selection state descriptions (`ACCESSIBLE_DAY_SELECTED` / `ACCESSIBLE_DAY_NOT_SELECTED`).
- **Map Picker & Ringing Alert (`MapPickerScreen`, `RingingAlertOverlay`)**: Map Picker's center crosshair clears child semantics (`clearAndSetSemantics`) to announce placement instructions, and its confirmation `Button` keeps `.semantics { role = Role.Button; contentDescription = ... }` (`ACCESSIBLE_PICKER_CONFIRM`) while clearing the child `"✓ ..."` `Text` node with `clearAndSetSemantics {}` so `OnClick` and `Disabled` semantics remain intact without speaking `"✓"` or duplicating the label. `RingingAlertOverlay` clears the decorative `"📍"` radar glyph via `clearAndSetSemantics {}`.
Primary HUD, navigation, card, editor, and ringing-alert controls (including Snooze with
`heightIn(min = 48.dp)`) enforce `>= 48 dp` minimum touch targets.

## Worldwide place search and recent destinations

Place search is worldwide and online-only through `PlaceSearchAdapter`:
`AndroidNominatimEngine` queries the public OSM Photon endpoint, and
`IosAppleLocalSearchEngine` uses Apple MapKit `MKLocalSearch`. No transit dataset is
bundled and no search-result cache is kept. Queries shorter than 3 characters (including
a blank query) return immediately without a network request. A blank query shows recent
destination quick-picks only, so the sheet has no empty-results panel until the user
types. A query returns at most 8 candidates after deduplication within 50 meters.

Every provider request sends the typed query plus the map center rounded to 2 decimal
places (roughly 1 km), never the precise position. Ranking uses the exact unrounded
coordinates on-device with `HaversineCalculator` (falling back to the current map
viewport center when a live 30 s location fix expires so search distance badges stay
visible). Provider results, the query, and the
coarse coordinates are never persisted. Category chips (All, Train, Metro, Transit) are
best-effort client-side filters driven by provider categories plus
`PlaceCategoryHeuristics`; places without matching metadata stay in the general or
transit-stop bucket. Offline, a non-blank query returns no results while recents and map
taps keep working.

The map picker and place search also support recent destination quick-picks
via `RecentDestinationsPort` backed by `PersistentRecentDestinationsAdapter` in
platform key-value storage. Destinations recorded from main-map browse selection
(`onBrowsePlaceSelected`), picker search selection (`onPickerPlaceSelected`), or
confirmed picker placement (`FjalarViewModel` driving `MapPickerScreen` in
`FjalarApp.kt`) are stored in an LRU list capped at 5 entries,
deduplicated by ID, case-insensitive trimmed name, and coordinate proximity within 50 meters.
In `SearchPlacesSheet`, when the query is blank and recent destinations exist,
a vertical scrollable list renders full-width destination rows with category icons,
single-line ellipsized names, optional distance pills, dividers, and
accessible content descriptions. Tapping a recent row centers the map (or
focuses the picker marker and enables confirmation) while keeping all coordinates
and history 100% on-device without persisting raw typed search queries.

## Architecture and distribution

Koin 4.2.2 assembles application modules while domain and ordinary application
classes stay constructor-injected. Persistence is JSON in platform key-value
storage. See [Dependency injection](dependency-injection.md).

For published releases with valid version tags, the `android` job builds a
signed APK and the `verify` job runs multiplatform verification (`./kotlin test`,
`./kotlin check`) plus unsigned physical-device iOS packaging
(`scripts/build_release_ios.sh`). After both jobs pass, the `publish` job verifies
and attaches the signed Android APK (`fjalar-<tag>-android.apk`), `SHA256SUMS.txt`,
unsigned iOS IPA (`fjalar-<tag>-ios-unsigned.ipa`), and `SHA256SUMS-ios.txt` via
`scripts/upload_release_assets.py`. Signed App Store / TestFlight distribution is not
yet wired. The Xcode project bundle identifier is aligned to canonical
`com.muhammadridha.fjalar` across configurations.
See [Releasing](releasing.md) for signing secrets and workflow details.

## Verification boundaries

The PR #22 Task 8 integration pass ran `./kotlin test`, `./kotlin check`, Android debug
build, and iOS simulator Arm64 debug build. The first test run
exposed two outdated Indonesian alarm-list string expectations; those test
assertions were corrected and the full test rerun passed. String catalog parity
and whitespace checks also passed. The task report retains exact commands, logs,
and exit codes.

| Device and view | Recorded observations (PR #22, PR #33, and `5c87d42` builds) |
| --- | --- |
| Android API 37, 1080 × 2400 | Existing destination pin and radius ring stayed visible after map tap, long press, and nearby pan. Main-map search moved the camera with no search marker. New editor began with no destination; picker search cancellation preserved that absence. Picker pan enabled confirmation; an earlier map-tap attempt was inconclusive because its coordinate and settlement state were not recorded. Android system Back saved a valid draft, discarded an invalid draft with a visible reason, and first hid the open keyboard. A destination-selected blank-name draft and an existing edit with equal start/end times discarded with visible reasons; the existing record retained its original schedule. The slider reached 50 m and 3 km on screen. |
| Android API 37, 320 dp portrait at 1.3 font scale | English Light editor showed destination action, radius presets, and mode text inside the screenshot. The form remained scrollable. |
| Android API 37, landscape at 1.3 font scale | Scrolling exposed the destination action and radius controls. The original size, font scale, and rotation settings were restored. |
| Android API 37 Emulator (`Medium_Phone_API_37.0`, `5c87d42` debug APK) | External `adb shell am start -n com.muhammadridha.fjalar/.AlarmActivity` rejected with `SecurityException` (`not exported`). Live `uiautomator dump` verified `>= 48 dp` (`126 px` at `420 dpi`) touch targets and semantic `content-desc` labels across `SearchPlacesSheet`, `FrostedBottomNavigationBar`, `ConfigureAlarmScreen` (5-column radius/day rows), `MapPickerScreen`, and armed `MainActivity` HUD. With temporary PIN `1234` locked (`showing=true, inputRestricted=true`) and `USE_FULL_SCREEN_INTENT` granted via `appops`, simulated GPS entering the 500 m radius launched `AlarmActivity` in isolated task `t40` (`occluded=true`) with `FLAG_SECURE` (`SECURE`) and `VISIBILITY_PRIVATE` notification + generic `publicVersion`. `Recents` kept keyguard locked/occluded; rotation (`ROTATION_90` → `ROTATION_0`) kept `AlarmActivity` over keyguard. `Snooze (+2 min)` scheduled exact `RTC_WAKEUP` (`window=0`) in `AlarmManager`; re-locking with PIN `1234` and waiting 2 minutes automatically re-launched `AlarmActivity` (`t42`, `FLAG_SECURE`) over keyguard; single-tap on the Slide-to-Stop thumb did not dismiss. Temporary PIN was cleared afterward. |
| Android API 37 Emulator (`Medium_Phone_API_37.0`, pre-v0.3.2 debug APK) | Visual inspection and `uiautomator dump` verified Light-mode (`Carto Positron`) 500 m geofence fill and stroke rendering over roads, rail lines, and building footprints north of Monas while street and place labels (`Jalan Medan Merdeka Utara`, `Jalan Veteran III`, `MONAS UTARA`), user halo/dot, and red destination pin remained legible on top. `RingingAlertOverlay` Snooze pill measured `126 px` (`48.0 dp` at `420 dpi`) with `"📍"` absent from accessibility nodes; HUD Search Pill, `LanguageDropdown`, and Theme Toggle exposed clean clickable `content-desc` labels (`Search places…` / `Cari tempat…`, `Language: English (EN)` / `Bahasa: Indonesia (ID)`, `Switch to light mode` / `Beralih ke mode gelap`) with zero emoji glyph nodes. `MapPickerScreen` Confirm button switched from `enabled=false` (`Pilih tujuan`) to `enabled=true, clickable=true` (`Konfirmasi tujuan -6.1726, 106.8272`) after panning without a `"✓"` child text node. `AlarmCard` kept identical card and downstream card bounds (`[42,273][1038,788]` / `[42,814][1038,1329]` at `420 dpi`) across Disabled, Enabled (`Skip next scheduled alarm`), and Skipped (`Done for this window`); at compact `320 dp` (`540 dpi`) and `1.3x` `font_scale`, `Lewati alarm berikutnya` wrapped cleanly to 2 lines beside unclipped `Hapus` (`162 px` / `48.0 dp` tall) before restoring `1080 × 2400`, `420 dpi`, and `1.0` `font_scale`. |
| Android API 37 Emulator (`Medium_Phone_API_37.0`, zoom-tiered OSM POI debug APK) | Verified zoom-tiered OSM Carto-style POI symbols and color-coded labels on existing `Carto Positron` (Light) and `Carto Dark Matter` (Dark) tiles with zero missing sprites or MapLibre expression errors in `logcat`: around Kayu Manis (`-6.1994, 106.8619`) at `z15` Light and Dark rendered hospital `H` badges (`Primaya Evasari Hospital`, `RSUD Matraman`), rail transit (`Matraman`), crimson medical cross (`Puskesmas Kelurahan Kayu Manis`), brown ATM card (`ATM Mandiri`), crescent-and-star mosques (`Masjid`/`Mushola`), graduation-cap schools, civic pillars (`Polsek Cempaka Putih`), and magenta market bag (`Pasar Jaya Palmeriam`); zoomed `z17+` around Kayu Manis (`Jalan Kayu Manis Timur`) and Thamrin/Bundaran HI (`-6.1950, 106.8230`) revealed local restaurants (`Oku`, `Skye`), hotels (`Hotel Indonesia Kempinski`), banks/money changers (`BNI`, `Sisi Money Changer`), civic posts, bus stops (`Plaza Indonesia`, `Wisma Nusantara`), and landmarks (`Monumen Selamat Datang`) while street names stayed intact. Light-mode 500 m Map Picker geofence over Kayu Manis rendered fill and stroke above roads and buildings with street names, OSM POI symbols/labels, user blue dot, and red destination pin cleanly visible on top. |
| iPhone 17, iOS 27.0 | Fresh app showed Indonesian Dark and English Light maps/editors. Browse search moved the camera without selecting a destination. Picker search candidate canceled without draft change and confirmed into the draft. Toolbar Back created a valid alarm, returned from an unchanged editor, saved a changed radius, and showed the invalid-draft reason. A destination-selected blank-name draft and an existing edit with equal start/end times discarded with visible reasons; the existing list retained its original 07:00–09:30 window and 200 m radius. The 50 m preset and intermediate slider values changed on screen; exact slider maximum was not reached through the available coordinate input. |
| iPhone 17 Simulator, iOS 27.0 (`5c87d42` Debug `.app`) | Confirmed `CFBundleIdentifier = com.muhammadridha.fjalar`, `UIBackgroundModes = [location, audio, fetch]`, `BGTaskSchedulerPermittedIdentifiers = [com.muhammadridha.fjalar.refresh]`, Dark Mode HUD rendering, notification prompt, and clean background (`com.apple.Preferences`) → foreground lifecycle on the same process without crashes. |
| iPhone Duo, iOS 27.2 | Fresh app launched and rendered its primary display behind a first-run notification prompt. The second display capture was black; interactive layout checks were not completed. |
| Android API 34 (Cloud Device, 720 × 1616) | Verified PR #33 debug APK via Ramus: foreground location and notification permissions accepted; background location rationale dismissed. MapLibre canvas edge-to-edge; Light mode and Dark mode tokens toggled cleanly. Real-time language switch between English and Bahasa Indonesia verified across HUD, navigation, and day chips (`Sn Sl Rb Km Jm Sb Mg`). The earlier offline station autocomplete and Metro/MRT filtering claim was superseded by worldwide online search (verified on the local API 37 emulator row below); current builds return no search results without network. MapPicker pin placement confirmed destination; alarm created with 200m preset and custom label; alarm card rendered with active badge; skip today transitioned to `COMPLETED_FOR_WINDOW` once-per-window state; alarm delete confirmation dialog cancelled and confirmed cleanly with safe empty-state return. |
| Android API 37 Emulator (`Medium_Phone_API_37.0`, worldwide-search debug APK) | Blank `SearchPlacesSheet` showed only category chips, with no results panel and no recent quick-picks after app data was cleared. `Manggarai` returned four Jakarta station rows with on-device distances (`1.6 km`); selecting the Train chip kept them. `Shibuya Station` returned `Shibuya, Tokyo, Japan` (`5782 km`) and `Berlin Hauptbahnhof` returned Berlin Hbf rows (`10790.2–10790.3 km`) while Train stayed selected, confirming worldwide results and best-effort category filtering. With Wi-Fi and mobile data disabled, `Manggarai` showed the `No places found` panel without a crash. Cancelling during a query left no result list behind. Recent quick-picks and picker map taps were not re-exercised in this pass because app data had been cleared. |
| Android API 37 Emulator (`Medium_Phone_API_37.0`, PR #59 debug APK) | Visually verified live Dark-to-Light Carto style reload (`Carto Dark Matter` to `Carto Positron`), Light `ConfigureAlarmScreen` `#F2F3F8` canvas with `1 dp` glass rims on unselected radius presets and day chips, soft keyboard open on `ConfigureAlarmScreen` with `FrostedBottomNavigationBar` hidden in both screenshot and `uiautomator dump`, and Dark cold-start splash release into the settled map view. |
| Ramus Cloud Android (`main` @ `e1116ae`) | Verified live cloud E2E: foreground location (`Precise`) and notification permissions granted, background-location prompt dismissed, live OSM Photon search (`Manggarai`, `Shibuya Station`) with on-device distances (`13820.3 km`, `8130.3 km`), vertical `Recent destinations` list populated from Main Map browse selections, `MapPickerScreen` recent selection and `Commute Test` (`200 m`) saved, `AlarmCard` `Skip next scheduled alarm` transition to `Done for this window` without layout shift, Delete confirmation returning to `No Alarms Yet` (`0 Active`), and zero `AndroidRuntime` crashes. |
| Android CLI (`Medium_Phone_API_37.0`, `main` @ `9b76516` debug APK, 2026-11-24) | Verified 4/4 OS × App cold-start splash matrix after PR #63 (`OS Light × App Light/Dark`, `OS Dark × App Light/Dark` visually inspected across force-stops), live Carto Positron / Dark Matter style and `EN <-> ID` HUD toggle, OSM Photon worldwide search with vertical `Recent destinations`, `ConfigureAlarmScreen` (`FrostedBottomNavigationBar` absent from `uiautomator dump` with IME open), and `Alarms` tab Skip (`0 px` `Delete` button vertical shift), edit discard confirmation guard, and Delete flow. |
| Ramus Cloud Android (`main` @ `9b76516` debug APK, 2026-11-24) | Verified end-to-end cloud Scenarios A–D: `EN <-> ID` switch, Carto Light <-> Dark live reload, `"Ma"` `<3` char gate, `"Manggarai"` and `"Shibuya Station"` search + `Metro / MRT` filter + `"zzzzqqqq"` empty state, vertical `Recent destinations` synced across Main Map and `MapPickerScreen`, geofence over streets/buildings, `200m` preset, `0 px` `Delete` shift on Skip, edit discard confirmation guard, alarm switch toggle, Delete returning to `No Alarms Yet`, and `0` `AndroidRuntime` crashes in `android_logcat`. |
| `iPhone 17` Simulator (`iOS 27.2`, `main` @ `9b76516` & PR #64 `c6e18f3` debug `.app`, 2026-11-24) | Verified `LaunchScreen` Light/Dark appearances, live `MKLocalSearch` (`Manggarai`, `Stasiun Manggarai`, `Shibuya Station`, `Station`, `Train` filter, `"zxqjvkwp99123"` empty state), vertical `Recent destinations` synced across Main Map and `MapPickerScreen`, keyboard-open form layout on `ConfigureAlarmScreen` with bottom dock hidden, `200m` preset & scheduled day chips, `Alarms` Skip (`0.0 px` `Delete` shift), and edit discard guard on `9b76516`; visually confirmed on PR #64 (`c6e18f3`) that ` Maps Legal` clears above `FrostedBottomNavigationBar` (Light/Dark) and `✓ Choose destination` with centered crosshair/pin, `MapPickerScreen` HUD/CTA stay hidden behind `SearchPlacesSheet`, and `Saved Alarms` empty-state subtitle matches `+ Add Alarm` / `+ Tambah alarm`. |

These observations do not establish map-bearing rotation or reproduce the earlier
missing-pin report. Still unverified on Android: completing the locked-screen
Slide-to-Stop swipe gesture, physical TalkBack speech audio, foldable postures,
map-bearing rotation, device injection of persistence failure, and older Android
APIs (API 26–33). Still unverified on iOS: physical iPhone `CLCircularRegion`
background wakeups, real OS `BGAppRefreshTask` heuristics, and VoiceOver speech
audio. The failure and ringing exit semantics
have deterministic source-level tests, which are separate from native-device
evidence.

The later wording and notification-language changes have not been verified on
devices with different app and OS languages. The PR #22 device observations above
do not establish that additional coverage.
