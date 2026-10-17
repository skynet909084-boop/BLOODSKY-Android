"""Static source evidence extractor tests. Never execute original Java."""
import hashlib
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import zipfile

from original_fbd_runtime_evidence import generate_report, inspect_class, TARGETS, SOURCE_PREFIX


class RuntimeEvidenceTest(unittest.TestCase):
    def test_extract_class_and_comment_mask(self):
        raw = (b"// class CTU extends Fake\n"
               b"public class FB_CTU extends RealCounter {\n"
               b'  String str = "if (getValue())";\n'
               b"  /* if (getValue()) */\n"
               b"  public void evaluate() { if (input.getValue()) counter++; }\n"
               b"}\n")
        doc = inspect_class(SOURCE_PREFIX + "FB_CTU.java", raw)
        self.assertEqual("RealCounter", doc["extends"])
        self.assertEqual(6, doc["line_count"])
        self.assertTrue(any("counter++" in row["code"] for row in doc["evidence"]))
        self.assertFalse(any("Fake" in row["code"] for row in doc["evidence"]))
        self.assertEqual(hashlib.sha256(raw).hexdigest(), doc["sha256"])

    def test_parent_resolution_and_bounded_excerpt(self):
        with TemporaryDirectory() as tmp:
            archive = Path(tmp) / "original.zip"
            with zipfile.ZipFile(archive, "w") as z:
                for filename in TARGETS:
                    parent = "FB_Base" if filename == "FB_CTU.java" else "Object"
                    z.writestr(SOURCE_PREFIX + filename,
                               ("public class " + filename[:-5] + " extends " + parent +
                                " {\n public boolean exec(){ return input.getValue(); }\n}\n").encode())
                z.writestr(SOURCE_PREFIX + "FB_Base.java",
                           b"public class FB_Base { public void reset() {} }")
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            output = generate_report(archive, expected_sha256=digest)
            names = {item["path"].split("/")[-1] for item in output["classes"]}
            self.assertTrue(set(TARGETS).issubset(names))
            self.assertIn("FB_Base.java", names)
            self.assertTrue(all(len(row["evidence"]) <= 96 for row in output["classes"]))
            self.assertRaises(ValueError, generate_report, archive, "0" * 64)

    def test_bounded_execution_body_and_qualified_parent(self):
        raw = (b"import ru.himik.nightscream.kascada.Logic.c.d;\n"
               b"public class FB_CTU extends ru.himik.nightscream.kascada.Logic.c.d {\n"
               b" public void f() { if (reset) { value = 0; } else if (inc) { value++; } }\n"
               b" public Object getOut() { return Integer.valueOf(value); }\n"
               b"}\n")
        doc = inspect_class(SOURCE_PREFIX + "FB_CTU.java", raw)
        self.assertEqual("ru.himik.nightscream.kascada.Logic.c.d", doc["extends"])
        self.assertIn("ru.himik.nightscream.kascada.Logic.c.d", doc["imports"])
        self.assertEqual(1, len(doc["execution"]))
        self.assertIn("else if (inc)", doc["execution"][0]["code"])
        self.assertEqual(3, doc["execution"][0]["line"])

    def test_bad_zip_path_rejected(self):
        with TemporaryDirectory() as tmp:
            path = Path(tmp) / "malicious.zip"
            with zipfile.ZipFile(path, "w") as z:
                z.writestr("../escape.java", "class Bad {}")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, "Unsafe"):
                generate_report(path, digest)


if __name__ == "__main__":
    unittest.main()
