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
TCP_CLIENT = ROOT / "app/src/main/java/com/bloodsky/hmi/ModbusTcpClient.java"


class AndroidSourceContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.tcp_client = TCP_CLIENT.read_text(encoding="utf-8")

    def test_project_persistence_serializes_screens_and_widgets(self):
        self.assertIn("private void persistProject()", self.source)
        self.assertIn('project.put("screens", screenArray)', self.source)
        self.assertIn('screenObj.put("widgets", widgetsToJson(screen.widgets))', self.source)
        self.assertIn('project.put("currentScreen", currentScreenIndex)', self.source)

    def test_project_load_supports_multiscreen_and_legacy_array(self):
        self.assertRegex(self.source, r"private (?:void|boolean) loadProject\(\)")
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
        validate_at = self.source.index("validateImportedProject(json);")
        store_at = self.source.index('storeActiveProject(json, true)', validate_at)
        self.assertLess(validate_at, store_at)
        storage = self.source.split('private boolean storeActiveProject(', 1)[1].split('private String readProjectCatalog()', 1)[0]
        self.assertLess(storage.index('ProjectCatalog.normalize(json)'), storage.index('.putString(ACTIVE_PROJECT, normalized)'))
        self.assertLess(storage.index('ProjectCatalog.upsert(oldCatalog, normalized)'), storage.index('edit.commit()'))
        self.assertIn('.putString(PROJECT_CATALOG, catalog)', storage)
        self.assertIn("Trailing content after JSON", self.source)
        self.assertIn("Project file too large", self.source)

    def test_import_limits_screen_widget_and_connection_counts(self):
        self.assertIn("screenArray.length() > 100", self.source)
        self.assertIn("totalWidgets > 2000", self.source)
        self.assertIn("connections.length() > 4000", self.source)

    def test_modbus_supports_only_explicit_read_functions(self):
        # Wire-level checks now live in the Android-independent production transport.
        self.assertIn("functionCode != 1 && functionCode != 2 && functionCode != 3 && functionCode != 4", self.tcp_client)
        self.assertIn("socket.connect(new InetSocketAddress(host, port), timeoutMs)", self.tcp_client)
        self.assertIn("responseTransaction != transaction", self.tcp_client)
        self.assertIn("function == (functionCode | 0x80)", self.tcp_client)
        self.assertIn("new ModbusTcpClient(host, port).read(", self.source)
        self.assertIn("new ModbusTcpClient(host, port).writeSingle(", self.source)
        self.assertIn("new ModbusTcpClient(host, port).writeMultiple(", self.source)
        self.assertIn("validateWriteEcho(pdu, 6, address, value)", self.tcp_client)
        self.assertIn("validateWriteEcho(pdu, 16, address, snapshot.length)", self.tcp_client)
        self.assertIn("deadline - System.nanoTime()", self.tcp_client)

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

    def test_macro_editor_is_the_third_palette_page(self):
        palette = (ROOT / "app/src/main/java/com/bloodsky/hmi/OriginalFbdPalette.java").read_text(encoding="utf-8")
        self.assertIn("this::showCraftMacroTools", self.source)
        self.assertIn('"Редактор макросов",macroEditor', palette)
        self.assertIn('menu.getMenu().add("Другие элементы BLOODSKY")', self.source)
        self.assertIn("android.widget.GridLayout", self.source)
        self.assertIn("R.layout.kc_macro_library_card", self.source)
        self.assertIn("R.drawable.kc_tab_page1", self.source)
        self.assertIn("createCraftMacroFromSelection()", self.source)
        self.assertIn("showFbdPalette();", self.source)

    def test_original_indicator_picker_has_real_preview_and_click_target(self):
        start = self.source.index("private void showAddWidgetMenu()")
        end = self.source.index("private void addWidget(", start)
        picker = self.source[start:end]
        self.assertIn("OriginalIndicator.supports(types[i])", picker)
        self.assertIn("R.drawable.kc_background_button : R.drawable.kc_lamp_off", picker)
        self.assertIn('"Кнопка: оригинальный фон" : "Индикатор: выключен"', picker)
        self.assertIn("previewRow.setOnClickListener(v -> item.performClick())", picker)
        self.assertIn("addWidget(types[index], defaults[index])", picker)

    def test_picker_uses_original_button_artwork(self):
        picker = self.source.split("private void showAddWidgetMenu()", 1)[1].split("private void addWidget(", 1)[0]
        self.assertIn('OriginalIndicator.supports(types[i]) || "Кнопка".equals(types[i])', picker)
        self.assertIn("R.drawable.kc_background_button : R.drawable.kc_lamp_off", picker)
        self.assertIn('buttonPreview = "Кнопка".equals(types[i])', picker)
        self.assertIn("previewRow.setOnClickListener(v -> item.performClick())", picker)

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

    def test_widget_metadata_is_preserved_for_round_trip(self):
        self.assertIn("JSONObject extraFields = new JSONObject()", self.source)
        self.assertIn("projectExtraFields", self.source)

    def test_fbd_connections_reject_cycles_and_duplicates(self):
        self.assertIn("reachesFbd(target, source", self.source)
        self.assertIn("Такая связь уже существует", self.source)
        self.assertIn("chooseFbdPins(source, target)", self.source)
        self.assertIn("screen.connections.add(edge)", self.source)
        self.assertIn("hasCompatibleKnownFbdPins(json, sourceType, targetType)", self.source)
        self.assertIn("compatibleFbdTargetPins(", self.source)
        self.assertRegex(self.source, r"pinnedFbdConnection\(source,\s*(?:sourcePins\[sourceChoice\]|chosenSourcePin)")
        self.assertIn('edge.extraFields.put("sourceSignalType", sourceSignal)', self.source)
        self.assertIn('edge.extraFields.put("targetSignalType", targetSignal)', self.source)

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
        load_start = self.source.index("private boolean loadProject()")
        load_end = self.source.index("public static void validateImportedProjectStatic(", load_start)
        load_method = self.source[load_start:load_end]
        self.assertIn("source < screen.widgets.size()", load_method)
        self.assertIn("target < screen.widgets.size()", load_method)
        self.assertIn("isFbd(screen.widgets.get(source))", load_method)
        self.assertIn("isFbd(screen.widgets.get(target))", load_method)

    def test_legacy_media_migration_is_checked_before_opening_project(self):
        load_start = self.source.index("private boolean loadProject()")
        load_end = self.source.index("public static void validateImportedProjectStatic(", load_start)
        method = self.source[load_start:load_end]
        self.assertIn("LegacyProjectMediaMigrator.needsMigration(json)", method)
        self.assertLess(method.index("migrateLegacyActiveProject(json)"),
                        method.index("widgets.clear()"))
        self.assertIn("storeActiveProject(newJson, true)", self.source)
        self.assertIn("if (!loadProject())", self.source)

    def test_ci_runs_python_tests_android_tests_and_builds_apk(self):
        self.assertIn("python -m unittest discover -s tools -p 'test_*.py' -v", self.workflow)
        self.assertIn("gradle testDebugUnitTest --stacktrace", self.workflow)
        self.assertIn("gradle assembleDebug --stacktrace", self.workflow)
        self.assertIn("BLOODSKY-Android-debug-APK", self.workflow)


if __name__ == "__main__":
    unittest.main()

