# BLOODSKY — project format findings (APK 1.0.7)

Date: 2026-10-14
Source: private GitHub Actions artifact `BLOODSKY-Kaskada-decompiled`, generated from the authorized Kaskada Craft 1.0.7 APK.

This document records reverse-engineering observations from the decoded source. It is a compatibility guide, not an assertion that BLOODSKY has already implemented the format.

## Confirmed project database

SQLite helper: `ru.himik.nightscream.kascada.Bd.e`
Database: `Rovki.db`
Schema version: 11.

The `TableNameProject` table contains:
`_id`, `name`, `pass`, `stationMode`, `countScreen`, `countDevice`, `screenBackground`, `deviceNetwork`, `createdTime`, `createdDate`, `work`, `pdfDocument`, `showTimeOut`, `systemRegisters`, `modbusSlave`, `settings`.

The `Settings` table contains `_id` and `key`. Upgrade code adds columns for `work`, `pdfDocument`, `showTimeOut`, `systemRegisters`, `modbusSlave`, and `settings` when missing.

## Confirmed ZIP export structure

`Bd/Export/ProjectExportActivity.exportProjectToZip(name, ...)` serializes a project to JSON and calls `ExportPresenter.createZip_(...)`.

The top-level JSON includes `TableNameProject`, whose project object includes:
- `_id`, `version`, `name`, `pass`, `stationMode`, `countScreen`
- `createdTime`, `createdDate`, `countDevice`, `deviceNetwork`
- `systemRegisters`, `modbusSlave`, `settings`, `pdfDocument`
- `jsTableNameProject_screenBackground` (per-screen color/image configuration)

Per-screen component records are read from tables named `pro_<screenNumber>_component`. The exported `TableComponent` entries include at least:
`componentId`, `text`, `width`, `height`, `x`, `y`, `textSize`, `colorText`, `colorBackground`.
Additional component data is exported in `jsTableComponent_connectionInfo` and `jsTableComponent_other`. Some components include asset paths such as on/off images, GIFs, sound files, or picture sets; export rewrites these paths to filenames in the project folder.

Connection records are read from `pro_<screenNumber>_connections` and placed in `TableConnections`. Confirmed connection columns used by the export code include `connectionsId`, `componentId`, and `server`; the full column schema and ordering still need verification against table creation and import code.

The exporter copies referenced images/sounds/PDF into the project directory and packages the project folder as a ZIP. Background image references are rewritten to local filenames. Do not assume exported paths remain valid outside the archive.

## Implementation requirements for BLOODSKY

1. Preserve the original archive and JSON fields, including unknown fields, when importing and re-exporting.
2. Keep stable component IDs and screen-specific component/connection collections.
3. Resolve all asset references relative to the extracted project directory; reject path traversal and avoid writing outside the target folder.
4. Treat import as a transaction: validate the archive and JSON before mutating the active project, and roll back on failure.
5. Round-trip test with projects exported by the original application. Compare screen count, widget properties, connections, backgrounds, and media assets after import/export.
6. Keep visual widget models, connection/register bindings, and runtime evaluation separate.

## Remaining investigation

- Verify complete component and connection table schemas from database creation and import routines.
- Map each widget tag to its custom view and serialized `other` fields.
- Confirm archive entry names and exact JSON filename from `ExportPresenter.createZip_` and import extraction routines.
- Inspect Modbus RTU/TCP data paths and FBD execution semantics before implementing runtime behavior.
