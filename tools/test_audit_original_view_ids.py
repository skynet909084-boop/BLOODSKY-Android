import csv
from pathlib import Path
import tempfile
import unittest

from audit_original_view_ids import audit


class OriginalViewIdAuditTests(unittest.TestCase):
    def test_public_id_is_cross_referenced_to_candidate_and_runtime(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            original_res = root / "original" / "res"
            original_java = root / "original" / "jadx" / "sources"
            bloodsky_res = root / "bloodsky" / "res"
            bloodsky_java = root / "bloodsky" / "java"
            runtime = root / "runtime"
            for folder in (
                original_res / "values", original_res / "layout",
                original_java / "ru/himik/nightscream/kascada",
                bloodsky_res / "layout",
                bloodsky_java / "com/bloodsky/hmi", runtime,
            ):
                folder.mkdir(parents=True, exist_ok=True)
            (original_res / "values/public.xml").write_text(
                '<resources><public type="id" name="tab_button" id="0x7f090001" />'
                '<public type="id" name="unused" id="0x7f090002" /></resources>',
                encoding="utf-8",
            )
            (original_res / "layout/palette.xml").write_text(
                '<ImageView xmlns:android="http://schemas.android.com/apk/res/android" '
                'android:id="@+id/tab_button" />', encoding="utf-8"
            )
            (original_java / "ru/himik/nightscream/kascada/Palette.java").write_text(
                'findViewById(R.id.tab_button);', encoding="utf-8"
            )
            (bloodsky_res / "layout/palette.xml").write_text(
                '<ImageView xmlns:android="http://schemas.android.com/apk/res/android" '
                'android:id="@+id/kc_picker_tab_button" />', encoding="utf-8"
            )
            (bloodsky_java / "com/bloodsky/hmi/Palette.java").write_text(
                'findViewById(R.id.kc_picker_tab_button);', encoding="utf-8"
            )
            (runtime / "state-001.xml").write_text(
                '<hierarchy><node resource-id="ru.kascada.modbus:id/tab_button" /></hierarchy>',
                encoding="utf-8",
            )
            output = root / "inventory.csv"
            counts = audit(original_res, original_java, bloodsky_res, bloodsky_java,
                           output, runtime)
            with output.open(encoding="utf-8-sig", newline="") as stream:
                rows = {row["resource_id"]: row for row in csv.DictReader(stream)}
            self.assertEqual(2, counts["public_ids"])
            self.assertIn("palette.xml", rows["tab_button"]["original_layouts"])
            self.assertIn("kc_picker_tab_button", rows["tab_button"]["bloodsky_candidate_ids"])
            self.assertIn("state-001.xml", rows["tab_button"]["runtime_screenshot_or_tree_files"])
            self.assertEqual("public_only_unclassified", rows["unused"]["review_status"])


if __name__ == "__main__":
    unittest.main()
