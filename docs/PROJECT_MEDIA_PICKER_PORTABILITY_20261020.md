# Project media picker portability — 2026-10-20

Newly selected image and audio files are copied into the active project's private media directory. Widget fields receive a relative `media/asset_<sha256>.<ext>` path rather than a provider `content://` URI, so the project can render or play the copied bytes after the provider grant is revoked and ZIP export can package them using its existing resolver.

The copy is bounded to 10 MiB per selected file and 10 MiB total in the active media generation, with a 2,048-file limit. Identical content with the same normalized extension is deduplicated. Files are written to a same-directory temporary file, flushed to disk, then renamed into place. The property editor's Apply button stays disabled until queued copies finish. Existing project references and older media generations are preserved.

This slice does not restore an open property dialog or its pending picker target after Activity recreation. Canceling properties deletes only a newly created copy that remains unreferenced in the saved active project; deduplicated files and any saved reference are retained. Previously saved external content URIs are not migrated by this change.

The related pull request adds unit coverage for new-copy tracking, dedupe, referenced-file preservation and canceled-copy deletion. Android provider behavior still needs emulator/device acceptance.
