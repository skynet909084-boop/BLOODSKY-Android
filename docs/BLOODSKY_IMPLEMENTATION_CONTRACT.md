# BLOODSKY — verified compatibility contract and implementation order

Date: 2026-10-14
Evidence: Kaskada Craft 1.0.7 decoded source from the private Actions artifact. Findings below are source-confirmed unless explicitly marked as a design recommendation.

## 1. Exact per-screen SQLite structures

The import routine `Bd/Import/h.java` creates one component table per screen:

```sql
CREATE TABLE pro_<screen>_component (
  _id INTEGER PRIMARY KEY AUTOINCREMENT,
  componentId INT,
  text TEXT DEFAULT 'default',
  width INT DEFAULT 100,
  height INT DEFAULT 100,
  x INT DEFAULT 300,
  y INT DEFAULT 300,
  textSize INT DEFAULT 32,
  connectionInfo TEXT DEFAULT '',
  colorText INT DEFAULT -65536,
  colorBackground INT DEFAULT -65536,
  other TEXT DEFAULT ''
);
```

The import routine creates the screen's connection table:

```sql
CREATE TABLE pro_<screen>_connections (
  _id INTEGER PRIMARY KEY AUTOINCREMENT,
  connectionsId INT,
  name TEXT,
  server TEXT
);
```

These definitions are direct compatibility evidence. Do not conflate `componentId` (widget type/tag in the relevant paths) with the SQLite row's `_id`; inspect the component model and export records before choosing the Windows model's field names.

## 2. Export/import contract

- Project export writes a top-level JSON object with `TableNameProject`, `TableComponent`, and `TableConnections` records.
- The project record contains the core project fields, plus per-screen background configuration under `jsTableNameProject_screenBackground`.
- Component records preserve geometry, text, font size and colors. `jsTableComponent_connectionInfo` and `jsTableComponent_other` carry additional serialized configuration.
- Connections use `connectionsId`, `componentId`, `name`, and `server` in the import table schema.
- Referenced media can include images, GIFs, sound, picture sets and PDFs. Export rewrites references to project-local filenames and packages them into a ZIP.

## 3. Editor methods confirmed in mbCanvas

The reconstructed editor exposes dedicated entry points for:
- Component construction/factory: `m(...)`
- Resize start/update: `StartResize(View)`, `Resizing(View)`
- Alignment: `alignSelectView(View)`, `openDialogAlignComponents(View)`
- Screen navigation: `clickScreenLeft(View)`, `clickScreenRight(View)`
- Delete: `deleteView(View)`
- Copy/cut/paste: `selectActionCopy(View)`, `selectActionCut(View)`, `selectActionPaste(View)`
- Work/operator toolbar: `onClickWorkToolbar(View)`
- Component details/info: `onClickGetInfo(View)`
- Screen name: `onClickSetNameScreen(View)`
- Project/runtime initialization paths: `onCreate(Bundle)`, `onResume()`, `onPause()`

These names provide a navigation map, not a promise that the decompiled method bodies are all accurate. The JADX report records 110 decompilation errors; consult Smali for suspicious branches.

## 4. FBD families discovered

The source tree includes AND, OR, XOR, RS, CTU, TIMER, TRIGGER, integer/float arithmetic, integer/float comparison, MOVE, SEL, MX, ARRAY, MACRO, integer/float conversion, ABS, LOG, SIN, POW, MQTT publisher/subscriber, and sensor/device-oriented blocks.

Recommended Windows architecture:
1. **Domain layer** — project, screens, components, typed properties and stable IDs.
2. **Persistence layer** — JSON/ZIP import-export adapter that preserves unknown fields and media.
3. **FBD graph** — typed input/output pins and explicit connections, independent from drawing.
4. **Evaluator** — deterministic scan-cycle execution with explicit timer/edge state.
5. **Protocol layer** — Modbus RTU/TCP transport, register mapping and conversion.
6. **UI layer** — screen editor, selection/resize/align/copy/paste and operator view.

Do not port Android Activity/View classes as the domain model.

## 5. Immediate implementation sequence

1. Implement a read-only archive inspector that lists the ZIP entries and validates JSON/table shapes without altering the archive.
2. Build round-trip fixtures from projects exported by the original app; preserve unknown JSON keys and assets.
3. Implement component registry and property model using stable internal IDs while retaining original `componentId`/type tags.
4. Implement screen canvas interactions and property editing against that model.
5. Add FBD graph representation and unit tests for basic gates, comparisons, arithmetic, timers and counters.
6. Implement Modbus TCP/RTU behind a transport interface; test with a simulator before connecting real equipment.
7. Validate each milestone with automated tests and a Windows x64 build. Mark features complete only after tests pass.

## 6. Safety and compatibility constraints

- Treat ZIP paths as untrusted: reject absolute paths and `..` traversal before extraction.
- Validate archive size, JSON structure and media references before mutating a project.
- Import atomically; keep the current project unchanged if validation fails.
- Never silently discard unknown component tags or JSON fields.
- Keep the source APK and decoded artifact private; only analyze software the project owner is authorized to inspect.
