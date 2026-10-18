# Craft native FB_TIMER integration — 2026-10-18

## Scope

The original Craft `FB_TIMER` catalogue action and extended editor palette both create `FBD CRAFT TIMER`, a versioned block bound to the pinned source. It remains distinct from the existing BLOODSKY TON/TOF/TP blocks and the already-integrated `FBD CRAFT BLINK` block. Existing saved timer types and defaults are not rewritten.

The native block has source-named contacts `IN: BOOL`, `PT: NUMBER` (validated as an exact signed Integer), and `Q: BOOL`. It preserves Craft's four action modes (TON, TOF, TP, BLINK) and three input units (MS, SEK, MIN); one configured preset is stored as exact milliseconds while the unit selection is persisted separately for interpreting connected integer PT values. Invalid, fractional, non-positive, unknown, or overflowing PT values use the configured preset. The verified third unit multiplier is 60,000 ms.

The runtime transition function is the independently tested `FbdCraftNativeTimer`, derived from the pinned source `FB_TIMER.java` (SHA-256 `e92072664732a19373a21afb21bb33837c412f8b7b711f3814d80bddca53c9f7`). It injects wall-clock time for deterministic unit tests and resets transient epochs on run entry. Source mode TP does not set Q true from the constructor's false initial state; the new source-native option preserves that observed behavior rather than silently borrowing BLOODSKY's separate TP implementation.

The timer uses the original FB_TIMER split-body geometry and typed named graph contacts. `FbdCraftTimerGraphTest` covers the pin contract, layout, mode/unit metadata round-trip, exact integer PT conversion, verified unit factors, and invalid-input fallback. The Android quick smoke adds the block through the extended UI, switches it to source TOF, and verifies its persisted distinct type and mode; the original palette smoke verifies the same type is created by the source preview.

## Remaining source-backed FBD scope

Craft's shared register blocks `FB_MX` and `FB_ARRAY` are implemented separately; see `docs/FBD_CRAFT_MX_ARRAY_20261018.md`. `FB_MACRO` still requires a persisted executable nested graph with dynamic typed interfaces and isolated namespaces, so that preview remains disabled. This batch does not claim complete FBD or pixel-identical original drawing parity.
