# BLOODSKY — original APK patch map

Date: 2026-10-15

This document records the next safe engineering steps after the Kaskada Craft 1.0.7 decompilation artifact was produced. It distinguishes source-confirmed facts from proposed implementation work.

## 1. Baseline before feature changes

The workflow `.github/workflows/rebuild-original-apk.yml` is a **round-trip build test**, not a modified app build. Run it first from GitHub Actions and confirm the artifact contains a signed APK plus package/hash logs. Do not call the APK installable as an update to the official app: it is signed with a temporary test key. Keep the original APK untouched.

The workflow currently expects the original APK to be attached to a GitHub Release in this repository. If the run reports that no Release or APK asset exists, upload the legally obtained source APK to a private Release, or adjust the workflow to use an explicitly configured private artifact source. Avoid committing the APK or decoded source tree to the normal repository.

## 2. Verified source navigation

| Area | Decompiled source | Confirmed purpose / next check |
|---|---|---|
| Project list and project creation | `jadx/sources/ru/himik/nightscream/kascada/MainActivity.java` | Creates project metadata and per-screen SQLite tables. Check exact schema and project lifecycle before modifying. |
| Main screen editor | `jadx/sources/ru/himik/nightscream/kascada/mbCanvas.java` | Central editor, component construction, screen navigation, selection, resize, align, copy/cut/paste, project save and import/export paths. Large decompiled file; verify questionable branches in Smali. |
| FBD blocks | `jadx/sources/ru/himik/nightscream/kascada/Logic/` | Original block classes and pin definitions. Existing implementations should be retained unless a specific defect or enhancement is verified. |
| Component views | `jadx/sources/ru/himik/nightscream/kascada/CustomComponents/` | Custom widgets and property serialization; several class names are obfuscated in JADX. |
| Network transports | `jadx/sources/ru/himik/nightscream/kascada/Connections/` | Inspect Wi-Fi/cloud/native Modbus paths, reconnection, endpoint configuration and security before changing behavior. |
| Android resources | `apktool/res/` and `apktool/AndroidManifest.xml` | Layouts, strings, styles, app label, permissions and activity declarations. |
| Native code | `apktool/lib/*/libmodbus.so` (where present) | Preserve ABI-specific libraries during APK rebuild; Java-only builds do not replace this dependency. |

The decompiled source is a navigation aid, not a perfect source recovery. JADX reported decompilation errors; use the matching Smali class whenever generated Java looks inconsistent.

## 3. Existing SEL block — do not duplicate

Source: `jadx/sources/ru/himik/nightscream/kascada/Logic/FB_SEL.java`.

Confirmed behavior:
- Visible name: `SEL`.
- Input pins: `IN` (Boolean), `I1` and `I2` (integer-valued).
- Output pin: `Q` (integer-valued).
- Type ID: `126`.
- Evaluation returns `I2` when `IN` is true and `I1` otherwise.
- The editor contains both a creation path for `FB_SEL` and a load path for type ID `126`.

Therefore the next work should test persistence, wiring and execution for SEL rather than add a second SEL implementation. Keep type ID 126 stable for compatibility.

## 4. Persistence contract to preserve

Per-screen component table:
```sql
pro_<screen>_component(
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
)
```

Per-screen connections table:
```sql
pro_<screen>_connections(
  _id INTEGER PRIMARY KEY AUTOINCREMENT,
  connectionsId INT,
  name TEXT,
  server TEXT
)
```

Do not confuse SQLite row `_id` with the original component type/tag. Keep unrecognized fields, IDs and media assets when importing/exporting projects.

## 5. Patch order

1. **Round-trip build** — decode, rebuild, align, sign, verify signature and inspect package metadata. Save source/rebuilt SHA-256 hashes.
2. **Install smoke test** — install the test-signed APK in a separate test environment; verify launch, project list, editor open, create/save/reopen project, and no immediate crash. This cannot update the official installation due to signing-key mismatch.
3. **Minimal branding patch** — only after baseline succeeds, change app label/icon/package identity deliberately. Package-name changes affect providers, databases, intents, permissions and compatibility; do not change it casually.
4. **Editor/property improvements** — modify one isolated feature at a time, with before/after screenshots and a regression checklist. Prefer a source-level patch plus rebuild over broad automatic replacements.
5. **FBD tests** — verify pin types, evaluation and saved/reloaded connections for existing blocks, beginning with SEL and simple gates. Use mocks; no live equipment required.
6. **Modbus validation** — audit RTU/TCP settings, register types, byte order, timeouts and reconnect behavior. Test against a simulator before enabling hardware I/O.
7. **Release build** — use a properly managed signing key, document its custody outside the repository, and only call the build ready after install/runtime tests pass.

## 6. Immediate blockers / evidence to collect

- First GitHub Actions run status and its log if the release asset cannot be found or Apktool fails.
- Package name and signing certificate of the original APK, captured by the workflow logs before any package identity change.
- A project exported from the original app for compatibility fixtures (sanitize credentials, server addresses and personal data first).
- A short, prioritized list of improvements for the first patch. Avoid making multiple behavior changes before the baseline has passed.

## 7. Security note

The decoded app contains legacy network functionality. Inspect and document remote endpoints and credential handling before reuse; do not silently preserve an unknown remote service as an active default. Do not upload source APKs, decoded proprietary code, signing keys or real connection credentials to a public repository.
