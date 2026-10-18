# BLOODSKY original Craft FB_SEL — native Integer-only implementation

**Status:** source backed, implemented in draft FBD feature stack; merge/release still gated by CI and compatibility review. This file stands alone so multiple FBD PRs can be merged without conflict in the main forensic handoff.

## Pinned original evidence

Archive `reference/craft-reference.zip` SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`, original `FB_SEL.java` SHA256 `fcad265b6fa9a9bcdbce75b53094fbb44c9525f0ce1b9b2ec1c4f07318715a35`. Runtime `f()` lines 39–48: if source `f8986g.getOut()` is Boolean true choose `f8988i.getOut()` Integer; otherwise choose `f8987h.getOut()` Integer. Output method `getOut()` lines 67–72: Integer Q, null if source input missing. The original palette preview `FB_SEL` visibly labels source inputs `IN`, `I1`, `I2`, numeric output `Q`.

## Implemented versioned BLOODSKY contract

- New **`FBD CRAFT SEL`** extended palette ID, typed BOOL `IN`, NUMBER `I1`/`I2`, NUMBER `Q`. `IN=false` yields `I1`; `IN=true` yields `I2`. Input-order-independent pinned wires.
- Pure helper `FbdCraftIntegerSelector` matches the source Java signed 32-bit Integer selection. Existing BLOODSKY graph carries generic Double NUMBER values, so exact int32 validation rejects fractional, NaN, Infinity, overflow rather than falsely treating a float as an original Integer. If a required terminal is unconnected or invalid, the BLOODSKY numeric runtime now represents the original `getOut()` null as a **transient NaN sentinel**: native downstream COMPARE rejects it, the HMI binding renders `CFG`, and no control logic can mistake it for a valid numeric zero. This improves fail-closed behavior; it is still an internal sentinel rather than a Java null, so **full source parity is not claimed**.
- Legacy **`FBD SEL`** remains G/IN0/IN1 -> OUT and unchanged; its saved JSON and imported unknown fields are preserved. Original `FB_SEL` catalog tile remains disabled until direct source-preview UI validation.
- Source-like default gray/black Canvas skin with `IN/I1/I2/Q` visible; custom element color/label overrides remain respected. `FbdCraftIntegerSelectorTest` and `FbdSourceNativeCanvasSkinTest` exercise binary/integer limits, typed graph, missing operands and source geometry.
- The source audit is in [PR #122](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/122); this implementation is [PR #123](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/123) stacked on it. All integrated changes remain in the draft FBD [PR #76](https://github.com/skynet909084-boop/BLOODSKY-Android/pull/76) only after exact-head tests and approval. Stable `main` remains unchanged.

## Remaining acceptance

Check exact-head Android unit tests, Android 11 UI smoke, source probe and stable legacy project round-trip. Add an original-palette SEL icon only after verifying its clickable source preview and numeric port routing on real Android UI. Do not claim the optional FB_COMPARE/MOVE/MACRO/ARRAY tiles ported merely because the archived static-source audit located the files.

## Connected native chain safety — 18 October 2026

A disconnected source-native SEL (`IN`, `I1`, `I2`) must not emit numeric 0 as a plausible value. That earlier approximation could make a downstream signed-Integer comparator `< 10` spuriously true, despite an incomplete input graph. From this change the transient FBD numeric cache instead uses NaN when source SEL operands are missing/non-int32. `fbdNumericPinValue(..., Double.NaN)` retains the invalid marker, signed-native COMPARE declines to compare, and `FbdResultBinding.resolve()` renders `CFG` rather than an apparently valid number. The sentinel is never written to project JSON. Exact input source control semantics and legacy SEL remain unchanged. JUnit covers disconnected chain and repaired named-wire graph.
