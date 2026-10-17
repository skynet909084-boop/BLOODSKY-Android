"""Regression tests for source-backed image pixels in Android smoke artifacts."""
import unittest

from png_pixel_probe import solid_rgba_png, count_matching_png_pixels


class PngPixelProbeTests(unittest.TestCase):
    def test_valid_fixture_has_exact_magenta_pixels(self):
        data = solid_rgba_png(8, 8, (235, 21, 207, 255))
        self.assertEqual(64, count_matching_png_pixels(data, (235, 21, 207)))
        self.assertEqual(0, count_matching_png_pixels(data, (0, 255, 0)))
        self.assertEqual(64, count_matching_png_pixels(data, (236, 19, 210), tolerance=4))
        self.assertEqual(0, count_matching_png_pixels(data, (236, 19, 210), tolerance=1))

    def test_truncated_or_crc_corrupted_png_never_counts_as_rendered(self):
        data = solid_rgba_png(16, 16, (235, 21, 207, 255))
        corrupted = bytearray(data)
        corrupted[40] ^= 1
        with self.assertRaises(ValueError):
            count_matching_png_pixels(bytes(corrupted), (235, 21, 207))
        with self.assertRaises(ValueError):
            count_matching_png_pixels(data[:-9], (235, 21, 207))
        with self.assertRaises(ValueError):
            count_matching_png_pixels(b'not PNG', (235, 21, 207))

    def test_rejects_impossible_png_dimensions_and_probe_inputs(self):
        with self.assertRaises(ValueError):
            solid_rgba_png(0, 16, (255, 0, 0, 255))
        with self.assertRaises(ValueError):
            solid_rgba_png(8, 8, (-1, 0, 0, 255))
        with self.assertRaises(ValueError):
            count_matching_png_pixels(solid_rgba_png(8, 8, (0, 0, 0, 255)),
                                      (0, 0, 0), tolerance=-1)


if __name__ == "__main__":
    unittest.main()
