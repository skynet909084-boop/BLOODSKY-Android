# Quarantine before asynchronous media cleanup — 22 October 2026

## Race removed

The durable cleanup queue from PR #346 preserves media deletion across
process death. However, a new import could reuse the same stable project
identity while a background worker is recursively deleting the *normal*
`files/projects/<id>` directory.

Cleanup now has a two-phase, crash-recoverable lifecycle:

1. On the UI thread, before reopening the editor at startup or after a
   successful permanent-delete commit, safely rename the old project
   directory inside `files/projects/` to
   `files/projects/.pending_media_cleanup/<id>`.
2. In the background, recursively delete **only** that isolated old directory.
3. Persist removal from the deletion queue after successful cleanup.
4. If staging fails, preserve the pending item and retry on the next launch.
5. If an ID is now referenced by a project and no quarantined copy exists,
   drop the stale tombstone rather than touching the live project.

All source/quarantine paths must be canonical, nested app-private paths and
must not traverse symlinks. Staging uses a same-filesystem directory rename,
not a recursive copy. A newly imported project with the same ID creates its
own normal directory, which the cleanup worker never deletes.

## Evidence and limitations

JVM tests cover two generations in the quarantined project, same-ID
re-import before worker deletion, existing staging idempotence, and unsafe
quarantine symlink rejection. The previous queue and canonical-deletion
tests remain. Actual Android app kill/relaunch and storage I/O failure
injection are still release acceptance gates. There is no project-format,
original asset, visual-layout or Modbus change.
