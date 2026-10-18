"""Source contract for the original Craft ID 27 voice-recognition widget."""
from pathlib import Path
import unittest
import zipfile

ARCHIVE = Path(__file__).resolve().parents[1] / "reference/craft-reference.zip"
SOURCE_SUFFIX = "jadx/sources/ru/himik/nightscream/kascada/CustomComponents/a0.java"


class CraftVoiceSourceAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(ARCHIVE) as archive:
            matches = [name for name in archive.namelist() if name.endswith(SOURCE_SUFFIX)]
            cls.source = archive.read(matches[0]).decode("utf-8", errors="replace") if matches else ""

    def test_original_class_identity_is_voice_recognition(self):
        self.assertIn("this.f8705d = 27;", self.source)
        self.assertIn("R.string.custom_voice_recognition", self.source)
        self.assertNotIn("TextToSpeech", self.source)

    def test_original_component_serializes_sixteen_phrase_fields(self):
        for index in range(16):
            self.assertIn(f'"textVoice{index}"', self.source, f"Missing Craft phrase field {index}")

    def test_print_voice_recognition_handler_for_manual_review(self):
        lines = self.source.splitlines()
        method_lines = [
            f"{index + 1}: {line.strip()}" for index, line in enumerate(lines)
            if line.strip().startswith(("public ", "private ", "protected "))
        ]
        print("ID 27 METHODS\\n" + "\\n".join(method_lines))
        relevant = []
        for index, line in enumerate(lines):
            low = line.lower()
            if any(token in low for token in ("textvoice", "recogn", "speech", " on", "click", "contains(\\"#\\"")):
                start, end = max(0, index - 2), min(len(lines), index + 3)
                relevant.extend(f"{n + 1}: {lines[n].strip()}" for n in range(start, end))
        print("ID 27 HANDLER EVIDENCE\\n" + "\\n".join(dict.fromkeys(relevant)))
