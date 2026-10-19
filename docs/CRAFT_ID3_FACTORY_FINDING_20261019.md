# Craft ID 3 factory finding — 2026-10-19

## Scope

This is a source-level result for the Craft 1.0.7 component factory. It narrows the open ID 3 question but does not identify a user-facing component or prove that the ID is absent from every project/import path.

## Source artifact

- GitHub Actions run: [38600957172](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172)
- Artifact: [BLOODSKY-Kaskada-decompiled, 11912849123](https://github.com/skynet909084-boop/BLOODSKY-Android/actions/runs/38600957172/artifacts/11912849123)
- Verified SHA-256: `d78bd703d6096ac12d69e19f8b5b5dfa9a4c25e342ce9af0ef8b42326aaa3f8c`
- Source files: `jadx/sources/ru/himik/nightscream/kascada/mbCanvas.java` and matching Apktool Smali `apktool/smali/ru/himik/nightscream/kascada/mbCanvas.smali`

## Finding

In `mbCanvas.m(int, ...)`:

- JADX lines 4842 and 4897 handle IDs 1 and 2 explicitly.
- The next switch begins at line 4941. Its cases begin at ID 4 and continue through ID 28 (lines 4942–6426); there is no case for ID 3.
- The Smali cross-check has the same structure: the method checks values 1 and 2, then `packed-switch v1` (line 8911) whose table starts at `0x4` and contains 25 entries for IDs 4–28 (lines 26418–26445).
- The default path then checks the FBD range beginning at ID 101. If no case matches, it returns null (lines 8927–8939).

Therefore, ID 3 has no component-class mapping in this factory and would not instantiate through this method. This does **not** tell us whether ID 3 was reserved, removed, or handled by a different project/UI path. Do not assign it a component name or import behavior by inference.

## BLOODSKY handling and next evidence

BLOODSKY currently preserves unknown component data and presents it disabled. Keep that safe fallback. To identify ID 3, we still need evidence from an actual Craft project containing ID 3, a palette/editor path that emits ID 3, or a second original version with a matching factory. Until then, the semantic identity stays unresolved.
