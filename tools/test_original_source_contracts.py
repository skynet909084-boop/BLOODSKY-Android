#!/usr/bin/env python3
"""Tests for original APK source contracts and verification ledger."""
import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest

from prepare_craft_resources import EXPECTED_SHA256
from verify_original_source_contracts import verify_contracts


class OriginalSourceContractsTests(unittest.TestCase):

    def setUp(self):
        self.repo_root = Path(__file__).resolve().parent.parent
        self.zip_path = self.repo_root / "reference/craft-reference.zip"
        self.contracts_path = self.repo_root / "docs/ORIGINAL_APK_SOURCE_CONTRACTS.json"
        self.decoded_dir = self.repo_root / "build/craft-reference"
        self.app_dir = self.repo_root / "app/src/main"
        self.fixtures_dir = self.repo_root / "reference/fixtures"

    def test_reference_zip_sha256(self):
        """Original reference ZIP archive SHA-256 must remain invariant."""
        self.assertTrue(self.zip_path.exists(), "Reference ZIP archive missing")
        actual_sha = hashlib.sha256(self.zip_path.read_bytes()).hexdigest()
        self.assertEqual(actual_sha, EXPECTED_SHA256)

    def test_json_contracts_structure(self):
        """Machine-readable contract JSON must contain all required sections."""
        self.assertTrue(self.contracts_path.exists(), "Contracts JSON missing")
        data = json.loads(self.contracts_path.read_text(encoding="utf-8"))
        self.assertIn("version", data)
        self.assertIn("target_application", data)
        self.assertEqual(data["archive_sha256"], EXPECTED_SHA256)
        self.assertIn("system_registers", data)
        self.assertIn("components", data)
        self.assertIn("project_schema", data)
        self.assertIn("fbd_contracts", data)
        self.assertIn("backlog_stages", data)

    def test_verify_contracts_execution(self):
        """Verify the contract counts against Java and Android resource XML references."""
        res = verify_contracts(
            self.zip_path,
            self.contracts_path,
            self.decoded_dir,
            self.app_dir,
            self.fixtures_dir
        )

        self.assertEqual(res["status"], "OK")
        self.assertEqual(res["total_images"], 152)
        self.assertEqual(res["referenced_images"], 59)
        self.assertEqual(res["unused_images"], 93)

    def test_cited_call_sites_exist_in_main_activity(self):
        """Every cited BLOODSKY call-site method must exist in MainActivity.java."""
        main_activity_path = self.app_dir / "java/com/bloodsky/hmi/MainActivity.java"
        self.assertTrue(main_activity_path.exists(), "MainActivity.java missing")
        main_code = main_activity_path.read_text(encoding="utf-8", errors="replace")

        data = json.loads(self.contracts_path.read_text(encoding="utf-8"))
        for item in data.get("components", []) + data.get("fbd_contracts", []):
            call_site = item.get("bloodsky_call_site", "")
            if call_site.startswith("com.bloodsky.hmi.MainActivity."):
                method = call_site.split(".")[-1]
                pattern = rf'\b{method}\s*\('
                self.assertTrue(
                    re.search(pattern, main_code),
                    f"Cited call-site method '{method}' for {item.get('id', item.get('block'))} not found in MainActivity.java"
                )

    def test_no_matched_status_without_runtime_evidence(self):
        """No component or contract item may claim Matched without paired runtime evidence."""
        data = json.loads(self.contracts_path.read_text(encoding="utf-8"))
        for comp in data.get("components", []):
            self.assertNotEqual(
                comp.get("status"),
                "Matched",
                f"Component {comp.get('id')} incorrectly claims Matched status without evidence"
            )

    def test_system_registers_contract(self):
        """System registers map must include device 0 and required system addresses."""
        data = json.loads(self.contracts_path.read_text(encoding="utf-8"))
        sys_reg = data["system_registers"]
        self.assertEqual(sys_reg["device_id"], 0)
        self.assertEqual(sys_reg["total_registers"], 2000)

        addresses = {item["address"]: item for item in sys_reg["map"]}
        for addr in range(10):
            self.assertIn(addr, addresses, f"System register address {addr} missing from map")

        self.assertIn("ambiguity_blockers", sys_reg)
        self.assertGreater(len(sys_reg["ambiguity_blockers"]), 0)

    def test_synthetic_fixtures_labelled(self):
        """Synthetic test fixtures under reference/fixtures/ must carry synthetic label."""
        synthetic_files = list(self.fixtures_dir.glob("synthetic_*.json"))
        self.assertGreater(len(synthetic_files), 0, "No synthetic fixtures found")
        for syn in synthetic_files:
            content = json.loads(syn.read_text(encoding="utf-8"))
            self.assertIn("_comment", content)
            self.assertIn("SYNTHETIC FIXTURE", content["_comment"])

    def test_synthetic_image_counting_positive_and_negative(self):
        """Verifier must strictly enforce count equality and fail on mismatch."""
        with tempfile.TemporaryDirectory() as tmpdir:
            tmppath = Path(tmpdir)

            # Setup mock decoded dir with 152 files (1 referenced lamp_green, 151 unused)
            mock_decoded = tmppath / "decoded"
            mock_drawables = mock_decoded / "apktool/res/drawable"
            mock_drawables.mkdir(parents=True)
            (mock_drawables / "lamp_green.png").write_bytes(b"fake_png_1")
            (mock_drawables / "lamp_blue.png").write_bytes(b"fake_png_2")
            for i in range(150):
                (mock_drawables / f"dummy_{i}.png").write_bytes(b"dummy")

            # Setup mock app source dir referencing R.drawable.kc_lamp_green
            mock_app = tmppath / "app"
            mock_java_dir = mock_app / "com/bloodsky/hmi"
            mock_java_dir.mkdir(parents=True)
            (mock_java_dir / "MainActivity.java").write_text(
                'package com.bloodsky.hmi;\nclass MainActivity {\n'
                '  int res = R.drawable.kc_lamp_green;\n'
                '  void addWidget() {}\n'
                '  void adjustSetpoint() {}\n'
                '  void playCraftAlarmAlert() {}\n'
                '  void activateScreen() {}\n'
                '  void loadCachedBitmap() {}\n'
                '  void speakVoiceAnnouncement() {}\n'
                '  void evaluateFbdBlock() {}\n'
                '  void evaluateFbdBlockNumeric() {}\n'
                '}\n',
                encoding="utf-8"
            )

            # Positive test: contract declaring 1 referenced and 151 unused passes
            contracts_data = json.loads(self.contracts_path.read_text(encoding="utf-8"))
            contracts_data["image_aliases_status"]["directly_referenced_in_bloodsky_app"] = 1
            contracts_data["image_aliases_status"]["packaged_but_unused_in_bloodsky"] = 151

            mock_contracts_json = tmppath / "contracts.json"
            mock_contracts_json.write_text(json.dumps(contracts_data), encoding="utf-8")

            res = verify_contracts(self.zip_path, mock_contracts_json, mock_decoded, mock_app, self.fixtures_dir)
            self.assertEqual(res["referenced_images"], 1)
            self.assertEqual(res["unused_images"], 151)

            # Negative test: declaring 12 referenced on a tree with 1 referenced file must throw ValueError
            contracts_data["image_aliases_status"]["directly_referenced_in_bloodsky_app"] = 12
            contracts_data["image_aliases_status"]["packaged_but_unused_in_bloodsky"] = 140
            mock_contracts_json.write_text(json.dumps(contracts_data), encoding="utf-8")

            with self.assertRaises(ValueError) as ctx:
                verify_contracts(self.zip_path, mock_contracts_json, mock_decoded, mock_app, self.fixtures_dir)
            self.assertIn("Referenced image count mismatch", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
