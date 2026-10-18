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
