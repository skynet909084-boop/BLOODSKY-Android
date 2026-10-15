"""Regression guardrails for the current Android prototype.

These are source-contract tests, not substitutes for Android instrumentation tests.
They catch accidental removal of critical persistence, import validation, and Modbus
safety checks while deeper tests are being added.
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/bloodsky/hmi/MainActivity.java"
CODEC_SOURCE = ROOT / "app/src/main/java/com/bloodsky/hmi/ProjectJsonCodec.java"
FBD_VALIDATOR_SOURCE = ROOT / "app/src/main/java/com/bloodsky/hmi/FbdGraphValidator.java"
WORKFLOW = ROOT / ".github/workflows/android-apk.yml"


class AndroidSourceContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.codec_source = CODEC_SOURCE.read_text(encoding="utf-8")
        cls.validator_source = FBD_VALIDATOR_SOURCE.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_json_metadata_copy_helper_is_used_for_project_screen_and_widget_load(self):
        self.assertIn("JsonObjectCopy.deepCopy(obj)", self.source)
        self.assertIn("JsonObjectCopy.deepCopy(project)", self.source)
        self.assertIn("JsonObjectCopy.deepCopy(screenObj)", self.source)

    def test_project_persistence_serializes_screens_and_widgets(self):
        self.assertIn("private void persistProject()", self.source)
        self.assertIn('project.put("screens", screenArray)', self.source)
        self.assertIn('screenObj.put("widgets", widgetsToJson(screen.widgets))', self.source)
        self.assertIn('project.put("currentScreen", currentScreenIndex)', self.source)
        self.assertIn("ProjectJsonCodec.mergePreservedFields(w.extraFields, obj)", self.source)
        self.assertIn("ProjectJsonCodec.mergePreservedFields(screen.extraFields, screenObj)", self.source)
        self.assertIn("ProjectJsonCodec.mergePreservedFields(projectExtraFields, project)", self.source)
        self.assertIn("static JSONObject mergePreservedFields", self.codec_source)

    def test_project_load_supports_multiscreen_and_legacy_array(self):
        self.assertIn("private void loadProject()", self.source)
        self.assertIn('project.optJSONArray("screens")', self.source)
        self.assertIn("parsed instanceof JSONArray", self.source)
        self.assertIn("new Screen(\"Экран 1\")", self.source)

    def test_import_is_validated_before_persisting(self):
        validate_at = self.source.index("validateImportedProject(json);")
        commit_at = self.source.index('.putString(ACTIVE_PROJECT, json).commit()', validate_at)
        self.assertLess(validate_at, commit_at)
        self.assertIn("ProjectJsonCodec.parse(json)", self.source)
        self.assertIn("Trailing content after project JSON", self.codec_source)
        self.assertIn("Project file too large", self.source)

    def test_import_uses_strict_integer_fields_for_screen_and_geometry(self):
        self.assertIn('ProjectJsonCodec.readIntField(project, "currentScreen", 0)', self.source)
        self.assertIn('ProjectJsonCodec.readIntField(widget, "x", 0)', self.source)
        self.assertIn("static int readIntField", self.codec_source)

    def test_import_limits_screen_widget_and_connection_counts(self):
        self.assertIn("screenArray.length() > 100", self.source)
        self.assertIn("totalWidgets > 2000", self.source)
        self.assertIn("connections.length() > 4000", self.validator_source)

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
        self.assertIn("connection.extraFields = FbdConnectionJsonCodec.preserveLoadedFields(connectionObj)", self.source)
        self.assertIn("FbdConnectionJsonCodec.toJson(", self.source)
        self.assertIn("JSONObject extraFields = new JSONObject()", self.source)
        self.assertIn("screen.connections.add(connection)", self.source)

    def test_deleting_widget_removes_and_reindexes_fbd_edges(self):
        delete_start = self.source.index("private void deleteSelectedWidget()")
        delete_end = self.source.index("private JSONObject widgetToJson", delete_start)
        delete_method = self.source[delete_start:delete_end]
        self.assertIn("connection.source == removed || connection.target == removed", delete_method)
        self.assertIn("if (connection.source > removed) connection.source--;", delete_method)
        self.assertIn("if (connection.target > removed) connection.target--;", delete_method)
        self.assertIn("screen.connections.addAll(kept)", delete_method)

    def test_import_rejects_invalid_and_duplicate_fbd_edges_and_cycles(self):
        self.assertIn("FbdGraphValidator.validateScreenConnections(screen, screenWidgets)", self.source)
        self.assertIn("validate(widgets, connections)", self.validator_source)
        self.assertIn('throw new IllegalArgumentException("Connections must be an array")', self.validator_source)
        self.assertIn('throw new IllegalArgumentException("Connection index out of range")', self.validator_source)
        self.assertIn('throw new IllegalArgumentException("Connections must link FBD blocks")', self.validator_source)
        self.assertIn('throw new IllegalArgumentException("Duplicate FBD connection")', self.validator_source)
        self.assertIn("Kahn's algorithm rejects imported graphs with cycles", self.validator_source)
        self.assertIn("if (visitedCount != nodeCount)", self.validator_source)

    def test_loading_only_restores_valid_fbd_connections(self):
        load_start = self.source.index("private void loadProject()")
        load_end = self.source.index("private boolean isFbd(", load_start)
        load_method = self.source[load_start:load_end]
        self.assertIn("source < screen.widgets.size()", load_method)
        self.assertIn("target < screen.widgets.size()", load_method)
        self.assertIn("isFbd(screen.widgets.get(source))", load_method)
        self.assertIn("isFbd(screen.widgets.get(target))", load_method)

    def test_ci_runs_python_tests_android_tests_and_builds_apk(self):
        self.assertIn("python -m unittest discover -s tools -p 'test_*.py' -v", self.workflow)
        self.assertIn("gradle testDebugUnitTest --stacktrace", self.workflow)
        self.assertIn("gradle assembleDebug --stacktrace", self.workflow)
        self.assertIn("BLOODSKY-Android-debug-APK", self.workflow)


if __name__ == "__main__":
    unittest.main()
