"""Fast source-contract checks for safe Craft operator write lifecycles.

These complement Android unit/device tests; they are not real Modbus traffic tests.
"""
from pathlib import Path
import unittest

SOURCE = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/com/bloodsky/hmi/MainActivity.java"
)


class CraftRuntimeWriteContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")
        cls.canvas = cls.source.split("private class EditorCanvas extends View", 1)[1]

    @staticmethod
    def method(source, start, stop):
        return source.split(start, 1)[1].split(stop, 1)[0]

    def test_runtime_context_requires_live_screen_canvas_and_exact_widget(self):
        body = self.method(self.canvas, "private boolean isRuntimeWidgetCurrent(",
                           "private boolean saveRuntimeLinearSetpoint(")
        for required in (
            "activityResumed && operatorMode",
            "editor == this",
            "originalScreenIndex >= 0",
            "currentScreenIndex == originalScreenIndex",
            "screens.get(currentScreenIndex) == originalScreen",
            "widgets.get(index) == widget",
        ):
            with self.subTest(check=required):
                self.assertIn(required, body)

    def test_linear_setter_is_saved_atomically_and_rejects_delayed_callbacks(self):
        writer = self.method(self.canvas, "private boolean saveRuntimeLinearSetpoint(",
                             "private void openCraftLinearSetter(")
        for required in (
            "isRuntimeWidgetCurrent(widget, index, originalScreen, originalScreenIndex)",
            "new JSONObject(oldFields.toString())",
            'widget.extraFields.put("craftLinearValue", next)',
            "if (!persistProjectNow(true))",
            "widget.extraFields = oldFields",
            "projectExtraFields = oldProject",
            "saveActiveScreen()",
        ):
            self.assertIn(required, writer)
        self.assertNotIn("persistProject();", writer)
        setter = self.method(self.canvas, "private void openCraftLinearSetter(",
                             "private void openCraftNumericInput(")
        self.assertIn("openingScreen = ", setter)
        self.assertIn("if (!isRuntimeWidgetCurrent(widget, widgetIndex,", setter)
        self.assertGreaterEqual(setter.count("isRuntimeWidgetCurrent(widget, widgetIndex,"), 4)
        self.assertIn("saveRuntimeLinearSetpoint(widget, widgetIndex, next,", setter)
        self.assertIn("writeSingle(binding.unit, binding.address, next)", setter)
        self.assertIn("Регистр записан, но проект не сохранён", setter)

    def test_numeric_input_guards_dialog_and_rolls_back_system_bank(self):
        numeric = self.method(self.canvas, "private void openCraftNumericInput(",
                              "private void openCraftRtsp(")
        self.assertGreaterEqual(numeric.count("isRuntimeWidgetCurrent(widget, widgetIndex,"), 4)
        for required in (
            "final Screen openingScreen",
            "dialog.dismiss();",
            "systemRegisterBank.toProjectJson()",
            "CraftSystemNumericWidgetBinding.writeValue(",
            "if (!persistProjectNow(true))",
            "systemRegisterBank.restoreProjectJson(oldSystemRegisters)",
            "widget.text = oldText",
            "projectExtraFields = oldProject",
            "saveActiveScreen()",
            "final int registerCount = CraftNumericInput.registerCount",
            "writeMultiple(binding.unit, binding.address, encoded)",
            "writeSingle(binding.unit, binding.address, encoded[0])",
            "Регистр записан, но проект не сохранён",
        ):
            with self.subTest(check=required):
                self.assertIn(required, numeric)
        self.assertNotIn("persistProject();", numeric)

    def test_craft_switch_prevents_double_write_and_waits_for_fc22_success(self):
        self.assertIn("transient boolean craftSwitchWritePending;", self.source)
        switch = self.method(self.canvas, "private void activateCraftSwitch(",
                             "private void queueCraftBitButtonWrite(")
        for required in (
            "isRuntimeWidgetCurrent(widget, index, originalScreen, originalScreenIndex)",
            "if (widget.craftSwitchWritePending)",
            "widget.craftSwitchWritePending = true",
            "widget.craftSwitchWritePending = false",
            "OriginalCraftSwitch.writeMasks(bit, next)",
            "writeMask(",
            "craftSwitchWriteSequence",
            "widget.buttonState = next",
            "if (!persistProjectNow(true))",
            "widget.buttonState = previous",
            "widget.extraFields = oldFields",
            "projectExtraFields = oldProject",
            "saveActiveScreen()",
            "Бит записан, но проект не сохранён",
        ):
            with self.subTest(check=required):
                self.assertIn(required, switch)
        self.assertNotIn("persistProject();", switch)
        self.assertLess(switch.index("writeMask("), switch.index("widget.buttonState = next", switch.index("runOnUiThread(")))


if __name__ == "__main__":
    unittest.main()
