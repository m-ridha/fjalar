# Security boundaries and verification

## Android keyguard and alarm presentation

The ordinary `MainActivity` must remain behind Android's keyguard. It must not
set `showWhenLocked`, `turnScreenOn`, `FLAG_SHOW_WHEN_LOCKED`, or request keyguard
dismissal to expose application navigation. Authentication belongs to the system;
Fjalar does not implement its own PIN or password screen.

The alarm presentation uses a separate, non-exported `AlarmActivity` and task.
Only this restricted activity may show above the keyguard. Its ViewModel retains
in-flight actions across activity recreation. The task waits for action side
effects to finish before closing. It must validate its
explicit alarm intent against the current ringing state, present generic alarm
copy instead of a saved destination or location, and expose only the existing
slide-to-stop and two-minute snooze actions. Ending ringing must finish that task
without launching the main application. Notification/full-screen intents route
to this restricted surface rather than to ordinary application navigation.
The notification uses private visibility with a generic public version.

These are activity/task boundaries, not a conditional overlay protecting an
otherwise accessible navigation tree. Preserve this distinction in future
notification and deep-link changes. Android can restrict full-screen notifications;
users may need to grant notification and full-screen alarm access in system settings.

### Device verification matrix

Use a PIN/password-protected test emulator or device. Verify on API 26 (legacy
window flags), API 27-28 (activity APIs), and a current Android release. Do not
substitute source or JVM tests for keyguard/task-stack behavior.

1. Save an alarm with a recognizable private label and destination. Leave the
   normal app open, lock the device, and trigger the alarm using a simulated
   location entering its radius during its active window.
2. Confirm only generic ringing UI is visible. No map, destination label,
   coordinates, alarms list, editor, or general navigation may appear.
3. Try Back, Home, notification taps, and Recents. The locked device must not
   expose normal application controls. A single tap must not dismiss ringing.
4. Complete the slide-to-stop gesture. Audio/vibration must stop, scheduled-window
   suppression must remain intact, and the restricted task must close to the
   system lock screen. Repeat with snooze. Verify its two-minute deadline persists
   and the next alarm still has only restricted actions.
5. Unlock normally and confirm the main application remains usable. Repeat while
   the app was backgrounded or its process was recreated before the alarm.
6. Rotate/recreate the restricted activity while ringing, and deliver missing,
   malformed, stale, and non-ringing alarm IDs from an in-app test harness. It
   must fail closed without revealing navigation or mutating another alarm.
   An external `adb shell am start -n
   com.muhammadridha.fjalar/.AlarmActivity` must be rejected as non-exported.
7. Repeat when another alarm becomes the ringing owner, and when dismiss/snooze
   fails: stale UI must not control another alarm. Failures must allow retry.

Record the device/API, secure-keyguard state, exact steps, and observed results.
Device checks that have not been executed remain unverified.

## GitHub Actions workflow-command injection

A PR title is attacker-controlled even when passed safely through `env`. The
runner interprets commands in standard output, including legacy `##[...]`
commands embedded after a fixed prefix. Shell quoting alone does not prevent
that second interpreter from registering masks or annotations.

`pr-check.yml` suspends command processing using a cryptographically random,
per-invocation 256-bit token (`secrets.token_hex(32)`) before printing `PR_TITLE`
with a fixed `printf` format. It resumes with the same token before emitting
trusted conventional-title diagnostics. The title never becomes executable shell
source. Do not replace the token with a fixed value or print other untrusted
metadata outside this boundary.

The workflow audit covers all run scripts: event title is passed through the
environment. Concurrency metadata and action/cache inputs are not interpolated
into executable shell source. Permissions and build steps remain unchanged.

```sh
python3 scripts/test_android_alarm_security.py
python3 scripts/test_workflow_command_safety.py
```

The regression test runs the actual extracted shell and a local model of modern
and legacy runner-command parsing. It covers multiline/CR payloads, masks,
annotations, nested stop commands, shell metacharacters, token freshness, and
resumption of later diagnostics. This is contract-level verification rather
than execution on a hosted GitHub runner.

## References

- [Android activity keyguard APIs](https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean))
- [Android urgent notification presentation](https://developer.android.com/develop/ui/compose/notifications/create-notification)
- [GitHub stop/resume workflow commands](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands#stopping-and-starting-workflow-commands)
- [Actions runner command parser](https://github.com/actions/runner/blob/main/src/Runner.Common/ActionCommand.cs)
- [Actions runner command manager](https://github.com/actions/runner/blob/main/src/Runner.Worker/ActionCommandManager.cs)

## Recorded verification for PR #18

On the API 37 emulator, the debug application started through the shipping
Android Koin module, and an external attempt to launch `AlarmActivity` was
rejected with Android's non-exported-component `SecurityException`. With a
temporary secure PIN and lockdown enabled, launching `MainActivity` left the
keyguard showing, input-restricted, and unoccluded (`deviceLocked=1`,
`showing=true`, `inputRestricted=true`, `occluded=false`). The temporary PIN was
removed afterward. This verifies startup and the ordinary-app keyguard boundary;
it does not reproduce a full locked-device ringing/dismiss/snooze sequence or
older API behavior. Those scenarios remain in the manual matrix above.
