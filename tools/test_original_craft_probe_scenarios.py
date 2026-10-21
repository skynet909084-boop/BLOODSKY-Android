import unittest

from original_craft_probe_scenarios import QUICK_SCENARIOS, parse_probe_options


class OriginalCraftProbeScenarioTests(unittest.TestCase):
    def test_quick_scenarios_are_independent_and_link_to_k107(self):
        self.assertEqual(
            {"project-create", "hmi-palette", "hmi-id17-properties"},
            set(QUICK_SCENARIOS),
        )
        self.assertEqual(["K107-0001"], QUICK_SCENARIOS["project-create"]["case_ids"])
        self.assertEqual(["K107-0023"], QUICK_SCENARIOS["hmi-palette"]["case_ids"])
        self.assertEqual(["K107-0023"], QUICK_SCENARIOS["hmi-id17-properties"]["case_ids"])

    def test_default_is_bounded_hmi_properties_scenario(self):
        self.assertEqual(("quick", "hmi-id17-properties"), parse_probe_options([]))

    def test_selects_a_different_short_scenario_without_full_profile(self):
        self.assertEqual(
            ("quick", "project-create"),
            parse_probe_options(["--scenario", "project-create"]),
        )
        self.assertEqual(
            ("quick", "hmi-palette"),
            parse_probe_options(["--scenario", "hmi-palette"]),
        )

    def test_full_is_explicit_and_unknown_scenario_fails_closed(self):
        self.assertEqual(
            ("full", "project-create"),
            parse_probe_options(["--full", "--scenario", "project-create"]),
        )
        with self.assertRaisesRegex(ValueError, "unknown quick scenario"):
            parse_probe_options(["--scenario", "everything"])

    def test_malformed_cli_arguments_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "requires a scenario"):
            parse_probe_options(["--scenario"])
        with self.assertRaisesRegex(ValueError, "unknown probe arguments"):
            parse_probe_options(["--other"])


if __name__ == "__main__":
    unittest.main()
