import unittest

from original_craft_probe_state import remember_state_signature


class OriginalCraftProbeStateTests(unittest.TestCase):
    def test_signature_is_registered_once_and_duplicate_is_rejected(self):
        seen = set()
        self.assertTrue(remember_state_signature(seen, "screen-a"))
        self.assertFalse(remember_state_signature(seen, "screen-a"))
        self.assertTrue(remember_state_signature(seen, "screen-b"))
        self.assertEqual({"screen-a", "screen-b"}, seen)


if __name__ == "__main__":
    unittest.main()
