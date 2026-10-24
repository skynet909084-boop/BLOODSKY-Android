# Craft LOG and WordToFloat port — 18 Oct 2026

Source archive: `reference/craft-reference.zip`, SHA256 `316da0335dc4ea0c8f2d8c14550118ba33bd0e95d7c926f67ed848429c554cdd`.

| Original source | SHA256 | Source behavior | BLOODSKY type |
|---|---|---|---|
| `Logic/LOG.java` | `4731e421e01eda2441b81eafb1d9a7d68f04dcf04c10ab62a7ef066ddc1266f0` | `getTypeVarOut()=3`; `o(1)=3`; `getOut()` at line 50 computes `(float)Math.log((float)input)` | `FBD CRAFT LOG` |
| `Logic/WordToFloat.java` | `ac71b2c25ecc9b74463e27c55bf974f61ab07d5476938c01e986e91037cc2ce8` | `getTypeVarOut()=3`; `o(1)=2`; `getOut()` at line 47 converts Integer to Float | `FBD CRAFT WORD TO FLOAT` |

Both blocks have one named `IN` contact and one `OUT` contact, use numeric wires, and preserve their source canvas captions (`LN`, `WORD->FL`). They are enabled from the matching original Craft preview and the extended picker.

`LOG` rounds its input to Float32 before `Math.log` and rounds the output back to Float32, matching the Java source's `Float` cast. A non-finite result remains unavailable to the graph evaluator. `WordToFloat` requires a finite, exact signed 32-bit integer before conversion; fractional or out-of-range generic numeric signals fail closed because the original block casts an `Integer`.

Validation covers source tag mappings, typed pins, Float32 rounding, invalid fractional values, graph evaluation from an Integer source and project JSON round-trip. The quick Android smoke adds both original palette previews and checks that saved block IDs survive a cold reload. This does not implement the distinct typed-variable interface required for full Craft wire type parity; current BLOODSKY wire metadata still classifies numeric signals as `NUMBER`.

## Craft pure math and numeric casts — 18 Oct 2026

Source archive is the same pinned Craft 1.0.7 ZIP above.

| Source class | SHA256 | Source rule | BLOODSKY type |
|---|---|---|---|
| `FB_POW.java` | `0c21424f82420d6db106130cb4a7bd472f6b67c40ccb9969de2f9127777a6668` | Float `X`, Float `Y`; `(float)Math.pow(X,Y)` | `FBD CRAFT POW` |
| `SIN.java` | `5d167998770dc8e330c877c9e23f12d7f83945dea953d5b27b13001b751000e3` | Float input; `(float)Math.sin(input)` | `FBD CRAFT SIN` |
| `ABS.java` | `9e96d12225b433c0119f86468f6cb14533f567d491cb212f0d9e3346ac90f783` | Float input; `Math.abs(float)` | `FBD CRAFT ABS` |
| `IntToFloat.java` | `5d55042f3b083abdf34055de54d6fb00d92e664c928307e728fe869c6435f8df` | Integer input narrows to Java `short`, then widens to Float | `FBD CRAFT INT TO FLOAT` |
| `FloatToInt.java` | `55ff1c6ae365469b6d5f8191d783bea3988282d62104f439f39fe67a8fa53582` | Float input casts to Integer, truncating toward zero | `FBD CRAFT FLOAT TO INT` |

These original preview actions use separate runtime IDs and preserve existing BLOODSKY operators. POW exposes named `X/Y` and numeric `Q`; unary actions expose `IN/OUT`. Floating math rounds through Java Float32. Integer conversion follows the source's short narrowing and float-to-int cast. Non-finite incoming generic values fail closed; numeric wire metadata remains the project's shared `NUMBER` type until distinct Integer/Float signal IDs are introduced.
