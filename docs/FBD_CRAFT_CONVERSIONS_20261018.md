# Craft LOG and WordToFloat port — 18 Oct 2026

Source archive: `reference/craft-reference.zip`, SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`.

| Original source | SHA256 | Source behavior | BLOODSKY type |
|---|---|---|---|
| `Logic/LOG.java` | `4731e421e01eda2441b81eafb1d9a7d68f04dcf04c10ab62a7ef066ddc1266f0` | `getTypeVarOut()=3`; `o(1)=3`; `getOut()` at line 50 computes `(float)Math.log((float)input)` | `FBD CRAFT LOG` |
| `Logic/WordToFloat.java` | `ac71b2c25ecc9b74463e27c55bf974f61ab07d5476938c01e986e91037cc2ce8` | `getTypeVarOut()=3`; `o(1)=2`; `getOut()` at line 47 converts Integer to Float | `FBD CRAFT WORD TO FLOAT` |

Both blocks have one named `IN` contact and one `OUT` contact, use numeric wires, and preserve their source canvas captions (`LN`, `WORD->FL`). They are enabled from the matching original Craft preview and the extended picker.

`LOG` rounds its input to Float32 before `Math.log` and rounds the output back to Float32, matching the Java source's `Float` cast. A non-finite result remains unavailable to the graph evaluator. `WordToFloat` requires a finite, exact signed 32-bit integer before conversion; fractional or out-of-range generic numeric signals fail closed because the original block casts an `Integer`.

Validation covers source tag mappings, typed pins, Float32 rounding, invalid fractional values, graph evaluation from an Integer source and project JSON round-trip. The quick Android smoke adds both original palette previews and checks that saved block IDs survive a cold reload. This does not implement the distinct typed-variable interface required for full Craft wire type parity; current BLOODSKY wire metadata still classifies numeric signals as `NUMBER`.
