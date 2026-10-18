"""Machine-verifiable tests for BLOODSKY vs KaScada Craft 1.0.7 parity matrix."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MATRIX_FILE = ROOT / "docs" / "BLOODSKY_PARITY_MATRIX.json"
SOURCE_FILE = ROOT / "app" / "src" / "main" / "java" / "com" / "bloodsky" / "hmi" / "MainActivity.java"
AUDIT_FILE = ROOT / "docs" / "KASCADA_FULL_PARITY_AUDIT_20261018.md"


class ParityMatrixTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not MATRIX_FILE.exists():
            raise FileNotFoundError(f"Matrix file missing at {MATRIX_FILE}")
        with open(MATRIX_FILE, "r", encoding="utf-8") as f:
            cls.matrix = json.load(f)
        cls.source = SOURCE_FILE.read_text(encoding="utf-8")

    def test_matrix_structure(self):
        self.assertIn("version", self.matrix)
        self.assertIn("categories", self.matrix)
        self.assertGreater(len(self.matrix["categories"]), 0)

        valid_statuses = {"implemented", "partial", "missing", "blocked"}
        item_ids = set()

        for category in self.matrix["categories"]:
            self.assertIn("name", category)
            self.assertIn("items", category)
            for item in category["items"]:
                self.assertIn("id", item)
                self.assertNotIn(item["id"], item_ids, f"Duplicate item ID: {item['id']}")
                item_ids.add(item["id"])
                self.assertIn("name", item)
                self.assertIn("status", item)
                self.assertIn(item["status"], valid_statuses, f"Invalid status: {item['status']}")
                self.assertIn("criteria", item)
                if self.matrix.get("version") == "2.0":
                    self.assertIn("audit_note", item)

    def test_matrix_links_source_backed_audit_and_keeps_known_gaps_open(self):
        self.assertTrue(AUDIT_FILE.exists())
        self.assertIn("docs/KASKADA_COMPONENT_REGISTRY.md", self.matrix["audit_basis"])
        items = {
            item["id"]: item
            for category in self.matrix["categories"]
            for item in category["items"]
        }
        self.assertEqual("missing", items["widget.bitlist"]["status"])
        self.assertEqual("missing", items["widget.voice_announcement"]["status"])
        self.assertEqual("partial", items["widget.sms_in"]["status"])
        self.assertEqual("partial", items["widget.sms_out"]["status"])
        self.assertNotEqual("implemented", items["fbd.latches"]["status"])
        self.assertNotEqual("implemented", items["modbus.rtu_crc"]["status"])

    def test_implemented_features_have_source_evidence(self):
        # Verify that key items marked as implemented exist in MainActivity.java
        self.assertIn("class MainActivity", self.source)
        self.assertIn("persistProject()", self.source)
        self.assertIn("loadProject()", self.source)
        self.assertIn("readModbusRegisters(", self.source)
        self.assertIn("evaluateFbd(", self.source)


if __name__ == "__main__":
    unittest.main()
