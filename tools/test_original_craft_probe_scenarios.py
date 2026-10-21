"""Unit checks for independent, bounded original Craft scenarios."""
import unittest

from original_craft_probe_scenarios import QUICK_SCENARIOS, parse_probe_options


class ProbeScenarioTests(unittest.TestCase):
    def test_each_scenario_is_independently_named_and_mapped(self):
        self.assertEqual({"project-create", "hmi-palette", "hmi-id17-properties"}, set(QUICK_SCENARIOS))
        for name, case in QUICK_SCENARIOS.items():
            with self.subTest(name=name):
                self.assertTrue(case["case_ids"])
                self.assertTrue(case["expected"])
                self.assertTrue(case["steps"])
                self.assertEqual(("quick", name, False), parse_probe_options(["--scenario", name]))

    def test_default_preserves_previous_short_route(self):
        self.assertEqual(("quick", "hmi-id17-properties", False), parse_probe_options([]))

    def test_manual_full_can_select_scenario_but_profile_is_full(self):
        self.assertEqual(("full", "hmi-palette", False), parse_probe_options(["--full", "--scenario", "hmi-palette"]))

    def test_resume_requires_manual_full_profile(self):
        self.assertEqual(("full", "hmi-id17-properties", True), parse_probe_options(["--full", "--resume"]))

    def test_invalid_and_missing_scenario_are_rejected(self):
        for args in (["--scenario"], ["--scenario", "unknown"], ["--unexpected"], ["--resume"]):
            with self.subTest(args=args), self.assertRaises(ValueError):
                parse_probe_options(args)


if __name__ == "__main__":
    unittest.main()
