# Original calculator transfer

The editor now opens the Craft calculator using the original calculator icon. Its table layout is copied from `apktool/res/layout/calculator.xml`; only resource IDs receive `kc_calc_` and the Russian Exit string is resolved. `OriginalCalculatorDialog.java` adapts `jadx/sources/ru/himik/nightscream/kascada/p.java` from the pinned Craft 1.0.7 reference archive (SHA-256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`).

The original input/operator methods, single-precision arithmetic, sequential operations and error handling remain unchanged. Resource-ID switch dispatch becomes equivalent comparisons because modern Android resource IDs are not compile-time constants. Native dark Material dialog styling is selected locally; the rest of the app theme is unchanged. The icon occupies the existing toolbar pending the full source-backed toolbar transfer.

The source regression compares the complete layout hierarchy and attributes and the arithmetic methods against the pinned originals. The emulator flow enters addition, division, decimal multiplication and subtraction, checks results, clears input, closes/reopens and deletes a digit before continuing the existing project/indicator persistence and deletion checks. Screenshots include calculator results.

This adds no project schema fields, network requests or Modbus commands. Existing project metadata/media handling is unchanged. Whole-editor visual parity, original AppCompat theme equivalence, calculator rotation-state retention and hardware operation are not claimed. Original floating-point quirks are retained intentionally for this transfer.
