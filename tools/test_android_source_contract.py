"""Regression guardrails for the current Android prototype.

These are source-contract tests, not substitutes for Android instrumentation tests.
They catch accidental removal of critical persistence, import validation, and Modbus
safety checks while deeper tests are being added.
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java"
WORKFLOW = ROOT / ".github/workflows/android-apk.yml"


class AndroidSourceContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_project_persistence_serializes_screens_and_widgets(self):
        self.assertIn("private void persistProject()", self.source)
        self.assertIn('project.put("screens", screenArray)', self.source)
        self.assertIn('screenObj.put("widgets", widgetsToJson(screen.widgets))', self.source)
        self.assertIn('project.put("currentScreen", currentScreenIndex)', self.source)

    def test_project_load_supports_multiscreen_and_legacy_array(self):
        self.assertIn("private void loadProject()", self.source)
        self.assertIn('project.optJSONArray("screens")', self.source)
        self.assertIn("parsed instanceof JSONArray", self.source)
        self.assertIn("new Screen(\"Экран 1\")", self.source)

    def test_import_is_validated_before_persisting(self):
        validate_at = self.source.index("validateImportedProject(json);")
        commit_at = self.source.index('.putString(ACTIVE_PROJECT, json).commit()', validate_at)
        self.assertLess(validate_at, commit_at)
        self.assertIn("Trailing content after JSON", self.source)
        self.assertIn("Project file too large", self.source)

    def test_import_limits_screen_widget_and_connection_counts(self):
        self.assertIn("screenArray.length() > 100", self.source)
        self.assertIn("totalWidgets > 2000", self.source)
        self.assertIn("connections.length() > 4000", self.source)

    def test_modbus_supports_only_explicit_read_functions(self):
        self.assertIn("functionCode != 3 && functionCode != 4", self.source)
        self.assertIn("socket.connect(new InetSocketAddress(host, port), 3000)", self.source)
        self.assertIn("socket.setSoTimeout(3000)", self.source)
        self.assertIn("responseTransaction != transaction", self.source)
        self.assertIn("function == (functionCode | 0x80)", self.source)

    def test_widget_metadata_is_preserved_for_round_trip(self):
        self.assertIn("JSONObject extraFields = new JSONObject()", self.source)
        self.assertIn("unknown imported widget metadata for round-trip", self.source)
        self.assertIn("projectExtraFields", self.source)

    def test_fbd_connections_reject_cycles_and_duplicates(self):
        self.assertIn("reachesFbd(target, source", self.source)
        self.assertIn("Такая связь уже существует", self.source)
        self.assertIn("screen.connections.add(new FbdConnection(source, target))", self.source)

    def test_ci_runs_python_tests_android_tests_and_builds_apk(self):
        self.assertIn("python -m unittest discover -s tools -p 'test_*.py' -v", self.workflow)
        self.assertIn("gradle testDebugUnitTest --stacktrace", self.workflow)
        self.assertIn("gradle assembleDebug --stacktrace", self.workflow)
        self.assertIn("BLOODSKY-Android-debug-APK", self.workflow)


if __name__ == "__main__":
    unittest.main()
