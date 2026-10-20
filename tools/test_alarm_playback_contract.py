"""Source-level regression guards for per-widget Craft alarm playback."""
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/bloodsky/hmi/MainActivity.java"


class CraftAlarmPlaybackContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = SOURCE.read_text(encoding="utf-8")

    def test_each_widget_owns_its_player_and_completion_cleanup(self):
        source = self.source
        self.assertIn("Map<String, MediaPlayer> craftAlarmPlayers", source)
        self.assertIn("Map<String, ToneGenerator> craftAlarmTones", source)
        play = source.split("private void playCraftAlarmAlert(", 1)[1].split(
            "private void stopCraftAlarmAlert(", 1
        )[0]
        self.assertIn("craftAlarmPlayers.get(alarmKey) == completed", play)
        self.assertIn("craftAlarmPlayers.put(alarmKey, activePlayer)", play)
        self.assertIn("craftAlarmTones.put(alarmKey, tone)", play)

    def test_media_players_prepare_asynchronously_and_ignore_stale_callbacks(self):
        play = self.source.split("private void playCraftAlarmAlert(", 1)[1].split(
            "private void playCraftAlarmFallbackTone(", 1
        )[0]
        self.assertIn("player.prepareAsync()", play)
        self.assertIn("setOnPreparedListener", play)
        self.assertIn("setOnErrorListener", play)
        self.assertIn("craftAlarmPlayers.get(alarmKey) != prepared", play)
        self.assertIn("craftAlarmPlayers.get(alarmKey) == failed", play)
        self.assertIn("MediaPlayer player = null;\n        try {\n            player = new MediaPlayer();", play)
        self.assertNotIn("player.prepare()", play)
        self.assertNotIn("MediaPlayer.create(", play)

    def test_stopping_one_widget_does_not_stop_other_alarm_players(self):
        stop_one = self.source.split("private void stopCraftAlarmAlert(", 1)[1].split(
            "private void stopAllCraftAlarmPlayback(", 1
        )[0]
        self.assertIn("craftAlarmPlayers.remove(alarmKey)", stop_one)
        self.assertIn("craftAlarmTones.remove(alarmKey)", stop_one)
        self.assertNotIn("stopAllCraftAlarmPlayback()", stop_one)
        self.assertNotIn("craftAlarmPlayers.clear()", stop_one)
        self.assertNotIn("craftAlarmTones.clear()", stop_one)

    def test_alarm_keys_include_screen_and_widget_identity(self):
        self.assertIn('String alarmKey = currentScreenIndex + ":" + index;', self.source)
        screen_stop = self.source.split("private void stopCurrentScreenAlarms()", 1)[1].split(
            "private void resetCraftAlarmEdges()", 1
        )[0]
        self.assertIn('stopCraftAlarmAlert(currentScreenIndex + ":" + index)', screen_stop)

    def test_lifecycle_cleanup_releases_all_players_and_tones(self):
        stop_all = self.source.split("private void stopAllCraftAlarmPlayback()", 1)[1].split(
            "private void stopCurrentScreenAlarms()", 1
        )[0]
        self.assertIn("releaseCraftAlarmPlayer(player)", stop_all)
        self.assertIn("tone.release()", stop_all)
        self.assertIn("craftAlarmPlayers.clear()", stop_all)
        self.assertIn("craftAlarmTones.clear()", stop_all)
        release = self.source.split("private static void releaseCraftAlarmPlayer(", 1)[1].split(
            "private void stopCraftAlarmAlert(", 1
        )[0]
        self.assertIn("player.release()", release)


if __name__ == "__main__":
    unittest.main()
