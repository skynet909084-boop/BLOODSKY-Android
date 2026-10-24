# Crash-safe permanent project media cleanup — 22 October 2026

## Issue and new behavior

Permanent deletion previously committed the removal from `project_trash_v1`
and only then deleted app-private media. If the directory was inaccessible
or the process died before cleanup, the orphaned generations had no retry
record. This is the remaining crash/retry gap under Issue #208.

Permanent deletion now commits two SharedPreferences fields together:
- the revised trash catalog, and
- a bounded list of generated directory IDs to clean up.

The dedicated cleanup executor processes pending IDs and retries on next app
startup. A failed cleanup or failed acknowledgement keeps the tombstone.
A project already referenced again in the active, saved, or trash catalog is
**never** deleted; its stale tombstone is dismissed instead. Invalid queue
content or catalog JSON fails closed without destroying files.

Every deletion is rooted under app-private `files/projects`, refuses unsafe
canonical paths/symlinks, and removes all media generations of that one
generated identity. Raw project names and custom paths are not accepted.
Cleanup is intentionally separate from the UI so large media directories
do not block home-screen interaction.

## Acceptance and limits

- JUnit tests cover bounded/deduplicated tombstones, malformed entries,
  live/trash/active identity protection, replay after simulated process
  restart, two media generations, preservation of another project and
  symlink/path traversal rejection.
- Project JSON formats and media generation naming do not change.
- Original images/audio inside the APK are untouched.
- Durable completion is recorded only after file deletion succeeds.
- Physical Android process-kill, I/O error injection, and concurrent
  project re-import remain release acceptance cases. No visual parity
  claim is made.

The change belongs to the single unified BLOODSKY Android application.
