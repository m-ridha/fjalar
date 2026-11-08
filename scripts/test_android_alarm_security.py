"""Check the Android component security boundary, independently of UI visibility."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class AndroidAlarmSecurityTests(unittest.TestCase):
    """Verify Android manifest and activity security invariants for locked-device alarms."""

    def setUp(self):
        """Parse AndroidManifest.xml and index activity declarations."""
        self.application = ET.parse(ROOT / "androidApp/src/AndroidManifest.xml").getroot().find("application")
        self.activities = {
            activity.attrib[ANDROID + "name"]: activity
            for activity in self.application.findall("activity")
        }

    def test_only_restricted_activity_can_show_over_keyguard(self):
        """Verify only AlarmActivity declares showWhenLocked in AndroidManifest.xml."""
        privileged = []
        for name, activity in self.activities.items():
            if activity.attrib.get(ANDROID + "showWhenLocked") == "true":
                privileged.append(name)
        self.assertEqual([".AlarmActivity"], privileged)
        main = (ROOT / "androidApp/src/MainActivity.kt").read_text()
        for unsafe_api in ("setShowWhenLocked", "FLAG_SHOW_WHEN_LOCKED", "FLAG_DISMISS_KEYGUARD", "requestDismissKeyguard"):
            self.assertNotIn(unsafe_api, main)

    def test_alarm_task_is_private_isolated_and_absent_from_recents(self):
        """Verify AlarmActivity is non-exported, excluded from recents, and uses singleTask."""
        alarm = self.activities[".AlarmActivity"]
        self.assertEqual("false", alarm.attrib[ANDROID + "exported"])
        self.assertEqual("true", alarm.attrib[ANDROID + "excludeFromRecents"])
        self.assertEqual("singleTask", alarm.attrib[ANDROID + "launchMode"])
        self.assertNotEqual(
            self.activities[".MainActivity"].attrib.get(ANDROID + "taskAffinity", "com.muhammadridha.fjalar"),
            alarm.attrib[ANDROID + "taskAffinity"],
        )
        self.assertEqual([], alarm.findall("intent-filter"))

    def test_notification_has_no_route_to_private_main_activity(self):
        """Verify alarm notifications target AlarmActivity with immutable PendingIntents."""
        ringer = (ROOT / "shared/src@android/adapters/ringer/AndroidSystemAlarmRingerAdapter.kt").read_text()
        self.assertIn('DEFAULT_ACTIVITY_CLASS_NAME = "com.muhammadridha.fjalar.AlarmActivity"', ringer)
        self.assertNotIn("getLaunchIntentForPackage", ringer)
        self.assertIn("PendingIntent.FLAG_IMMUTABLE", ringer)
        self.assertIn(".setPublicVersion(publicNotification)", ringer)
        self.assertIn(".setVisibility(Notification.VISIBILITY_PRIVATE)", ringer)

    def test_restricted_activity_has_no_normal_navigation_or_unlock_bypass(self):
        """Verify AlarmActivity cannot navigate to MainActivity or dismiss keyguard."""
        activity = (ROOT / "androidApp/src/AlarmActivity.kt").read_text()
        for forbidden in ("MainActivity", "FjalarViewModel", "NavController", "startActivity(", "requestDismissKeyguard", "FLAG_DISMISS_KEYGUARD"):
            self.assertNotIn(forbidden, activity)
        self.assertNotRegex(activity, r"\bApp\s*\(")
        self.assertIn("hideDestinationDetails = true", activity)
        self.assertIn("finishAndRemoveTask()", activity)
        self.assertIn("state.alarm == null && !state.isOperationPending", activity)
        self.assertIn("FLAG_SECURE", activity)
        self.assertIn("intent.action != ACTION_SHOW_ALARM", activity)

    def test_locked_device_window_flags_and_lifecycle_on_dismiss(self):
        """Verify locked-device window flags on AlarmActivity and absence on MainActivity."""
        activity = (ROOT / "androidApp/src/AlarmActivity.kt").read_text()
        self.assertIn("setShowWhenLocked(true)", activity)
        self.assertIn("setTurnScreenOn(true)", activity)
        self.assertIn("FLAG_SHOW_WHEN_LOCKED", activity)
        self.assertIn("FLAG_TURN_SCREEN_ON", activity)
        self.assertIn("FLAG_SECURE", activity)
        self.assertIn("finishAndRemoveTask()", activity)
        # Ensure keyguard window flags never leak to MainActivity
        main_activity = (ROOT / "androidApp/src/MainActivity.kt").read_text()
        for flag in ("setShowWhenLocked", "setTurnScreenOn", "FLAG_SHOW_WHEN_LOCKED", "FLAG_TURN_SCREEN_ON"):
            self.assertNotIn(flag, main_activity)


if __name__ == "__main__":
    unittest.main()
