"""Release numbering and stale metadata detection without changing this release."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts/versioning.py"
SPEC = importlib.util.spec_from_file_location("pocket_versioning", SCRIPT)
v = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = v
SPEC.loader.exec_module(v)


class VersionTests(unittest.TestCase):
    def test_numbering_and_rollovers(self):
        for old, expected in [(v.Version(0, 1, "beta"), "0.02"),
                              (v.Version(0, 9, "beta"), "0.10"),
                              (v.Version(0, 99, "beta"), "1.00"),
                              (v.Version(1, 99, "stable"), "2.00")]:
            new = old.next()
            self.assertEqual(new.version_name, expected)
            self.assertEqual(new.version_code, old.version_code + 1)
        self.assertEqual(v.Version(0, 1, "beta").apk_name, "hermes-pocket-v0.01.apk")

    def test_major_and_channel_promotion_increase_code(self):
        old = v.Version(0, 42, "beta")
        new = old.next(major=True, channel="stable")
        self.assertEqual(new.as_dict(), {"major": 1, "minor": 0, "channel": "stable"})
        self.assertGreater(new.version_code, old.version_code)
        self.assertEqual(old.next(channel="stable").version_code, 43)

    def test_invalid_releases(self):
        for args in [(0, 0, "beta"), (-1, 1, "beta"), (0, 100, "beta"),
                     (0, True, "beta"), (True, 1, "beta"), (0, 1, "rc"),
                     (21_000_000, 1, "beta")]:
            with self.subTest(args=args), self.assertRaises(ValueError):
                v.Version(*args)

    def test_sync_preserves_manifest_and_check_rejects_stale_metadata(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            version = v.Version(0, 1, "beta")
            (root / "version.json").write_text(json.dumps(version.as_dict()))
            java, manifest = v.generated_paths(root)
            manifest.parent.mkdir(parents=True)
            original = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.chanho.hermes"><application android:allowBackup="false" /></manifest>'
            manifest.write_text(original)
            v.sync_version(root)
            self.assertEqual(v.check_version(root), version)
            self.assertIn('android:allowBackup="false"', manifest.read_text())
            first = manifest.read_bytes()
            v.sync_version(root)
            self.assertEqual(first, manifest.read_bytes())
            (root / "version.json").write_text(json.dumps(version.next().as_dict()))
            with self.assertRaisesRegex(ValueError, "stale"):
                v.check_version(root)
            v.sync_version(root)
            self.assertEqual(v.check_version(root).version_name, "0.02")
            java.write_text(java.read_text().replace("0.02", "0.01"))
            with self.assertRaisesRegex(ValueError, "stale"):
                v.check_version(root)

    def test_version_json_rejects_unknown_fields(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "version.json").write_text('{"major":0,"minor":1,"channel":"beta","versionCode":77}')
            with self.assertRaisesRegex(ValueError, "only"):
                v.load_version(root)


if __name__ == "__main__":
    unittest.main()
