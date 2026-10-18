"""Locate the decompiled Craft voice component in the pinned APK archive."""
from pathlib import Path
import unittest
import zipfile

ARCHIVE = Path(__file__).resolve().parents[1] / "reference/craft-reference.zip"


class CraftVoiceSourceAuditTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(ARCHIVE) as archive:
            cls.paths = archive.namelist()
            cls.name_hints = [
                name for name in cls.paths
                if "a0" in name.lower() or "customcomponents" in name.lower()
            ]
            cls.voice_hits = []
            cls.component_source = []
            for name in cls.paths:
                if not name.endswith((".java", ".smali")):
                    continue
                raw = archive.read(name)
                text = raw.decode("utf-8", errors="replace")
                if name.endswith("/CustomComponents/a0.java"):
                    cls.component_source.append((name, text))
                if any(token in text.lower() for token in ("texttospeech", "speak(", "voice announcement", "голосов")):
                    cls.voice_hits.append((name, text))

    def test_pinned_reference_contains_decompiled_sources(self):
        self.assertTrue(any(name.endswith((".java", ".smali")) for name in self.paths))

    def test_print_candidate_paths_and_voice_source_signatures(self):
        print("Craft a0/CustomComponents path hints:", self.name_hints[:100])
        self.assertTrue(self.component_source, "CustomComponents/a0.java source not found")
        for name, text in self.component_source:
            print("CRAFT ID 27 SOURCE", name)
            lines = [
                line.strip() for line in text.splitlines()
                if any(token in line.lower() for token in (
                    "textvoice", "custom_voice_recognition", "texttospeech", "speechrecognizer",
                    "recognition", "startactivity", "performclick", "onclick", "dispatchtouchevent",
                    "getjsonother", "getjsonconnectioninfo", ".method "
                ))
            ]
            print("\n".join(lines))
            keys = {int(value) for value in __import__("re").findall(r'textVoice(\\d+)', text)}
            print("SOURCE TEXT VOICE INDICES:", sorted(keys))
            self.assertIn(0, keys)
            self.assertIn(15, keys)
        print("Voice/TTS source paths:", [name for name, _ in self.voice_hits[:100]])
        for name, text in self.voice_hits[:20]:
            interesting = [
                line.strip() for line in text.splitlines()
                if any(token in line.lower() for token in (
                    "texttospeech", "speak(", "voice", "голос", ".field ", ".method "
                ))
            ]
            print("SOURCE", name, "\n".join(interesting[:60]))
