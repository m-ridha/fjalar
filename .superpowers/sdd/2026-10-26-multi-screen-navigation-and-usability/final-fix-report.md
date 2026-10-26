# Final review fix report

Semantic source verified: `33917f9` on `feat/multi-screen-navigation-and-usability`. No push or merge performed. This report and the final route EOF cleanup do not change runtime behavior.

## Four final-branch review findings

| Finding | Resolution |
| --- | --- |
| Historical Android cache can falsely trigger an alarm | Removed startup last-known-location dispatch into the alarm service. Map-only requests never enter alarm evaluation. Armed service fixes additionally reject missing, future, or older-than-30-second monotonic timestamps. |
| Delete crashes with incomplete draft fields | Editor confirmation receives the persisted alarm selected by draft identity. It never validates/converts partially edited fields just to delete. |
| Scheduled to one-time remains idle | Domain save reconciliation arms enabled mode conversions, preserves disabled/ringing states, and retains unfinished completion suppression. Converted one-time suppression schedules a wakeup and arms only at its deadline. |
| Initial/recenter location remains stale or absent | Android requests a current foreground map fix for startup/permission grant and every recenter. Requests end after the first fresh fix, cancellation, replacement, screen disposal, activity stop, or a 15-second timeout. Continuous foreground-service tracking remains controlled by armed alarms. |

## Additional review dispositions

All forwarded CodeRabbit findings were inspected against source and addressed; duplicate address/failure findings share the fixes below, and cached-location/invalid-delete duplicates share the four fixes above.

- Formatting scans the original template once, preserving user labels containing literal `%s`, `%d`, or positional placeholders.
- Leaving the editor clears its operation error; delayed operations retain the existing revision ownership guard.
- The picker is explicitly crosshair-only. Native camera movement updates the confirmed center; a separate tap/drag pin cannot disagree with the crosshair or force the initial zoom. EN/ID/in help text now describes this interaction.
- Moving a draft pin clears its obsolete derived address, including the persistence fallback that previously restored it.
- Navigation helper strings match exact supported route names instead of substrings or unrelated classes.
- The deletion test clears prior scheduler cancellation evidence before deleting, so its assertion is nonvacuous.
- The root measures actual dock height, including navigation insets, and supplies alarm-list scroll clearance.
- Alarm-card deletion has an explicit localized accessibility description using the existing catalog key; its 36 dp visual size is preserved.

Scoped corrective review also identified a delayed first-save/resave identity defect. New drafts now receive stable identities before saving, including search and picker fallback creation. A later save of newer edits updates the same identity rather than creating a timestamp-based duplicate.

## Test-first evidence

The following `./kotlin test` runs were intentionally RED before implementing their corresponding behavior:

- `/tmp/fjalar-final-red.log` and `/tmp/fjalar-final-red-all.log`: missing save-reconciliation, persisted deletion-target, and freshness APIs.
- `/tmp/fjalar-suppression-red.log`: converted completed alarm lost its required wakeup; expected suppression deadline, actual null.
- `/tmp/fjalar-extra-red-2.log`: literal-placeholder corruption, stale editor failure, and lost suppression wakeup reproduced.
- `/tmp/fjalar-route-address-red.log`: route substring matching and stale address retention reproduced.
- `/tmp/fjalar-stable-identity-red.log`: delayed-save/resave regression failed on both JVM and native because new drafts lacked stable identity.

Regression coverage also exercises invalid draft deletion identity, freshness boundary/future/missing timestamps, delayed recenter fixes with no initial cache and subsequent movement, disabled/ringing mode conversion, and suppression expiry. Native camera gesture behavior, request cancellation, dock geometry, and accessibility semantics were source-reviewed; this report does not claim physical-device verification of those interactions.

An overlapping intermediate build produced a native ZIP/cache failure and stale variant results. These intermediate results were not accepted. `./kotlin clean` completed successfully before the final clean verification sequence. After the final wording change, the entire sequence below was rerun serially with semantic source frozen at `33917f9`.

## Fresh final commands and results

| Exact command | Result | Evidence |
| --- | --- | --- |
| `./kotlin clean` | Exit 0, clean successful | `/tmp/fjalar-final-clean.log` |
| `./kotlin test` | Exit 0; 215 Android/JVM + 209 iOS Simulator = **424 passed**, zero failures | `/tmp/fjalar-final-test.log` |
| `./kotlin check` | Exit 0, `Check successful`; 215 Android/JVM × 2 + 209 native × 2 = **848 passed**, zero failures | `/tmp/fjalar-final-check.log` |
| `python3 scripts/test_generated_icons.py` | Exit 0; **6 passed** | `/tmp/fjalar-final-icons.log` |
| `./kotlin task :androidApp:buildAndroidDebug` | Exit 0; refreshed APK | `/tmp/fjalar-final-apk.log` |

Existing compiler warnings about coroutine/cinterop opt-ins and unnecessary non-null assertions remain non-failing. No dependencies were added.

Final debug APK: `build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk` (57,549,942 bytes).

SHA-256: `d4f2c4851f67612be01e5eff4436a074e30464c409ee00344132a862469470db`.

The parent was notified as soon as this APK was available for independent device acceptance.

## Focused commits

- `9f2d677` — reconcile saved modes and suppression wakeups.
- `507e6e3` — editor identity, literal labels, revision errors, routes, address retention, recenter requests, and regression coverage.
- `66fd227` — bounded map-only location requests and stale armed-fix rejection.
- `61cc948` — measured dock clearance.
- `1f450d0` — crosshair-only selection and stable draft identities.
- `3af3687` — localized alarm-card delete semantics.
- `33917f9` — accurate bilingual picker guidance.

Final hygiene: `git diff --check d311495..HEAD` plus the pending report/EOF diff check passed after trimming the extra route EOF blank line; `git status -s` was inspected before committing only the report and formatting cleanup. Device acceptance and the controller ledger are maintained by the parent.
