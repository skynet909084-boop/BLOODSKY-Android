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

    def test_atomic_save_and_load(self):
        self.assertIn("saveProjectAtomic(", self.source)
        self.assertIn("loadProjectAtomic(", self.source)

    def test_zip_export_and_import(self):
        self.assertIn("exportProjectToZip(", self.source)
        self.assertIn("importProjectFromZip(", self.source)

    def test_modbus_tcp_write_support(self):
        self.assertIn("writeModbusRegisterStatic(", self.source)
        self.assertIn("writeModbusMultipleRegistersStatic(", self.source)

    def test_modbus_rtu_transport(self):
        self.assertIn("ModbusRtuTransport", self.source)

    def test_import_is_validated_before_persisting(self):
        self.assertIn("validateImportedProject(", self.source)
        self.assertIn(".putString(ACTIVE_PROJECT, importedJson).commit()", self.source)
        self.assertIn("Trailing content after JSON", self.source)
        self.assertIn("Project file too large", self.source)

    def test_import_limits_screen_widget_and_connection_counts(self):
        self.assertIn("screenArray.length() > 100", self.source)
        self.assertIn("totalWidgets > 2000", self.source)
        self.assertIn("connections.length() > 4000", self.source)

    def test_modbus_supports_only_explicit_read_functions(self):
        self.assertIn("functionCode != 1 && functionCode != 2 && functionCode != 3 && functionCode != 4", self.source)
        self.assertIn("socket.connect(new InetSocketAddress(host, port), 3000)", self.source)
        self.assertIn("socket.setSoTimeout(3000)", self.source)
        self.assertIn("responseTransaction != transaction", self.source)
        self.assertIn("function == (functionCode | 0x80)", self.source)

    def test_modbus_rtu_crc_helper(self):
        self.assertIn("calculateModbusRtuCrc(", self.source)
        self.assertIn("buildModbusRtuFrame(", self.source)

    def test_editor_alignment_features(self):
        self.assertIn("alignSelectedWidget(", self.source)
        self.assertIn("showAlignDialog()", self.source)

    def test_numeric_and_extended_fbd_blocks(self):
        self.assertIn("FBD Сложение", self.source)
        self.assertIn("FBD Вычитание", self.source)
        self.assertIn("FBD Умножение", self.source)
        self.assertIn("FBD Деление", self.source)
        self.assertIn("FBD ABS", self.source)
        self.assertIn("FBD MIN_MAX", self.source)
        self.assertIn("FBD POW", self.source)
        self.assertIn("FBD SQRT", self.source)
        self.assertIn("FBD SIN", self.source)
        self.assertIn("FBD COS", self.source)
        self.assertIn("FBD TAN", self.source)
        self.assertIn("FBD FloatToInt", self.source)
        self.assertIn("FBD IntToFloat", self.source)

    def test_kascada_craft_widgets(self):
        self.assertIn("Шкала / Стрелка", self.source)
        self.assertIn("Прогресс-бар", self.source)
        self.assertIn("График / Тренд", self.source)
        self.assertIn("Двухпозиционный индикатор", self.source)
        self.assertIn("Битовый список", self.source)
        self.assertIn("Звуковой сигнал", self.source)
        self.assertIn("Переход на экран", self.source)
        self.assertIn("Многопозиционное фото", self.source)
        self.assertIn("Голосовое сообщение", self.source)

    def test_batch3_project_settings_persistence(self):
        self.assertIn("showProjectSettingsDialog()", self.source)
        self.assertIn("stationMode", self.source)
        self.assertIn("modbusSlave", self.source)
        self.assertIn("systemRegisters", self.source)

    def test_batch4_operator_mode_and_thumbnails(self):
        self.assertIn("isOperatorMode", self.source)
        self.assertIn("toggleOperatorMode()", self.source)
        self.assertIn("createWidgetThumbnail(", self.source)

    def test_batch4_usb_hardware_status_and_receiver(self):
        self.assertIn("checkUsbHardwareStatusStatic(", self.source)
        self.assertIn("UsbHardwareStatus", self.source)
        self.assertIn("registerUsbReceiver()", self.source)

    def test_batch4_lifecycle_hooks(self):
        self.assertIn("onPause()", self.source)
        self.assertIn("onResume()", self.source)
        self.assertIn("onSaveInstanceState(", self.source)
        self.assertIn("isFinishing() || isDestroyed()", self.source)

    def test_widget_metadata_is_preserved_for_round_trip(self):
        self.assertIn("JSONObject extraFields = new JSONObject()", self.source)
        self.assertIn("projectExtraFields", self.source)

    def test_fbd_connections_reject_cycles_and_duplicates(self):
        self.assertIn("reachesFbd(target, source", self.source)
        self.assertIn("Такая связь уже существует", self.source)
        self.assertIn("screen.connections.add(new FbdConnection(source, target))", self.source)

    def test_deleting_widget_removes_and_reindexes_fbd_edges(self):
        delete_start = self.source.index("private void deleteSelectedWidget()")
        delete_end = self.source.index("public static JSONObject widgetToJson", delete_start)
        delete_method = self.source[delete_start:delete_end]
        self.assertIn("connection.source == removed || connection.target == removed", delete_method)
        self.assertIn("if (connection.source > removed) connection.source--;", delete_method)
        self.assertIn("if (connection.target > removed) connection.target--;", delete_method)
        self.assertIn("screen.connections.addAll(kept)", delete_method)

    def test_import_rejects_invalid_and_duplicate_fbd_edges_and_cycles(self):
        self.assertIn('throw new IllegalArgumentException("Connection index out of range")', self.source)
        self.assertIn('throw new IllegalArgumentException("Connections must link FBD blocks")', self.source)
        self.assertIn('throw new IllegalArgumentException("Duplicate FBD connection")', self.source)
        self.assertIn("if (visitedCount != nodeCount)", self.source)

    def test_loading_only_restores_valid_fbd_connections(self):
        load_start = self.source.index("private void loadProject()")
        load_end = self.source.index("public static void validateImportedProjectStatic(", load_start)
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
