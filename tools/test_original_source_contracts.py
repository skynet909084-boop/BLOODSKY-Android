#!/usr/bin/env python3
"""Tests for original APK source contracts and verification ledger."""
import hashlib
import json
from pathlib import Path
import unittest

from prepare_craft_resources import EXPECTED_SHA256
from verify_original_source_contracts import verify_contracts


class OriginalSourceContractsTests(unittest.TestCase):

    def setUp(self):
        self.repo_root = Path(__file__).resolve().parent.parent
        self.zip_path = self.repo_root / "reference/craft-reference.zip"
        self.contracts_path = self.repo_root / "docs/ORIGINAL_APK_SOURCE_CONTRACTS.json"
        self.decoded_dir = self.repo_root / "build/craft-reference"
        self.app_dir = self.repo_root / "app/src/main/java"
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
        """Verification tool must pass on repo state and report OK."""
        res = verify_contracts(
            self.zip_path,
            self.contracts_path,
            self.decoded_dir,
            self.app_dir,
            self.fixtures_dir
        )
        self.assertEqual(res["status"], "OK")
        self.assertEqual(res["total_images"], 152)
        self.assertEqual(res["referenced_images"], 0)
        self.assertEqual(res["unused_images"], 152)

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


if __name__ == "__main__":
    unittest.main()
