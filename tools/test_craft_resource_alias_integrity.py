"""Contract tests for byte-identical compiled KaScada resource aliases."""
from pathlib import Path
import tempfile
import unittest

from verify_craft_resource_aliases import (
    ESSENTIAL_DRAWABLES, ORIGINAL_SELECTION_ACTIONS, digest, verify,
)


class CraftResourceAliasIntegrityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.decoded = self.root / "decoded"
        self.app = self.root / "app"
        self.drawables = self.decoded / "apktool/res/drawable"
        self.raw = self.decoded / "apktool/res/raw"
        self.alias_drawables = self.app / "src/main/res/drawable-nodpi"
        self.alias_raw = self.app / "src/main/res/raw"
        self.layout = self.app / "src/main/res/layout/kc_selection_actions.xml"
        for path in (self.drawables, self.raw, self.alias_drawables,
                     self.alias_raw, self.layout.parent):
            path.mkdir(parents=True, exist_ok=True)
        for name in ESSENTIAL_DRAWABLES:
            data = ("original png " + name).encode()
            (self.drawables / (name[3:] + ".png")).write_bytes(data)
            (self.alias_drawables / (name + ".png")).write_bytes(data)
        (self.raw / "alarm.wav").write_bytes(b"original alarm")
        (self.alias_raw / "kc_alarm.wav").write_bytes(b"original alarm")
        self.set_layout()
        self.report = self.root / "reports/original-alias.md"

    def set_layout(self, icon_override=None, size_override=None):
        rows = []
        for view_id, icon in ORIGINAL_SELECTION_ACTIONS.items():
            if icon_override and view_id == icon_override[0]:
                icon = icon_override[1]
            dimension = "40.0dp"
            if size_override and view_id == size_override[0]:
                dimension = size_override[1]
            rows.append(
                f'<ImageButton android:id="@+id/{view_id}" '
                f'android:background="@drawable/{icon}" '
                f'android:scaleType="fitXY" '
                f'android:layout_width="{dimension}" '
                f'android:layout_height="{dimension}" />'
            )
        self.layout.write_text(
            '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">'
            + "".join(rows) + "</LinearLayout>", encoding="utf-8"
        )

    def run_verifier(self):
        return verify(self.decoded, self.app, self.report,
                      min_images=len(ESSENTIAL_DRAWABLES), min_sounds=1)

    def test_original_alias_bytes_and_selection_xml_pass(self):
        result = self.run_verifier()
        self.assertEqual(len(ESSENTIAL_DRAWABLES), result["images"])
        self.assertEqual(1, result["sounds"])
        self.assertEqual(result["images"] + result["sounds"], result["verified"])
        self.assertIn("byte for byte", self.report.read_text(encoding="utf-8"))
        self.assertIn(digest(self.drawables / "action_copy.png"),
                      self.report.read_text(encoding="utf-8"))

    def test_modified_compiled_icon_fails_and_does_not_create_report(self):
        (self.alias_drawables / "kc_action_copy.png").write_bytes(b"lookalike icon")
        with self.assertRaisesRegex(ValueError, "bytes changed"):
            self.run_verifier()
        self.assertFalse(self.report.exists())

    def test_missing_compiled_audio_fails(self):
        (self.alias_raw / "kc_alarm.wav").unlink()
        with self.assertRaisesRegex(ValueError, "not compiled"):
            self.run_verifier()

    def test_wrong_icon_action_mapping_fails(self):
        self.set_layout(icon_override=("kc_action_selectActionPaste", "kc_action_cut"))
        with self.assertRaisesRegex(ValueError, "Wrong original icon"):
            self.run_verifier()

    def test_changed_original_control_geometry_fails(self):
        self.set_layout(size_override=("kc_action_selectActionCopy", "36dp"))
        with self.assertRaisesRegex(ValueError, "Original 40dp"):
            self.run_verifier()

    def test_required_original_image_missing_fails(self):
        (self.drawables / "main_background_small.png").unlink()
        (self.alias_drawables / "kc_main_background_small.png").unlink()
        with self.assertRaisesRegex(ValueError, "Required original icons missing"):
            verify(self.decoded, self.app, self.report, min_images=1, min_sounds=1)

    def test_android_editor_really_binds_original_action_bar_and_toolbar(self):
        root = Path(__file__).resolve().parents[1]
        activity = (root / "app/src/main/java/com/bloodsky/hmi/MainActivity.java").read_text(
            encoding="utf-8")
        selection = (root / "app/src/main/java/com/bloodsky/hmi/OriginalSelectionActions.java").read_text(
            encoding="utf-8")
        toolbar = (root / "app/src/main/java/com/bloodsky/hmi/OriginalEditorToolbar.java").read_text(
            encoding="utf-8")
        self.assertIn("OriginalSelectionActions.create(root, this::cutSelectedWidget,", activity)
        self.assertIn("this::copySelectedWidget, this::pasteWidget, this::showAlignDialog", activity)
        self.assertIn("R.layout.kc_selection_actions", selection)
        for view_id, label in (
            ("kc_action_selectActionCut", "Вырезать"),
            ("kc_action_selectActionCopy", "Копировать"),
            ("kc_action_selectActionPaste", "Вставить"),
            ("kc_action_openDialogAlignComponents", "Выравнивание"),
        ):
            self.assertIn("R.id." + view_id, selection)
            self.assertIn('"' + label + '"', selection)
        self.assertIn("R.layout.kc_editor_toolbar", toolbar)
        self.assertIn("R.drawable.kc_stop : R.drawable.kc_start", toolbar)

    def test_ci_checks_pinned_origin_before_build_and_keeps_report(self):
        root = Path(__file__).resolve().parents[1]
        workflow = (root / ".github/workflows/android-apk.yml").read_text(encoding="utf-8")
        prep = (root / "tools/prepare_craft_resources.py").read_text(encoding="utf-8")
        bundle = (root / "tools/bundle_craft_resources.py").read_text(encoding="utf-8")
        self.assertIn("EXPECTED_SHA256 =", prep)
        self.assertIn("Craft reference checksum mismatch", prep)
        self.assertIn('"sha256": hashlib.sha256(item.read_bytes()).hexdigest()', bundle)
        preparation = workflow.index("python tools/prepare_craft_resources.py")
        verification = workflow.index("python tools/verify_craft_resource_aliases.py")
        testing = workflow.index("python -m unittest discover")
        building = workflow.index("gradle assembleDebug")
        self.assertLess(preparation, verification)
        self.assertLess(verification, testing)
        self.assertLess(testing, building)
        self.assertIn("build-reports/kascada-original-alias-integrity.md", workflow)
        self.assertIn("build-reports/*.md", workflow)
        # Old PR heads must not starve the latest APK build in a long patch batch.
        self.assertIn("cancel-in-progress: true", workflow)

    def test_truncated_catalogue_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Incomplete original aliases"):
            self.run_verifier_with_production_thresholds()

    def run_verifier_with_production_thresholds(self):
        return verify(self.decoded, self.app, self.report)


if __name__ == "__main__":
    unittest.main()
