#!/usr/bin/env python3
"""Verify shared FjalarStrings, native Android strings.xml, and iOS InfoPlist.strings parity."""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[sd]")
INFO_PLIST_STRINGS_RE = re.compile(r'^"([^"]+)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;', re.MULTILINE)


def normalized_placeholders(text: str) -> list[str]:
    return sorted(re.sub(r"%(?:\d+\$)?([sd])", r"%\1", match) for match in PLACEHOLDER_RE.findall(text))


def parse_runtime_string_catalogs(source: str) -> tuple[list[str], dict[str, str], dict[str, str]]:
    enum_match = re.search(r"enum\s+class\s+FjalarStringKey\s*\{(.*?)\}", source, re.DOTALL)
    if not enum_match:
        raise AssertionError("Could not find FjalarStringKey enum in FjalarStrings.kt")
    enum_body = re.sub(r"//.*", "", enum_match.group(1))
    enum_keys = re.findall(r"\b([A-Z][A-Z0-9_]*)\b", enum_body)

    en_match = re.search(
        r"private\s+val\s+englishCatalog\s*:\s*Map<FjalarStringKey,\s*String>\s*=\s*mapOf\((.*?)\n\s*\)",
        source,
        re.DOTALL,
    )
    id_match = re.search(
        r"private\s+val\s+indonesianCatalog\s*:\s*Map<FjalarStringKey,\s*String>\s*=\s*mapOf\((.*?)\n\s*\)",
        source,
        re.DOTALL,
    )
    if not en_match or not id_match:
        raise AssertionError("Could not find englishCatalog and indonesianCatalog in FjalarStrings.kt")

    entry_pattern = re.compile(r'FjalarStringKey\.([A-Z0-9_]+)\s+to\s+"((?:[^"\\]|\\.)*)"')

    def decode(raw: str) -> str:
        return raw.replace(r"\$", "$").replace(r'\"', '"').replace(r"\n", "\n")

    en_entries = {key: decode(val) for key, val in entry_pattern.findall(en_match.group(1))}
    id_entries = {key: decode(val) for key, val in entry_pattern.findall(id_match.group(1))}
    return enum_keys, en_entries, id_entries


class StringCatalogTests(unittest.TestCase):
    def test_shared_runtime_catalog_exhaustive_parity(self):
        runtime = (ROOT / "shared/src/localization/FjalarStrings.kt").read_text(encoding="utf-8")
        enum_keys, en_catalog, id_catalog = parse_runtime_string_catalogs(runtime)

        self.assertGreater(len(enum_keys), 0)
        self.assertEqual(len(enum_keys), len(set(enum_keys)), "Duplicate keys in FjalarStringKey")
        self.assertEqual(set(enum_keys), set(en_catalog.keys()), "englishCatalog must cover every FjalarStringKey")
        self.assertEqual(set(enum_keys), set(id_catalog.keys()), "indonesianCatalog must cover every FjalarStringKey")

        for key in enum_keys:
            self.assertTrue(en_catalog[key].strip(), f"Blank EN value for {key}")
            self.assertTrue(id_catalog[key].strip(), f"Blank ID value for {key}")
            self.assertEqual(
                normalized_placeholders(en_catalog[key]),
                normalized_placeholders(id_catalog[key]),
                f"Placeholder mismatch for FjalarStringKey.{key}",
            )

        self.assertEqual("Delete", en_catalog["DIALOG_DELETE_CONFIRM"])
        self.assertEqual("Hapus", id_catalog["DIALOG_DELETE_CONFIRM"])
        self.assertEqual("Snooze (+2 min)", en_catalog["RINGING_SNOOZE_ACTION"])
        self.assertEqual("Tunda (+2 mnt)", id_catalog["RINGING_SNOOZE_ACTION"])

    def test_android_native_strings_parity(self):
        catalogs = {}
        for folder in ("values", "values-in", "values-id"):
            strings = ET.parse(ROOT / "androidApp/res" / folder / "strings.xml").getroot()
            entries = strings.findall("string")
            catalog = {item.attrib["name"]: "".join(item.itertext()) for item in entries}
            self.assertEqual(len(entries), len(catalog), f"Duplicate keys in androidApp/res/{folder}")
            self.assertTrue(all(v.strip() for v in catalog.values()), f"Blank value in androidApp/res/{folder}")
            catalogs[folder] = catalog

        expected_keys = {
            "app_name",
            "notif_monitoring_title",
            "notif_monitoring_body",
            "notif_alarm_approaching",
            "notif_action_skip_today",
            "notif_action_stop_monitoring",
            "notification_channel_alarm_name",
            "notification_channel_alarm_desc",
            "notification_channel_monitoring_name",
            "notification_channel_monitoring_desc",
        }
        baseline = catalogs["values"]
        self.assertEqual(expected_keys, set(baseline.keys()))
        for folder, catalog in catalogs.items():
            self.assertEqual(baseline.keys(), catalog.keys(), folder)
            for key in baseline:
                self.assertEqual(
                    normalized_placeholders(baseline[key]),
                    normalized_placeholders(catalog[key]),
                    f"androidApp/res/{folder}/{key}",
                )
        self.assertEqual(catalogs["values-id"], catalogs["values-in"])

    def test_ios_info_plist_strings_parity(self):
        lproj = {}
        for locale in ("en", "id"):
            content = (ROOT / f"iosApp/src/{locale}.lproj/InfoPlist.strings").read_text(encoding="utf-8")
            pairs = INFO_PLIST_STRINGS_RE.findall(content)
            parsed = {key: val for key, val in pairs}
            self.assertEqual(len(pairs), len(parsed), f"Duplicate keys in {locale}.lproj/InfoPlist.strings")
            self.assertTrue(all(v.strip() for v in parsed.values()), f"Blank value in {locale}.lproj/InfoPlist.strings")
            lproj[locale] = parsed

        self.assertEqual(
            {"CFBundleDisplayName", "NSLocationWhenInUseUsageDescription", "NSLocationAlwaysAndWhenInUseUsageDescription"},
            set(lproj["en"].keys()),
        )
        self.assertEqual(lproj["en"].keys(), lproj["id"].keys())


if __name__ == "__main__":
    unittest.main()
