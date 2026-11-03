"""Check real Compose catalogs and the shared runtime lookup stay in parity."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class StringCatalogTests(unittest.TestCase):
    def test_catalog_keys_values_and_placeholders(self):
        catalogs = {}
        for folder in ("values", "values-in", "values-id"):
            strings = ET.parse(ROOT / "shared/composeResources" / folder / "strings.xml").getroot()
            entries = strings.findall("string")
            catalog = {item.attrib["name"]: "".join(item.itertext()) for item in entries}
            self.assertEqual(len(entries), len(catalog), f"Duplicate keys in {folder}")
            self.assertTrue(all(catalog.values()), f"Blank value in {folder}")
            catalogs[folder] = catalog
        baseline = catalogs["values"]
        pattern = re.compile(r"%(?:\d+\$)?[sd]")
        for folder, catalog in catalogs.items():
            self.assertEqual(baseline.keys(), catalog.keys(), folder)
            for key in baseline:
                self.assertEqual(pattern.findall(baseline[key]), pattern.findall(catalog[key]), f"{folder}/{key}")
        self.assertEqual(catalogs["values-id"], catalogs["values-in"])
        runtime = (ROOT / "shared/src/localization/FjalarStrings.kt").read_text()
        # Every lookup entry must match its real catalog, including literal placeholders.
        entries = re.findall(r'FjalarStringKey\.([A-Z_]+) to "((?:[^"\\]|\\.)*)"', runtime)
        self.assertEqual(0, len(entries) % 2)
        entry_count = len(entries) // 2
        for index, folder in enumerate(("values", "values-id")):
            for key, value in entries[index * entry_count:(index + 1) * entry_count]:
                self.assertIn(key.lower(), catalogs[folder], f"{folder}/{key}")
                normalize = lambda text: re.sub(r"%(?:\d+\$)?([sd])", r"%\1", text)
                self.assertEqual(normalize(catalogs[folder][key.lower()].replace("\\'", "'")), normalize(value.replace('\\$', '$').replace('\\"', '"')), f"{folder}/{key}")
        self.assertEqual("Delete", baseline["dialog_delete_confirm"])
        self.assertEqual("Hapus", catalogs["values-id"]["dialog_delete_confirm"])


if __name__ == "__main__":
    unittest.main()
