# Source-derived Craft project fixtures — 23 Oct 2026

This batch adds privacy-scrubbed JSON fixtures derived from the Library-provided `home2.zip` and `дом 2025.zip` to the existing sanitized project catalog. The repository does not contain the original archives or their image payloads.

## Provenance and retained structure

| Project fixture | Original ZIP SHA-256 | Components | Declared screens | Craft connections | Non-empty legacy bindings | Device slots | Sanitized source JSON SHA-256 |
|---|---|---:|---:|---:|---:|---:|---|
| `home2` | `d0eb9d86896aa750824eab4e51dc5d59a206d504154712e100e6066c4d66f7fa` | 65 | 16 | 3 | 46 | 16 | `e283f13a64c70a150c8062a8b487f1a7a2b5a430943bb53add9a0f5fa738a3bf` |
| `дом 2025` | `bbc1a9339d36c5c13d9cf1aa8feb8db567d2af294fcaf8d01d161b4f360a0544` | 346 | 16 | 2 | 122 | 16 | `4be7dbc4c07217c0f1f0f25286aa1b710727ff4bd06b718ce1071bb9231f762a` |

The fixture preserves the JSON object/array layout, field names and types, component records, screen assignments, device slot positions, numeric unit IDs, RTU flags, Craft connection records and legacy binding shapes. Free-text labels, project/device names, credentials, server addresses, URLs, date metadata and image paths are replaced with generic values. Device and connection endpoints resolve only to `example.invalid` or remain empty. The paired source images are represented in tests by the existing six-byte fixture image; no source image bytes or imported live endpoint are included.

The catalog now covers five source-derived project JSON examples: `home` (24 components), `gitH` (65), `пож` (124), `home2` (65) and `дом 2025` (346). The `пож.zip` archive itself contains no project or media; its separate `.pro` was already represented by the sanitized fixture.

The five source files contain 0, 5, 76, 5 and 200 FBD nodes respectively (Craft component IDs 101–140). None contains an ID 135 macro instance. The two newly inspected ZIP archives have no `.mcr` sidecars.

## Regression coverage

`PortableZipMediaTest.sanitizedCraftSampleProjectsPreserveSourceAndMediaAcrossZipRoundTrip` sends all five catalog entries through the production ZIP import path, verifies source records and screen/component counts, edits the project name, atomically saves and reopens, exports and re-imports, then verifies `craftSource` again. It also checks the source FBD node count, typed FBD widget import and valid non-empty graph edges for projects with FBD nodes. For `home2` and `дом 2025`, it checks all 16 device slots, their zero-based slot indices, declared device count, connection totals, legacy-binding totals, endpoint scrubbing, and that imported legacy bindings remain opaque and disabled (`connectionInfo` is empty).

This is structural import/save/export evidence. It does not verify original media mapping, component appearance, active runtime bindings, device connectivity, macro sidecars, or physical hardware behavior. The two ZIP archives contain no `.mcr` sidecars. Full original-project acceptance remains open.
