# Polling session and activity lifecycle guards

`PollSessionGuard` gives each active editor polling session a distinct token. Stopping/restarting polling invalidates prior tokens even when the same widget instances remain on screen. The worker checks the token before each network request; the UI callback checks it again before updating values or triggering audio/speech. The callback also checks the current canvas, activity lifecycle and captured binding string, so an edited binding cannot receive an old register result.

`onPause` stops foreground polling, releases the sound player and stops speech. `onResume` starts polling only if an editor remains open. Releasing the player is attempted even if stop throws. The existing transport bounds an in-progress read; this change does not close a socket mid-read. Intentional foreground-service/background runtime is not implemented and remains a separate product item.

Executable tests cover inactive/stopped tokens, stop/start or supersession, and a delayed worker response after replacement. Source checks ensure the policy is wired before UI value/audio publication and lifecycle callbacks. Android instrumentation, real background transitions, MediaPlayer state handling and hardware polling remain unverified. These guards do not establish original runtime/FBD timing or a complete operator mode.
