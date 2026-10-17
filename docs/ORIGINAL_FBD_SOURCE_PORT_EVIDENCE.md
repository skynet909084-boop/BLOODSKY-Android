# KaScada Craft 1.0.7 — original FBD labels versus BLOODSKY

**Evidence status:** static inspection of user-supplied original reference archive; **not** a claim of runtime equivalence, device execution or visual pixel parity.

## Immutable source

- ZIP: `reference/craft-reference.zip`
- ZIP SHA256: `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`
- Verified by free GitHub Actions workflow `BLOODSKY Original Craft FBD source audit`.
- Probe: `tools/original_fbd_source_probe.py`; data fields `contracts` and `pinned_label_checks` include each file hash and source line without exporting decompiled Java.

| Original class (relative to `jadx/sources/.../Logic/`) | Short literal labels observed in order | First relevant source lines | Original file SHA256 |
|---|---|---|---|
| `FB_CTU.java` | `CTU, +, -, R, Q` | 20–24 (repeated 32–36) | `76baf47ef8e7dca58352f949f9217e179682e224872ef0bc173857bd38260673` |
| `FB_RS.java` | `RS, R, S, Q` | 19–22 (repeated 28–31) | `543a1707f4f70a0a37579cb6acec57a1c646b525ff612e3da6e1a7c761eacea0` |
| `FB_TIMER.java` | `TON, IN, Q`; mode strings `TOF, TP` | 32–35, 254–264 | `e92072664732a19373a21afb21bb33837c412f8b7b711f3814d80bddca53c9f7` |
| `FB_TRIGGER.java` | `IN, Q`, plus later `R` | 24–25, 195 | `f44bdc2b09839e6167a5da7bd51e6d999a56693a50d7e1bbb7ea7cd9fcd152e9` |

These are exact **short string literals in class source files**. They do *not*, on their own, establish terminal count, terminal direction, data type, execution priority or edge/timer state transitions. Parent classes may define important behavior. The probe asserts label sequences only for CTU, RS, TIMER, and deliberately rejects invented `CU/R/PV` or `PT` tokens as source labels.

## Current implementation gap

| Area | Original evidence | Current BLOODSKY behavior | Acceptance work |
|---|---|---|---|
| CTU | Class explicitly names `+`, `-`, `R`, `Q` | `FBD CTU` currently advertises `CU, R, PV` inputs and Boolean `Q`; its counter threshold is a BLOODSKY extension | Audit parent/decompiled runtime and identify precise types/counting rules. Add a source-compatible *separate* block or project migration rather than silently changing saved graphs |
| RS | Source labels `R, S, Q` | BLOODSKY uses named Boolean `R, S, Q`; reset/set precedence is implemented locally | Confirm priority and missing-input behavior from original class/superclass and differential tests |
| TON / TOF / TP | Original timer class exposes `TON` with `IN, Q` strings and mode labels `TOF, TP` | BLOODSKY exposes typed `IN, PT, Q` contacts and treats PT as milliseconds | Confirm whether original preset is a property or external wire and exact units, transitions and inversion |
| Trigger | `IN, Q`, later `R` | BLOODSKY has explicit `R_TRIG/F_TRIG` block types | Verify original mode selection and edge latch behavior, do not infer from `R` literal alone |

**Keep unsupported original Craft palette thumbnails disabled** until port geometry, data types, and behavior are source-verified. Do not publish the current BLOODSKY CTU as an exact copy of original FB_CTU. Preserve existing project JSON and legacy wire metadata when adding new source-compatible types.

## Next objective

Inspect class `FB_CTU` and its relevant superclasses as *data*, extract minimal source-backed field, method and port facts (avoid executing untrusted source). Then write differential fixtures for each port type, transition and counter saturation. Repeat for timer modes and RS priority. Require APK/JUnit, source-audit and Android UI smoke on the **exact merged head**, without touching stable `main` until parity acceptance is proven.
