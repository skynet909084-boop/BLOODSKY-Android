# FBD macro `.mcr` codec checkpoint — 18 October 2026

## Source contract

The pinned Craft 1.0.7 `FB_MACRO.java` writes a JSON object to a plain text file named
`<macro name>.mcr`. The root object contains `fbInMacros`, `name`, `countIn`, `uniqueId`,
four possible `nameInN` / `typeInN` interface records, and `nameOut` / `typeOut`. A child
record carries its original `componentId`, position, macro root name and root unique ID.
The original source maps interface type codes 1, 2 and 3 to BOOL, Integer and Float.

## BLOODSKY implementation in this checkpoint

`FbdCraftMacroDefinition` provides a defensive, lossless reader/writer for this file shape
and extracts the declared dynamic interface pins. It bounds the file to 512 KiB and the
child list to 512 records, requires strict UTF-8 and one JSON object, rejects unsafe names,
invalid port type codes, out-of-range child positions, and child records that claim another
macro root. Validated source JSON is retained, including fields BLOODSKY does not interpret.
Export is UTF-8 JSON with the original `.mcr` text-file convention.

`FbdCraftMacroDefinitionTest` covers typed pins, unknown-field round trips, malformed and
oversize files, invalid types/names, foreign child records, and count limits.

The extended FBD tools now import `.mcr` files into the active project and export a selected
definition back to `.mcr`. Project-local definitions are stored under
`craftMacroDefinitions`, so the existing project JSON unknown-field preservation keeps them
through normal saves and reloads. `FbdCraftMacroCatalog` validates each catalog entry,
replaces definitions case-insensitively by name, and caps the catalog at 64 entries.
Import is bounded while reading the selected document and validates the entire definition
before saving the project.

## Scope and remaining work

This is an import/export and definition-model layer. It does not yet add an editor for nested
child graphs, evaluate child blocks in scan order, map dynamic pins into the live connection
picker, or enable `FB_MACRO` in the original palette. Runtime namespaces already exist for
named Craft variables but are not proof of complete macro execution. Keep the original
palette entry gated until the complete nested graph lifecycle and exact-head Android gates
pass. No visual parity or physical PLC behavior is claimed.
