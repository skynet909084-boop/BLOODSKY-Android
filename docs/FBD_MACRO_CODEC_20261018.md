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
definition back to `.mcr`. Macro widget instances carry their selected definition in preserved
widget metadata, an imported catalog entry can be placed on the current FBD canvas, and a selected
FBD block can be captured with its upstream graph as a new project macro. Leaf sources become
typed macro inputs; graphs with more than four inputs are rejected. Rebuilding a macro under the
same name refreshes already-placed instances and clears their nested runtime cache. The wire
picker, imported-project validator and canvas terminals read the
definition's named input and output pins and enforce BOOL versus numeric compatibility. Original
integer and float declarations stay distinct in the saved interface; both use the app's shared
numeric signal class for wiring. Project-local definitions are stored under
`craftMacroDefinitions`, so the existing project JSON unknown-field preservation keeps them
through normal saves and reloads. `FbdCraftMacroCatalog` validates each catalog entry,
replaces definitions case-insensitively by name, and caps the catalog at 64 entries.
Import is bounded while reading the selected document and validates the entire definition
before saving the project. Definitions may also retain an optional `bloodskyGraph` object;
its bounded widget/edge counts, named macro inputs, output reference and acyclic topology are
validated while preserving unknown graph metadata. During operator scans, an imported macro
instance evaluates that validated graph with its own child-widget runtime state and named typed
inputs; separate instances keep separate nested state. Definitions without an embedded
BLOODSKY graph remain importable and inspectable but evaluate as unavailable.

## Scope and remaining work

The nested graph editor and `FB_MACRO` entry in the original palette are still gated. Canvas
capture creates a new nested graph, but arbitrary nested graph editing is not yet available. The
nested runtime executes the explicitly embedded BLOODSKY graph; it does not claim that opaque
original Craft child records execute. Keep the original palette entry gated until the nested graph
authoring lifecycle and exact-head Android gates pass. No visual parity or physical PLC behavior
is claimed.
