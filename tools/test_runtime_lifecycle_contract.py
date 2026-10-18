"""Production wiring guards complement executable PollSessionGuard tests."""
from pathlib import Path
import unittest
SOURCE = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/bloodsky/hmi/MainActivity.java'

class RuntimeLifecycleContractTests(unittest.TestCase):
    def test_pause_stops_polling_and_audio_and_resume_restarts_editor(self):
        source = SOURCE.read_text()
        pause = source.split('protected void onPause()', 1)[1].split('protected void onDestroy()', 1)[0]
        self.assertIn('activityResumed = false', pause)
        self.assertIn('stopModbusPolling()', pause)
        self.assertIn('stopFbdSensors()', pause)
        self.assertIn('mediaPlayer.release()', pause)
        self.assertIn('textToSpeech.stop()', pause)
        resume = source.split('protected void onResume()', 1)[1].split('protected void onPause()', 1)[0]
        self.assertIn('if (editor != null) { startModbusPolling(); startFbdSensors(); startCraftMqtt(); }', resume)
        self.assertIn('stopCraftMqtt()', pause)

    def test_worker_and_ui_both_reject_obsolete_sessions(self):
        source = SOURCE.read_text()
        polling = source.split('private void pollBoundWidgets()', 1)[1].split('private static class BindingRequest', 1)[0]
        self.assertIn('if (!pollSession.accepts(sessionToken)) return;', polling)
        ui = polling.split('runOnUiThread(() -> {', 1)[1]
        self.assertLess(ui.index('!pollSession.accepts(sessionToken)'), ui.index('liveValues.put('))
        self.assertIn('editor != sessionEditor', ui)
        self.assertIn('isFinishing() || isDestroyed()', ui)
        self.assertIn('snapshotBindings.get(index)', ui)

if __name__ == '__main__':
    unittest.main()
