"""Inspect the pinned APK class behind Craft voice component ID 27.

The source class is nested/obfuscated, so this first audit locates it by Smali
class descriptor rather than relying on a JADX filename.
"""
from pathlib import Path
import re
import unittest
import zipfile

ARCHIVE = Path(__file__).resolve().parents[1] / "reference/craft-reference.zip"


class CraftVoiceSourceAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.matches = []
        with zipfile.ZipFile(ARCHIVE) as archive:
            for name in archive.namelist():
                if not name.endswith(".smali"):
                    continue
                text = archive.read(name).decode("utf-8", errors="replace")
                if re.search(r"^\\.class .*?(?:\\$a0|/a0);", text, re.MULTILINE):
                    cls.matches.append((name, text))
        if cls.matches:
            cls.path, cls.source = cls.matches[0]

    def test_source_class_for_craft_id_27_is_present(self):
        self.assertTrue(self.matches, "Could not locate CustomComponents.a0 Smali class")

    def test_print_fields_methods_and_string_constants_for_source_review(self):
        self.assertTrue(self.matches, "Source class must be located before review")
        lines = [
            line.strip()
            for line in self.source.splitlines()
            if re.search(r"^\\.field |^\\.method |const-string|setText|speak|TextToSpeech|performClick|onClick", line)
        ]
        print("Craft ID 27 source:", self.path)
        print("\\n".join(lines[:220]))
        self.assertTrue(lines, "Located Craft ID 27 class had no inspectable members")
