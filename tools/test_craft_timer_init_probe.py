import hashlib
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import zipfile

from craft_timer_init_probe import report, excerpt_method
from original_fbd_source_probe import SOURCE_PREFIX


class CraftTimerInitProbeTests(unittest.TestCase):
    def test_method_braces_multiline_and_singleline(self):
        code = [
            'public class FB_TIMER {',
            ' public void setActionExecute(int mode)',
            ' {',
            '   if (mode == 3) { output = true; }',
            ' }',
            ' public void setTypeTime(int unit) { f0 = unit; }',
            '}',
        ]
        a = excerpt_method(code, "setActionExecute")
        b = excerpt_method(code, "setTypeTime")
        self.assertEqual(1, len(a))
        self.assertEqual([2,3,4,5], [r["line"] for r in a[0]["source_lines"]])
        self.assertEqual(1, len(b))
        self.assertEqual([6], [r["line"] for r in b[0]["source_lines"]])

    def test_pin_source_constant_and_comment_rejection(self):
        with TemporaryDirectory() as td:
            p = Path(td) / "source.zip"
            with zipfile.ZipFile(p, "w") as z:
                z.writestr(SOURCE_PREFIX + "FB_TIMER.java",
                           'public class FB_TIMER { public void setActionExecute(int x) { output = x==3; } }')
                z.writestr(SOURCE_PREFIX + "FB_TRIGGER.java",
                           'public class FB_TRIGGER { public void setActionExecute(int x) { type=x; } }')
                z.writestr("jadx/sources/com/hoho/android/usbserial/driver/UsbId.java",
                           'public class UsbId {\n'
                           '  // SILABS_CP2102 = 42;\n'
                           '  public static final int SILABS_CP2102 = 60000;\n}'
                )
            sha = hashlib.sha256(p.read_bytes()).hexdigest()
            result = report(p, sha)
            self.assertEqual(60000, result["unit3_value"])
            self.assertEqual(1, len(result["unit3_declarations"]))
            self.assertEqual(2, len(result["methods"]))
            with self.assertRaises(ValueError):
                report(p, "0"*64)

    def test_unknown_constant_fails_closed(self):
        with TemporaryDirectory() as td:
            p = Path(td) / "original.zip"
            with zipfile.ZipFile(p, "w") as z:
                z.writestr(SOURCE_PREFIX + "FB_TIMER.java",
                           "public class FB_TIMER { public void setTypeTime(int n) { code=n; } }")
            sha = hashlib.sha256(p.read_bytes()).hexdigest()
            result = report(p, sha)
            self.assertIsNone(result["unit3_value"])
            self.assertIn("unverified", result["unit3_claim"])

    def test_unsafe_archive_entry_is_rejected(self):
        with TemporaryDirectory() as td:
            p = Path(td) / "bad.zip"
            with zipfile.ZipFile(p, "w") as z:
                z.writestr("../bad.java", "class X {}")
            sha = hashlib.sha256(p.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, "Unsafe"):
                report(p, sha)


if __name__ == "__main__":
    unittest.main()
