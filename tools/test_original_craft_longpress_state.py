import unittest

from original_craft_longpress_state import action_identity, actions_for_controls, touch_command


class CraftLongPressStateTest(unittest.TestCase):
    def test_long_press_only_for_accessibility_marked_controls(self):
        controls = [
            {"x": 10, "y": 20, "text": "Timer", "long_clickable": True},
            {"x": 30, "y": 40, "text": "Ordinary button", "long_clickable": False},
        ]
        actions = actions_for_controls(controls, "fbd-timer-modes")
        long_actions = [action for action in actions if action["kind"] == "long_press"]
        self.assertEqual(1, len(long_actions))
        self.assertEqual("LONG PRESS: Timer", long_actions[0]["label"])

    def test_tap_and_long_press_at_same_point_are_kept_once_each(self):
        controls = [
            {"x": 10, "y": 20, "text": "Timer", "clickable": True, "long_clickable": True},
            {"x": 10, "y": 20, "text": "Timer child", "clickable": True, "long_clickable": True},
        ]
        actions = actions_for_controls(controls, "fbd-timer-modes")
        self.assertEqual(["long_press", "tap"], [action["kind"] for action in actions])
        self.assertEqual(2, len(actions))

    def test_direction_prioritizes_long_press_target(self):
        controls = [
            {"x": 10, "y": 20, "text": "Sensor", "long_clickable": True},
            {"x": 30, "y": 40, "text": "Timer", "long_clickable": True},
        ]
        actions = actions_for_controls(controls, "fbd-timer-modes")
        self.assertIn("Timer", actions[0]["label"])

    def test_unmatched_long_press_does_not_block_directional_navigation(self):
        controls = [
            {"x": 10, "y": 20, "text": "Calculator", "long_clickable": True},
            {"x": 30, "y": 40, "text": "Macros", "clickable": True},
        ]
        actions = actions_for_controls(controls, "macros")
        self.assertEqual("Macros", actions[0]["label"])
        self.assertEqual("LONG PRESS: Calculator", actions[1]["label"])

    def test_action_identity_distinguishes_gesture_kind(self):
        node = {"resource_id": "toolbar", "bounds": "[0,0][20,20]"}
        self.assertNotEqual(
            action_identity(
                {"kind": "tap", "node": node}),
            actions_for_controls.__globals__["action_identity"](
                {"kind": "long_press", "node": node}),
        )

    def test_long_press_uses_stationary_swipe_with_one_second_hold(self):
        action = {"kind": "long_press", "node": {"x": 123, "y": 456}}
        self.assertEqual(
            ["shell", "input", "swipe", "123", "456", "123", "456", "1000"],
            touch_command(action, 1000),
        )


if __name__ == "__main__":
    unittest.main()
