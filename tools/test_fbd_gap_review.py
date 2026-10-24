"""Keep the source-backed Craft FBD status aligned with current coverage."""
import unittest
from pathlib import Path


REVIEW = (Path(__file__).resolve().parents[1] / "docs/FBD_CRAFT_GAP_REVIEW_20261018.md")


class CraftFbdGapReviewTests(unittest.TestCase):
    def test_review_marks_implemented_batches_and_real_open_gaps(self):
        text = REVIEW.read_text(encoding="utf-8")
        for item in (
                "Typed arithmetic", "Native transitions", "Pull-evaluated blocks and registers",
                "Macro blocks", "Visual parity", "Original macro evidence",
                "Unknown factory ID 3"):
            self.assertIn(item, text)
        for stale in (
                "Next arithmetic batch", "PR #136 remains open",
                "stable `main` remains unchanged", "T is implemented; confirm F/R"):
            self.assertNotIn(stale, text)


if __name__ == "__main__":
    unittest.main()
