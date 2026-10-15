"""Machine-verifiable tests for BLOODSKY vs KaScada Craft 1.0.7 parity matrix."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MATRIX_FILE = ROOT / "docs" / "BLOODSKY_PARITY_MATRIX.json"
SOURCE_FILE = ROOT / "app" / "src" / "main" / "java" / "com" / "bloodsky" / "hmi" / "MainActivity.java"


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

    def test_implemented_features_have_source_evidence(self):
        # Verify that key items marked as implemented exist in MainActivity.java
        self.assertIn("class MainActivity", self.source)
        self.assertIn("persistProject()", self.source)
        self.assertIn("loadProject()", self.source)
        self.assertIn("readModbusRegisters(", self.source)
        self.assertIn("evaluateFbd(", self.source)


if __name__ == "__main__":
    unittest.main()
